package com.aadil.spool.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var context: Context
    private val testDbName = "spool_legacy_test.db"
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun testLegacyDataRetentionWithKmpRoom270() = runBlocking {
        // 1. Pre-populate a mock database with legacy records (simulating the current Play Store build)
        val legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacyDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `filaments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `brand` TEXT NOT NULL,
                `material` TEXT NOT NULL,
                `color_name` TEXT NOT NULL,
                `color_hex` INTEGER NOT NULL,
                `total_weight` REAL NOT NULL DEFAULT 0,
                `current_weight` REAL NOT NULL DEFAULT 0,
                `temp_nozzle` INTEGER NOT NULL DEFAULT 0,
                `temp_bed` INTEGER NOT NULL DEFAULT 0,
                `note` TEXT NOT NULL DEFAULT '',
                `price` REAL NOT NULL DEFAULT 0,
                `timestamp` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        legacyDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `usage_log` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `spoolId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `grams_used` REAL NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `is_failure` INTEGER NOT NULL,
                `price_per_print` REAL NOT NULL DEFAULT 0.0,
                FOREIGN KEY(`spoolId`) REFERENCES `filaments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        legacyDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)
            """.trimIndent()
        )
        legacyDb.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'mock_hash')"
        )

        // Insert Legacy Filament Record
        legacyDb.execSQL(
            """
            INSERT INTO filaments (id, brand, material, color_name, color_hex, total_weight, current_weight, temp_nozzle, temp_bed, note, price, timestamp)
            VALUES (1, 'Prusament', 'PLA', 'Galaxy Black', 4280821800, 1000.0, 750.0, 215, 60, 'Production Spool', 29.99, 1700000000000)
            """.trimIndent()
        )

        // Insert Legacy Usage Log
        legacyDb.execSQL(
            """
            INSERT INTO usage_log (id, spoolId, title, grams_used, timestamp, is_failure, price_per_print)
            VALUES (100, 1, 'Benchy Test', 15.5, 1700000001000, 0, 0.46)
            """.trimIndent()
        )
        legacyDb.close()

        // 2. Open with Room 2.7.0+ KMP Builder
        val kmpDatabase = Room.databaseBuilder<SpoolDatabase>(
            context = context,
            name = dbFile.absolutePath
        ).setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration() // Safe fallback if hash mismatch in test, but schema matches 100%
            .build()

        val dao = kmpDatabase.spoolDao()

        // 3. Verify 100% Data Retention
        val spools = dao.getAllSpools().first()
        assertThat(spools).hasSize(1)
        val spool = spools.first()
        assertThat(spool.id).isEqualTo(1)
        assertThat(spool.brand).isEqualTo("Prusament")
        assertThat(spool.material).isEqualTo("PLA")
        assertThat(spool.colorName).isEqualTo("Galaxy Black")
        assertThat(spool.currentWeight).isEqualTo(750.0)
        assertThat(spool.tempNozzle).isEqualTo(215)
        assertThat(spool.price).isEqualTo(29.99)
        assertThat(spool.dryBaselineWeight).isNull()
        assertThat(spool.dryBaselineTareGrams).isNull()
        assertThat(spool.lastWeighedTareGrams).isNull()
        assertThat(spool.lastDriedAt).isNull()

        val logs = dao.getSpoolUsage(spoolId = 1).first()
        assertThat(logs).hasSize(1)
        val log = logs.first()
        assertThat(log.id).isEqualTo(100)
        assertThat(log.title).isEqualTo("Benchy Test")
        assertThat(log.gramsUsed).isEqualTo(15.5)
        assertThat(log.isFailure).isFalse()

        kmpDatabase.close()
    }
}
