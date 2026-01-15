include "type.ol"

interface BankInterface {
    RequestResponse:
        preAuthorize( PreAuthRequest )( PreAuthResponse ) throws PaymentDeclined( PaymentDeclined ) ,
        releaseDeposit( ReleaseRequest )( BankResponse ) ,
        captureDeposit( CaptureRequest )( BankResponse ) ,
        processFinalPayment( FinalPaymentRequest )( BankResponse ) throws PaymentFailed( PaymentFailedType )
}