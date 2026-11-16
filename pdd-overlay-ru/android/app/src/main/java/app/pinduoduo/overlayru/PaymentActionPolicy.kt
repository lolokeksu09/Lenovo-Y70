package app.pinduoduo.overlayru

import java.util.Locale

/**
 * Never cover irreversible purchase/payment/refund action labels with a
 * machine-generated overlay. Users must see the original action before tapping.
 *
 * Only exact, normalized action labels are excluded; product descriptions,
 * informative payment-method labels and normal navigation stay translatable.
 */
internal object PaymentActionPolicy {
    private val protectedActions = setOf(
        "支付", "付款", "立即支付", "立即付款", "去支付", "去付款",
        "确认支付", "确认付款", "继续支付", "提交订单", "确认订单",
        "确认下单", "确认购买", "确认收货", "确认退款", "申请退款",
        "pay now", "confirm payment", "place order", "submit order",
        "confirm purchase", "confirm order", "buy now", "confirm refund"
    )

    @JvmStatic
    fun isProtected(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val normalized = text.trim()
            .trimEnd(':', '：', '!', '！', ' ')
            .trim()
            .lowercase(Locale.ROOT)
        return normalized in protectedActions
    }
}
