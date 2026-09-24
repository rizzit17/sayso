package com.samsung.prism.teachable.security

import android.text.InputType
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialBoundaryDetectorTest {

    private val detector = CredentialBoundaryDetector()

    private fun createSnapshot(
        pkg: String = "com.application.zomato",
        activity: String = "MainActivity",
        nodes: List<UiNode> = emptyList()
    ): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            packageName = pkg,
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = pkg,
            activityName = activity,
            rootNode = root,
            screenSignature = "sig_test"
        )
    }

    @Test
    fun testLayer1_IsPasswordFlag() {
        val pwNode = UiNode(
            resourceId = "com.app:id/pw_field",
            isPassword = true,
            semanticRole = "input"
        )
        val snapshot = createSnapshot(nodes = listOf(pwNode))
        val result = detector.checkBoundary(snapshot)

        assertTrue(result.isBoundary)
        assertEquals(1, result.layerTriggered)
        assertTrue(result.reason.contains("isPassword == true"))
    }

    @Test
    fun testLayer2_InputTypeVariations() {
        val textPwNode = UiNode(
            resourceId = "com.app:id/text_pw",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val resultText = detector.checkBoundary(createSnapshot(nodes = listOf(textPwNode)))
        assertTrue(resultText.isBoundary)
        assertEquals(2, resultText.layerTriggered)

        val numPwNode = UiNode(
            resourceId = "com.app:id/num_pw",
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        val resultNum = detector.checkBoundary(createSnapshot(nodes = listOf(numPwNode)))
        assertTrue(resultNum.isBoundary)
        assertEquals(2, resultNum.layerTriggered)

        val webPwNode = UiNode(
            resourceId = "com.app:id/web_pw",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val resultWeb = detector.checkBoundary(createSnapshot(nodes = listOf(webPwNode)))
        assertTrue(resultWeb.isBoundary)
        assertEquals(2, resultWeb.layerTriggered)
    }

    @Test
    fun testLayer3_SensitiveKeywords_Otp() {
        val node = UiNode(text = "Enter OTP sent to +91-9876543210")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("OTP"))
    }

    @Test
    fun testLayer3_SensitiveKeywords_Cvv() {
        val node = UiNode(text = "Enter 3-digit CVV / CVC on back of card")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("CVV"))
    }

    @Test
    fun testLayer3_SensitiveKeywords_UpiPin() {
        val node = UiNode(text = "Enter 6-digit UPI PIN to authorize transaction")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("PIN"))
    }

    @Test
    fun testLayer3_SensitiveKeywords_CardDetails() {
        val node = UiNode(contentDescription = "Credit card number field, valid thru MM/YY")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("Card Details"))
    }

    @Test
    fun testLayer3_SensitiveKeywords_PaymentAction() {
        val node = UiNode(text = "Proceed to Pay Rs. 499", semanticRole = "button")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("Payment Action"))
    }

    @Test
    fun testLayer3_SensitiveKeywords_Biometric() {
        val node = UiNode(text = "Touch the fingerprint sensor to approve payment")
        val result = detector.checkBoundary(createSnapshot(nodes = listOf(node)))

        assertTrue(result.isBoundary)
        assertEquals(3, result.layerTriggered)
        assertTrue(result.detectedKeywords.contains("Biometric Prompt"))
    }

    @Test
    fun testLayer4_PaymentGatewayPackage() {
        val phonePeSnapshot = createSnapshot(pkg = "com.phonepe.app")
        val result1 = detector.checkBoundary(phonePeSnapshot)
        assertTrue(result1.isBoundary)
        assertEquals(4, result1.layerTriggered)

        val gpaySnapshot = createSnapshot(pkg = "com.google.android.apps.nbu.paisa.user")
        val result2 = detector.checkBoundary(gpaySnapshot)
        assertTrue(result2.isBoundary)
        assertEquals(4, result2.layerTriggered)

        val razorpaySnapshot = createSnapshot(pkg = "com.razorpay.payments")
        val result3 = detector.checkBoundary(razorpaySnapshot)
        assertTrue(result3.isBoundary)
        assertEquals(4, result3.layerTriggered)
    }

    @Test
    fun testLayer5_PaymentActivity() {
        val snapshot = createSnapshot(
            pkg = "com.application.zomato",
            activity = "com.application.zomato.payment.PaymentActivity"
        )
        val result = detector.checkBoundary(snapshot)

        assertTrue(result.isBoundary)
        assertEquals(5, result.layerTriggered)
    }

    @Test
    fun testLayer5b_MultiplePaymentOptionsScreen() {
        val nodes = listOf(
            UiNode(text = "Google Pay (UPI)"),
            UiNode(text = "PhonePe"),
            UiNode(text = "Paytm Wallet"),
            UiNode(text = "Cash on Delivery")
        )
        val snapshot = createSnapshot(nodes = nodes)
        val result = detector.checkBoundary(snapshot)

        assertTrue(result.isBoundary)
        assertEquals(5, result.layerTriggered)
        assertTrue(result.reason.contains("Multiple payment options"))
    }

    @Test
    fun testSafeScreens_PassInspection() {
        // 1. Food Menu Screen
        val menuNodes = listOf(
            UiNode(text = "Margherita Pizza - Fresh tomato basil and mozzarella", semanticRole = "card"),
            UiNode(text = "Add to Cart", semanticRole = "button", clickable = true),
            UiNode(text = "View Cart", semanticRole = "button", clickable = true)
        )
        val menuSnapshot = createSnapshot(activity = "RestaurantDetailActivity", nodes = menuNodes)
        assertFalse(detector.checkBoundary(menuSnapshot).isBoundary)

        // 2. Search Screen
        val searchNodes = listOf(
            UiNode(text = "Restaurant name or a dish...", semanticRole = "search_box"),
            UiNode(text = "Recent searches: Domino's, Subway", semanticRole = "text_view")
        )
        val searchSnapshot = createSnapshot(activity = "SearchActivity", nodes = searchNodes)
        assertFalse(detector.checkBoundary(searchSnapshot).isBoundary)

        // 3. Cart Summary Screen before payment selection
        val cartNodes = listOf(
            UiNode(text = "Cart Summary"),
            UiNode(text = "1x Margherita Pizza"),
            UiNode(text = "Delivery Address: Home"),
            UiNode(text = "Subtotal: Rs. 299")
        )
        val cartSnapshot = createSnapshot(activity = "CartActivity", nodes = cartNodes)
        assertFalse(detector.checkBoundary(cartSnapshot).isBoundary)
    }
}
