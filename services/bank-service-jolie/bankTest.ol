include "console.iol"
include "bankInterface.iol"
include "time.iol"


outputPort BankService {
    Location: "socket://localhost:8000"
    Protocol: soap {
        .wsdl = "BankService.wsdl";
        .wsdl.port = "BankPortServicePort"
    }
    Interfaces: BankInterface
}

main {
    
    // // --- SCENARIO 1: CLIENTE CON FONDI (Happy Path) ---
    // println@Console( "\n--- TEST 1: Mario (Carta valida) ---" )();
    
    // // 1. Dati della carta (finisce con 1000 = Ricco)
    // marioCard.holderName = "Mario Rossi";
    // marioCard.cardNumber = "1234-1234-1234-1000"; 
    // marioCard.cvv = "123";
    // marioCard.expiryDate = "12/28";

    // request.amount = 10.0;
    // request.card << marioCard;

    // // 2. Chiamata Pre-Autorizzazione
    // println@Console( "Invio richiesta Pre-Auth..." )();
    // preAuthorize@BankService( request )( response );

    // token = response.token;
    // println@Console( "Pre-Auth OK! Token ricevuto: " + token )();

    // // 3. Simuliamo il tempo che passa (il noleggio)
    // sleep@Time( 1000 )();

    // // 4. Chiamata Pagamento Finale (chiusura sessione)
    // // Usiamo il token ricevuto prima per riagganciare la sessione
    // paymentReq.token = token;
    // paymentReq.amount = 25.50; // Costo del noleggio
    
    // println@Console( "Invio richiesta Pagamento Finale..." )();
    // processFinalPayment@BankService( paymentReq )( payResponse );

    // println@Console( "Esito Pagamento: " + payResponse.status )();
    // println@Console( "Messaggio Banca: " + payResponse.message )()


    // --- TEST LUIGI (Sad Path) ---
    println@Console( "\n--- TEST 2: Luigi (Fondi insufficienti) ---" )();

    luigiCard.holderName = "Luigi Verdi";
    luigiCard.cardNumber = "1234-1234-1234-2000";
    luigiCard.cvv = "999";
    luigiCard.expiryDate = "10/26";

    requestLuigi.amount = 10.0;
    requestLuigi.card << luigiCard;

    // Scope obbligatorio per catturare il Fault SOAP
    scope( luigiScope ) {
        
        install( PaymentDeclined => 
            println@Console( "[CLIENT] Errore ricevuto dal Server!" )();
            println@Console( "Motivo: " + luigiScope.PaymentDeclined.reason )();
            println@Console( "Dettagli: " + luigiScope.PaymentDeclined.originalAmount )()
        );

        println@Console( "Invio richiesta..." )();
        preAuthorize@BankService( requestLuigi )( responseLuigi );
        
        // Se arriva qui, qualcosa non va nel test
        println@Console( "ERRORE DEL TEST: Luigi doveva fallire!" )()
    }
}