type CardData {
    .cardNumber: string
    .cvv: string
    .expiryDate: string
    .holderName: string
}

// Richiesta di avvio (Apre la sessione)
type PreAuthRequest: void {
    .amount: double
    .card: CardData
}

// Risposta con il Token generato
type PreAuthResponse: void {
    .token: string
}

//Tipo per il fault
type PaymentDeclined: void {
    .reason: string
    .originalAmount: double
}

type ReleaseRequest: void {
    .token: string
    .amount?: double 
}

type CaptureRequest: void {
    .token: string
    .amount?: double 
}

type FinalPaymentRequest: void {
    .token: string
    .amount?: double 
}

type BankResponse: void {
    .status: string
    .message?: string
}

//Pagamento Fallito in fase finale
type PaymentFailedType: void {
    .reason: string
    .availableTotal: double // Utile per dire ad ACME "Guarda che aveva solo tot"
    .required: double
}