package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class PaymentActionPolicyTest {
    @Test fun protectsPaymentAndOrderConfirmation() {
        for (value in listOf("立即支付", "确认付款", "提交订单", "确认收货", "Pay now", "buy now!", "确认退款：")) {
            assertTrue("Must not cover: $value", PaymentActionPolicy.isProtected(value))
        }
    }

    @Test fun ordinaryShoppingAndPaymentInformationStillTranslate() {
        for (value in listOf("支付方式", "商品详情", "查看物流", "优惠券", "Shipping options", "商品评价", "支付成功", "查看订单")) {
            assertFalse("Should remain translatable: $value", PaymentActionPolicy.isProtected(value))
        }
    }

    @Test fun nullEmptyAndUnrelatedTextNotProtected() {
        assertFalse(PaymentActionPolicy.isProtected(null))
        assertFalse(PaymentActionPolicy.isProtected(""))
        assertFalse(PaymentActionPolicy.isProtected("  "))
        assertFalse(PaymentActionPolicy.isProtected("确认支付方式"))
    }
}
