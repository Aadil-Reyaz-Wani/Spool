package com.aadil.spool.core.model

data class FAQ(
    val question: String,
    val answer: String
)

object SpoolLists {
    val materialTypes = listOf(
        "PLA",
        "ABS",
        "PETG",
        "PET",
        "PC",
        "TPU",
        "ASA",
        "PA",
        "PA-CF",
    )

    val currencyType = listOf(
        "USD", // US Dollar
        "EUR", // Euro
        "INR", // Indian Rupee
        "GBP", // British Pound
        "CAD", // Canadian Dollar
        "AUD", // Australian Dollar
    )

    val faqItem = listOf(
        FAQ(
            question = "How do I update the remaining filament?",
            answer = "From the dashboard, tap a spool to open its Details screen. Scroll to the bottom and tap the 'Update' button to open the Edit screen, where you can adjust its properties."
        ),
        FAQ(
            question = "Where can I change the currency?",
            answer = "Tap the gear icon on the main dashboard to open the settings menu and select your local currency from the supported list."
        ),
        FAQ(
            question = "Is my data backed up to the cloud?",
            answer = "Spool is an offline-first tracker. Your data is currently stored securely on your local device for maximum speed, with optional cloud backups planned for a future update."
        ),
    )
}
