package com.samsung.prism.teachable.security

import android.text.InputType
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot

class CredentialBoundaryDetector {

    // Layer 3: Sensitive Regex Patterns
    private val otpPattern = Regex(
        "\\b(otp|one[-\\s]?time[-\\s]?(password|pin)|verification[-\\s]?code|enter[-\\s]?code)\\b",
        RegexOption.IGNORE_CASE
    )
    private val cvvPattern = Regex(
        "\\b(cvv|cvc|security[-\\s]?code|card[-\\s]?verification)\\b",
        RegexOption.IGNORE_CASE
    )
    private val pinPattern = Regex(
        "\\b(upi[-\\s]?pin|atm[-\\s]?pin|m[-\\s]?pin|enter[-\\s]?pin|secret[-\\s]?pin)\\b",
        RegexOption.IGNORE_CASE
    )
    private val cardPattern = Regex(
        "\\b(card[-\\s]?number|credit[-\\s]?card|debit[-\\s]?card|expiry[-\\s]?(date|month)|valid[-\\s]?thru|mm\\s*/\\s*yy)\\b",
        RegexOption.IGNORE_CASE
    )
    private val paymentActionPattern = Regex(
        "\\b(pay[-\\s]?now|proceed[-\\s]?to[-\\s]?pay|complete[-\\s]?payment|select[-\\s]?payment[-\\s]?method|choose[-\\s]?payment|make[-\\s]?payment)\\b",
        RegexOption.IGNORE_CASE
    )
    private val biometricPattern = Regex(
        "\\b(biometric|fingerprint|face[-\\s]?id|touch[-\\s]?id)\\b",
        RegexOption.IGNORE_CASE
    )
    private val bankingPasswordPattern = Regex(
        "\\b(net[-\\s]?banking|transaction[-\\s]?password|profile[-\\s]?password|login[-\\s]?password)\\b",
        RegexOption.IGNORE_CASE
    )

    // Layer 4: Payment gateway & financial packages
    private val paymentPackages = setOf(
        "com.phonepe.app",
        "net.one97.paytm",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.sbi.lotusintouch",
        "com.sbi.SBIFreedomPlus",
        "com.axis.mobile",
        "com.razorpay",
        "com.razorpay.payments",
        "com.juspay",
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.dreamplug.androidapp"
    )

    // Layer 5: Activity patterns indicating checkout / payment views
    private val paymentActivityKeywords = listOf(
        "PaymentActivity", "CheckoutActivity", "PaymentMethodActivity",
        "UpiActivity", "OtpActivity", "AuthActivity", "BankingActivity",
        "RazorpayActivity", "JuspayActivity", "PaytmActivity"
    )

    fun checkBoundary(snapshot: UiSnapshot): BoundaryCheckResult {
        val allNodes = snapshot.allNodes

        // Layer 1: Node-level isPassword flag
        for (node in allNodes) {
            if (node.isPassword) {
                return BoundaryCheckResult(
                    isBoundary = true,
                    layerTriggered = 1,
                    reason = "Password field detected on screen: isPassword == true"
                )
            }
        }

        // Layer 2: InputType password variations
        for (node in allNodes) {
            if (isPasswordInputType(node.inputType)) {
                return BoundaryCheckResult(
                    isBoundary = true,
                    layerTriggered = 2,
                    reason = "Password inputType detected (flags: ${node.inputType})"
                )
            }
        }

        // Layer 3: Sensitive text patterns & keywords
        val textMatches = mutableListOf<String>()
        for (node in allNodes) {
            val text = "${node.text ?: ""} ${node.contentDescription ?: ""}"
            if (text.isBlank()) continue

            when {
                otpPattern.containsMatchIn(text) -> textMatches.add("OTP")
                cvvPattern.containsMatchIn(text) -> textMatches.add("CVV")
                pinPattern.containsMatchIn(text) -> textMatches.add("PIN")
                cardPattern.containsMatchIn(text) -> textMatches.add("Card Details")
                paymentActionPattern.containsMatchIn(text) -> textMatches.add("Payment Action")
                biometricPattern.containsMatchIn(text) -> textMatches.add("Biometric Prompt")
                bankingPasswordPattern.containsMatchIn(text) -> textMatches.add("Banking Password")
            }
        }
        if (textMatches.isNotEmpty()) {
            return BoundaryCheckResult(
                isBoundary = true,
                layerTriggered = 3,
                reason = "Credential or payment keywords detected: ${textMatches.distinct().joinToString(", ")}",
                detectedKeywords = textMatches.distinct()
            )
        }

        // Layer 4: Payment gateway or banking package
        val pkg = snapshot.packageName.lowercase()
        if (paymentPackages.contains(pkg) || pkg.contains(".payment") || pkg.contains(".checkout") || pkg.contains(".upi") || pkg.contains(".banking")) {
            return BoundaryCheckResult(
                isBoundary = true,
                layerTriggered = 4,
                reason = "External payment gateway or banking package detected: ${snapshot.packageName}"
            )
        }

        // Layer 5: Activity name matching payment / checkout
        val activity = snapshot.activityName
        if (activity != null && paymentActivityKeywords.any { activity.contains(it, ignoreCase = true) }) {
            return BoundaryCheckResult(
                isBoundary = true,
                layerTriggered = 5,
                reason = "Checkout or payment activity detected: $activity"
            )
        }

        // Layer 5b: Combined payment option list detection (e.g. Google Pay, PhonePe, Cards, UPI listed on same screen)
        var paymentOptionCount = 0
        val paymentOptionKeywords = listOf("google pay", "phonepe", "paytm", "upi", "credit / debit card", "netbanking", "cash on delivery")
        val combinedScreenText = allNodes.joinToString(" ") { "${it.text ?: ""} ${it.contentDescription ?: ""}" }.lowercase()
        for (opt in paymentOptionKeywords) {
            if (combinedScreenText.contains(opt)) {
                paymentOptionCount++
            }
        }
        if (paymentOptionCount >= 3) {
            return BoundaryCheckResult(
                isBoundary = true,
                layerTriggered = 5,
                reason = "Multiple payment options detected on screen ($paymentOptionCount options identified)"
            )
        }

        return BoundaryCheckResult.SAFE
    }

    fun isNodeDangerous(node: UiNode): Boolean {
        if (node.isPassword || isPasswordInputType(node.inputType)) return true
        val text = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase()
        return otpPattern.containsMatchIn(text) ||
                cvvPattern.containsMatchIn(text) ||
                pinPattern.containsMatchIn(text) ||
                cardPattern.containsMatchIn(text) ||
                paymentActionPattern.containsMatchIn(text) ||
                bankingPasswordPattern.containsMatchIn(text)
    }

    private fun isPasswordInputType(type: Int): Boolean {
        if (type == 0) return false
        val classMask = type and InputType.TYPE_MASK_CLASS
        val variationMask = type and InputType.TYPE_MASK_VARIATION

        if (classMask == InputType.TYPE_CLASS_TEXT) {
            if (variationMask == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variationMask == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variationMask == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return true
            }
        }
        if (classMask == InputType.TYPE_CLASS_NUMBER) {
            if (variationMask == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                return true
            }
        }
        return false
    }
}
