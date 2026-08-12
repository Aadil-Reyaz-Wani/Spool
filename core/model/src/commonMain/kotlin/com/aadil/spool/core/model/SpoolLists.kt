package com.aadil.spool.core.model

data class FAQ(
    val question: String,
    val answer: String
)

data class SpoolTare(
    val name: String,
    val grams: Double
)

fun computeRemainingWeight(grossWeight: Double, tareWeight: Double): Double? {
    val remaining = grossWeight - tareWeight
    return remaining.takeIf { grossWeight > 0 && tareWeight >= 0 && remaining >= 0 }
}

fun isWeighInSuspicious(storedRemaining: Double, enteredRemaining: Double?, marginGrams: Double = 100.0): Boolean =
    enteredRemaining != null && storedRemaining - enteredRemaining > marginGrams

enum class MoistureLevel { UNKNOWN, DRY, LOW, HIGH }

enum class MaterialMoisture(
    val dryTempC: Int,
    val dryHours: Int,
    val dryBelowGrams: Double,
    val lowBelowGrams: Double
) {
    LOW(50, 4, 8.0, 15.0),          // PLA, ABS, ASA
    MEDIUM(65, 6, 5.0, 12.0),       // PETG, PET, unknown materials
    HIGH(65, 6, 3.0, 8.0),          // TPU, PC
    VERY_HIGH(80, 8, 3.0, 8.0);     // PA, PA-CF
}

val materialMoistureSpecs: Map<String, MaterialMoisture> = mapOf(
    "PLA" to MaterialMoisture.LOW,
    "ABS" to MaterialMoisture.LOW,
    "ASA" to MaterialMoisture.LOW,
    "PETG" to MaterialMoisture.MEDIUM,
    "PET" to MaterialMoisture.MEDIUM,
    "PC" to MaterialMoisture.HIGH,
    "TPU" to MaterialMoisture.HIGH,
    "PA" to MaterialMoisture.VERY_HIGH,
    "PA-CF" to MaterialMoisture.VERY_HIGH,
)

fun materialMoisture(material: String): MaterialMoisture =
    materialMoistureSpecs[material.trim().uppercase()] ?: MaterialMoisture.MEDIUM

data class MoistureVerdict(
    val level: MoistureLevel,
    val absorbedGrams: Double,
    val tareMismatch: Boolean,
    val dryTempC: Int,
    val dryHours: Int
)

fun moistureVerdict(
    material: String,
    dryBaselineWeight: Double?,
    currentWeight: Double,
    dryBaselineTareGrams: Double?,
    checkTareGrams: Double?,
): MoistureVerdict {
    val spec = materialMoisture(material)
    if (dryBaselineWeight == null) {
        return MoistureVerdict(
            level = MoistureLevel.UNKNOWN,
            absorbedGrams = 0.0,
            tareMismatch = false,
            dryTempC = spec.dryTempC,
            dryHours = spec.dryHours
        )
    }
    val absorbedGrams = (currentWeight - dryBaselineWeight).coerceAtLeast(0.0)
    val level = when {
        absorbedGrams < spec.dryBelowGrams -> MoistureLevel.DRY
        absorbedGrams < spec.lowBelowGrams -> MoistureLevel.LOW
        else -> MoistureLevel.HIGH
    }
    val tareMismatch = dryBaselineTareGrams != null && checkTareGrams != null &&
        kotlin.math.abs(dryBaselineTareGrams - checkTareGrams) > 0.01
    return MoistureVerdict(
        level = level,
        absorbedGrams = absorbedGrams,
        tareMismatch = tareMismatch,
        dryTempC = spec.dryTempC,
        dryHours = spec.dryHours
    )
}

object SpoolLists {
    const val DEFAULT_TARE_GRAMS = 140.0
    const val CUSTOM_TARE_LABEL = "Custom…"

    val emptySpoolWeights = listOf(
        SpoolTare("Cardboard spool", 140.0),
        SpoolTare("Plastic spool", 220.0),
        SpoolTare("Bambu reusable spool", 208.0),
        SpoolTare("Bambu cardboard spool", 205.0),
        SpoolTare("eSun spool", 200.0),
        SpoolTare("Prusament spool", 200.0),
        SpoolTare("Sunlu spool", 230.0),
        SpoolTare("Refill (no spool)", 0.0),
    )

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
