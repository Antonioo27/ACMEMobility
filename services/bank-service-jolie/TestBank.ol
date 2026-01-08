include "console.iol"
include "bankInterface.iol"

outputPort BankService {
    Location: "socket://localhost:8000"
    Protocol: soap
    Interfaces: BankInterface
}

main {
    
    // --- TEST 1: MARIO (HAPPY PATH) ---
    println@Console("\n--- TEST 1: MARIO (Happy Path) ---")();
    with( req ) { 
        .amount = 10.0; 
        .card.holderName = "Mario Rossi";
        .card.cardNumber = "1234-1234-1234-1234"; 
        .card.cvv = "123";
        .card.expiryDate = "12/28"
    }; // Carta Ricca
    
    preAuthorize@BankService( req )( resp );
    tokenMario = resp.token;
    println@Console("1. PreAuth OK. Token: " + tokenMario )();

    // Mario paga 15€ (ha 500, quindi ok)
    with( payReq ) { .token = tokenMario; .amount = 15.0 };
    processFinalPayment@BankService( payReq )( payResp );
    println@Console("2. Pagamento Finale: " + payResp.status )();


    // --- TEST 2: LUIGI (UNHAPPY PATH - RECOVERY) ---
    println@Console("\n--- TEST 2: LUIGI (Fallimento + Recupero) ---")();
    with( reqL ) { 
        .amount = 10.0; 
        .card.holderName = "Luigi";
        .card.cardNumber = "1111-2222-3333-4444"; 
        .card.cvv = "123";
        .card.expiryDate = "12/28"
    }; // Carta Povera (15€ tot)
    
    preAuthorize@BankService( reqL )( respL );
    tokenLuigi = respL.token;
    println@Console("1. PreAuth OK (Bloccati 10E). Token: " + tokenLuigi )();

    // Luigi prova a pagare 20€ (ne ha 15 totali -> 10 bloccati + 5 liberi)
    // Questo deve fallire
    scope( attemptPayment ) {
        install( PaymentFailed => 
            println@Console("2. CATTURATO FAULT: " + attemptPayment.PaymentFailed.reason )();
            
            // 3. RECUPERO (Simulazione BPMS)
            with( captureReq ) { .token = tokenLuigi };
            captureDeposit@BankService( captureReq )( captResp );
            println@Console("3. Recupero Crediti (Capture): " + captResp.status )()
        );

        with( payReqL ) { 
            .token = tokenLuigi; 
            .amount = 20.0  // <--- Cifra alta che causa il fallimento finale
        };
        processFinalPayment@BankService( payReqL )( payRespL )
    }
}