package com.aadil.spool.notifications

import platform.Foundation.NSNumber
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptions
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptions
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import org.koin.dsl.module

// ponytail: no BGTaskScheduler background refresh on iOS — checks run when the app opens;
// add a BGAppRefreshTask in AppDelegate if background checks are ever needed.
class IosNotificationPoster : NotificationPoster {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun post(items: List<LowStockItem>, batched: Boolean, actionLabel: String) {
        ensureCategory()
        items.forEachIndexed { index, item ->
            val content = UNMutableNotificationContent()
            content.title = item.title
            content.body = item.body
            content.sound = UNNotificationSound.defaultSound()
            content.categoryIdentifier = CATEGORY_ID

            val info = mutableMapOf<Any?, Any?>()
            if (!batched && item.spoolId > 0) info["spoolId"] = NSNumber(item.spoolId)
            if (!batched) item.reorderUrl?.let { info["reorderUrl"] = it }
            if (info.isNotEmpty()) content.setUserInfo(info)

            val request = UNNotificationRequest.requestWithIdentifier(
                "low_stock_${item.spoolId}_$index",
                content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false),
            )
            center.addNotificationRequest(request) { error ->
                if (error != null) println("Failed to post low-stock notification: ${error.localizedDescription}")
            }
        }
    }

    private fun ensureCategory() {
        val open = UNNotificationAction.actionWithIdentifier(ACTION_OPEN, "Open", UNNotificationActionOptions.Foreground)
        val reorder = UNNotificationAction.actionWithIdentifier(ACTION_REORDER, "Reorder", UNNotificationActionOptions.Foreground)
        val category = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY_ID,
            listOf(open, reorder),
            emptyList<String>(),
            UNNotificationCategoryOptions.None,
        )
        center.getNotificationCategoriesWithCompletionHandler { existing ->
            if (existing?.contains(category) != true) {
                center.setNotificationCategories(setOf(category))
            }
        }
    }

    companion object {
        const val CATEGORY_ID = "LOW_STOCK"
        const val ACTION_OPEN = "OPEN"
        const val ACTION_REORDER = "REORDER"
    }
}

val iosNotificationsModule = module {
    single<NotificationPoster> { IosNotificationPoster() }
}
