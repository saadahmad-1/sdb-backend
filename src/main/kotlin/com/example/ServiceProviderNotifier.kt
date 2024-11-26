package com.example

object ServiceProviderNotifier {
    fun notifyOtpGenerated(serviceProviderId: String, otpId: String) {
        println("Notifying service provider $serviceProviderId about OTP generation: $otpId")
    }
    
    fun notifyOtpGenerationFailed(errorId: String, error: String) {
        println("Notifying service providers about OTP generation failure: $errorId - $error")
    }
}