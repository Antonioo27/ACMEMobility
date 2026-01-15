package it.unibo.acme.mobility.integration.bank;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

public class BankResponseHandler implements SOAPHandler<SOAPMessageContext> {

    @Override
    public boolean handleMessage(SOAPMessageContext context) {
        Boolean outbound = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

        // Solo per i messaggi in arrivo (Risposte)
        if (!outbound) {
            try {
                SOAPMessage soapMsg = context.getMessage();

                // 1. Convertiamo il messaggio SOAP in Stringa
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                soapMsg.writeTo(out);
                String xmlContent = new String(out.toByteArray(), StandardCharsets.UTF_8);

                // DEBUG: Vediamo cosa arriva
                // System.out.println("--- XML RAW ---");
                // System.out.println(xmlContent);

                // 2. MANIPOLAZIONE DIRETTA STRINGA
                // Se l'XML contiene i tag "nudi", li forziamo con il namespace corretto.
                
                // Aggiungiamo il namespace al tag padre se manca o se è nudo
                if (xmlContent.contains("<preAuthorizeResponse>")) {
                    xmlContent = xmlContent.replace(
                        "<preAuthorizeResponse>", 
                        "<ns1:preAuthorizeResponse xmlns:ns1=\"http://acmemobility.org/bank.xsd.wsdl\">"
                    );
                    xmlContent = xmlContent.replace("</preAuthorizeResponse>", "</ns1:preAuthorizeResponse>");
                }

                // Aggiungiamo il prefisso al tag figlio <token>
                // Usiamo "<token" per intercettare anche attributi come xsi:type
                if (xmlContent.contains("<token")) {
                    xmlContent = xmlContent.replace("<token", "<ns1:token");
                    xmlContent = xmlContent.replace("</token>", "</ns1:token>");
                }

                // 3. Ricostruiamo il messaggio SOAP dalla stringa modificata
                MessageFactory factory = MessageFactory.newInstance();
                SOAPMessage newMsg = factory.createMessage(
                    new MimeHeaders(), 
                    new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))
                );

                // 4. Sostituiamo il messaggio nel contesto
                context.setMessage(newMsg);

                // DEBUG: Controllo finale
                System.out.println("--- [HANDLER] XML REBUILT (String Patch) ---");
                ByteArrayOutputStream debugOut = new ByteArrayOutputStream();
                newMsg.writeTo(debugOut);
                System.out.println(new String(debugOut.toByteArray()));
                System.out.println("--------------------------------------------");

            } catch (Exception e) {
                System.err.println("Errore grave nel BankResponseHandler: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean handleFault(SOAPMessageContext context) { return true; }

    @Override
    public void close(MessageContext context) { }

    @Override
    public Set<QName> getHeaders() { return Collections.emptySet(); }
}