include "type.ol"

interface BankInterface {
    RequestResponse:
        preAuthorize( PreAuthRequest )( PreAuthResponse ) throws PaymentDeclined( PaymentDeclined ) ,
        releaseDeposit( TokenOperation )( BankResponse ) ,
        captureDeposit( TokenOperation )( BankResponse ) ,
        processFinalPayment( TokenOperation )( BankResponse ) throws PaymentFailed( PaymentFailedType )
}