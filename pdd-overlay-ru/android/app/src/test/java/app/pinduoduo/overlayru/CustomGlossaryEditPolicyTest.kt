package app.pinduoduo.overlayru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGlossaryEditPolicyTest {
    @Test fun renameMovesExistingSourceKey() {
        assertTrue(CustomGlossaryEditPolicy.canReplace(
            "查看物流", "查看订单", "Просмотреть заказ", listOf("查看物流")
        ))
    }

    @Test fun editingTranslationWithoutRenamingIsAllowed() {
        assertTrue(CustomGlossaryEditPolicy.canReplace(
            "查看物流", " 查看物流 ", "Отследить доставку", listOf("查看物流")
        ))
    }

    @Test fun renameDoesNotOverwriteAnotherCorrection() {
        assertFalse(CustomGlossaryEditPolicy.canReplace(
            "查看物流", "查看订单", "Заказы", listOf("查看物流", "查看订单")
        ))
    }

    @Test fun editingDeletedEntryIsRejected() {
        assertFalse(CustomGlossaryEditPolicy.canReplace(
            "查看物流", "查看订单", "Заказы", listOf("商品")
        ))
    }

    @Test fun paymentLabelCannotBeIntroducedByRename() {
        assertFalse(CustomGlossaryEditPolicy.canReplace(
            "查看物流", "立即支付", "Оплатить", listOf("查看物流")
        ))
    }
}
