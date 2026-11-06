package com.aadil.spool.core.data.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsBackend(context: Context) : AnalyticsBackend {
    private val analytics = runCatching {
        FirebaseAnalytics.getInstance(context).apply { setAnalyticsCollectionEnabled(false) }
    }.getOrNull()
    override val available: Boolean get() = analytics != null

    override fun setCollectionEnabled(enabled: Boolean) {
        val sdk = analytics ?: return
        if (!enabled) sdk.setAnalyticsCollectionEnabled(false)
        sdk.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
        ))
        sdk.setAnalyticsCollectionEnabled(enabled)
        if (!enabled) sdk.resetAnalyticsData()
    }

    override fun record(event: UsageEvent) { analytics?.logEvent(event.eventName, null) }
}
