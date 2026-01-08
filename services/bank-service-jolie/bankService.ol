include "console.iol"
include "string_utils.iol"
include "bankInterface.iol"

service BankService {

    execution { concurrent }

    inputPort BankPort {
        Location: "socket://localhost:8000"
        Protocol: soap {
            .wsdl = "BankService.wsdl"
            .wsdl.port = "BankPortServicePort"
        }
        Interfaces: BankInterface
    }

    cset {
        token: PreAuthResponse.token
               TokenOperation.token
    }

    init {
        println@Console( "Bank Service avviato..." )();

        // ESEMPIO: Mario (Ricco)
        // Ha 500€ veri, e 500€ spendibili subito
        with( global.db.cards.("1234-1234-1234-1234") ) {
            .accountingBalance = 500.00; 
            .availableBalance  = 500.00;
            .valid = true
        };

        // ESEMPIO: Luigi (Al limite - 15€)
        with( global.db.cards.("1111-2222-3333-4444") ) {
            .accountingBalance = 15.00;
            .availableBalance  = 15.00;
            .valid = true
        }

        with( global.db.cards.("0000-0000-0000-0000") ) {
            .accountingBalance = 00.00;
            .availableBalance  = 00.00;
            .valid = false
        }

    }

    main {
        // --- FASE 1: PRE-AUTORIZZAZIONE ---
        preAuthorize( preRequest )( preResponse ) {
            cardNum = preRequest.card.cardNumber;
            amountReq = preRequest.amount;

            println@Console( "Richiesta Pre-Auth: " + cardNum + " per " + amountReq + "€" )();

            synchronized( cardLock ) {
                if ( is_defined( global.db.cards.(cardNum) ) && global.db.cards.(cardNum).valid ) {
                    
                    currentAvail = global.db.cards.(cardNum).availableBalance;

                    // Controllo sul SALDO DISPONIBILE
                    if ( currentAvail >= amountReq ) {
                        
                        // Genero token
                        preResponse.token = csets.token = new;
                        
                        // LOGICA CHIAVE: Abbasso il disponibile
                        global.db.cards.(cardNum).availableBalance = currentAvail - amountReq;
                        
                        // Salvo sessione
                        blockedAmount = amountReq;
                        clientCard = cardNum;

                        println@Console( " -> Blocco OK. " )()
                    
                    } else {
                        // Fondi insufficienti
                        with( faultData ) {
                            .reason = "Fondi insufficienti (Saldo: " + currentAvail + ")";
                            .originalAmount = amountReq
                        }
                        println@Console(" -> KO: Fondi insufficienti")();
                        throw( PaymentDeclined, faultData )
                    }
                } else {
                    // Carta non valida
                    with( faultData ) {
                        .reason = "Carta non valida o inesistente";
                        .originalAmount = amountReq
                    }
                    println@Console(" -> KO: Carta non valida")();
                    throw( PaymentDeclined, faultData )
                }
            }   
        }

        // --- FASE 2: CHIUSURA (Attesa operazioni) ---
        ; 
        (
            // Rilascio semplice (Annullamento gratuito o Pulizia post-pagamento)
            [ 
                releaseDeposit( releaseRequest )( releaseResponse ) {
                    synchronized( cardLock ) {
                        // CONTROLLO DI SICUREZZA (Idempotenza)
                        // Se blockedAmount è > 0, vuol dire che i soldi sono ancora bloccati.
                        // Se è 0, vuol dire che sono già stati usati dal pagamento finale.
                        
                        if ( blockedAmount > 0 ) {
                            global.db.cards.(clientCard).availableBalance = global.db.cards.(clientCard).availableBalance + blockedAmount;
                            println@Console("Cauzione rilasciata ("+blockedAmount+"). Saldi riallineati.")();
                            
                            // Importante: azzero il blocco per evitare rilasci doppi
                            blockedAmount = 0.0
                        } else {
                            println@Console("Richiesta rilascio ignorata: cauzione già consumata o assente.")()
                        };

                        releaseResponse.status = "OK"
                    }
                } 
            ]

            |

            // Capture (Penale o trattenuta totale cauzione)
            [ 
                captureDeposit( captureRequest )( captureResponse ) {
                    synchronized( cardLock ) {
                        if ( blockedAmount > 0 ) {
                            // Tolgo dal contabile perché dal disponibile li avevo già tolti all'inizio
                            global.db.cards.(clientCard).accountingBalance = global.db.cards.(clientCard).accountingBalance - blockedAmount;
                            
                            println@Console("Cauzione incassata definitivamente.")();
                            blockedAmount = 0.0;
                            captureResponse.status = "OK"
                        } else {
                            captureResponse.status = "FAILED";
                            captureResponse.message = "Nessuna cauzione attiva da incassare"
                        }
                    }
                } 
            ]

            |

            // Pagamento Finale (Sblocco + Addebito Contestuale)
            [ 
                processFinalPayment( paymentRequest )( paymentResponse ) {
                    cost = paymentRequest.amount;
                    
                    // Logica "Netting": considero il blocco come parte dei soldi disponibili
                    amountPreviouslyBlocked = blockedAmount; 
                    
                    synchronized( cardLock ) {
                        currentAvailable = global.db.cards.(clientCard).availableBalance;
                        totalSpendingPower = currentAvailable + amountPreviouslyBlocked;
                        
                        println@Console("Pagamento di " + cost + "€. Potere spesa totale: " + totalSpendingPower )();

                        if ( totalSpendingPower >= cost ) {
                            
                            // PAGAMENTO OK
                            
                            // 1. Aggiorno il Contabile (escono i soldi veri del costo)
                            global.db.cards.(clientCard).accountingBalance = global.db.cards.(clientCard).accountingBalance - cost;
                            
                            // 2. Aggiorno il Disponibile
                            // Matematicamente equivale a: (DisponibileAttuale + SbloccoCauzione) - Costo
                            global.db.cards.(clientCard).availableBalance = totalSpendingPower - cost;
                            
                            paymentResponse.status = "OK";
                            paymentResponse.message = "Pagamento riuscito.";
                            
                            // FONDAMENTALE: Azzero blockedAmount.
                            // Così se dopo chiami releaseDeposit, non succede nulla (corretto).
                            blockedAmount = 0.0 

                        } else {
                            // FALLIMENTO (Fondi insufficienti anche considerando la cauzione)
                            //paymentResponse.status = "PARTIAL_FAILURE";
                            //paymentResponse.message = "Fondi insufficienti (Max: " + totalSpendingPower + ")";
                            
                            with( faultFail ) {
                                .reason = "Fondi insufficienti (inclusa cauzione)";
                                .availableTotal = totalSpendingPower;
                                .required = cost
                            };
                            
                            throw( PaymentFailed, faultFail )
                            // Nota: qui NON azzero blockedAmount.
                            // Così il BPMN può chiamare 'captureDeposit' per prendersi almeno la cauzione come penale.
                        }
                    }
                } 
            ]
        )


    }
}