# Usa l'immagine ufficiale di Jolie come base
FROM jolielang/jolie:latest

# Imposta la cartella di lavoro dentro il container
WORKDIR /app

# Copia i file sorgenti dal tuo PC al container
COPY bankInterface.iol .
COPY bankService.ol .
COPY type.ol .
COPY BankService.wsdl .

# Esponi la porta 8000 (quella definita nel tuo codice inputPort)
EXPOSE 8000

# Comando che viene lanciato quando parte il container
CMD ["jolie", "bankService.ol"]