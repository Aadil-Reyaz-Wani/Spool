package com.aadil.spool.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log

private const val CHANNEL_ID = "low_stock"

class AndroidNotificationPoster(private val context: Context) : NotificationPoster {

    override fun post(items: List<LowStockItem>, batched: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        Log.d("LowStock", "post() items=${items.size} batched=$batched enabled=${manager.areNotificationsEnabled()}")
        if (!manager.areNotificationsEnabled()) return

        for ((index, item) in items.withIndex()) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            builder.setContentIntent(openSpoolPendingIntent(context, item.spoolId))
            item.reorderUrl?.let { url ->
                builder.addAction(0, "Reorder", reorderPendingIntent(context, url))
            }

            manager.notify(1000 + index, builder.build())
        }
    }

    private fun openSpoolPendingIntent(context: Context, spoolId: Int): PendingIntent {
        // Resolves via the manifest intent-filter; shared can't reference the app's MainActivity class.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spool://$spoolId")).apply {
            `package` = context.packageName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            spoolId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reorderPendingIntent(context: Context, url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Low stock alerts", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
