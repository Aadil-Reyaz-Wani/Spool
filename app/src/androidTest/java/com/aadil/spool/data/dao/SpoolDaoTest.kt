package com.aadil.spool.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.entity.Filament
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@SmallTest
class SpoolDaoTest {

    private lateinit var database: SpoolDatabase
    private lateinit var dao: SpoolDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpoolDatabase::class.java,
        ).allowMainThreadQueries().build()

        dao = database.spoolDao()
    }

    @After
    fun teardown() {
        database.close()
    }


    @Test
    fun insertSpool() = runBlocking {
        val filament = Filament(
            1,
            "Brand",
            "PLA",
            "White",
            0xFFFFFFFF,
            200.0,
            120.0,
            98,
            23,
            "This is my Note"
        )

        dao.insertSpool(filament)

        val getSpoolById = dao.getSpool(1).first()

        assertThat(getSpoolById).isEqualTo(filament)
    }

    @Test
    fun deleteSpool_returnsTrue() = runBlocking {
        val filament = Filament(
            1,
            "Overture",
            "PLA",
            "White",
            0xFFFFFFFF,
            200.0,
            120.0,
            98,
            23,
            "This is my Note"
        )

        dao.insertSpool(filament)

        dao.deleteSpool(filament)

        val getSpoolById = dao.getSpool(1).first()

        assertThat(getSpoolById).isNotEqualTo(filament)
    }


    @Test
    fun updateSpool_returnTrue() = runBlocking {

        val filament = Filament(
            1,
            "Overture",
            "PLA",
            "White",
            0xFFFFFFFF,
            200.0,
            120.0,
            98,
            23,
            "This is my Note"
        )

        dao.insertSpool(filament)

        val updateSpoolName = filament.copy(
            brand = "Aadil",
            currentWeight = 100.0
        )

        dao.updateSpool(updateSpoolName)

        val getSpoolById = dao.getSpool(1)

        assertThat(getSpoolById.first()).isEqualTo(updateSpoolName)

    }


    @Test
    fun updateCurrentWeight_returnTrue() = runBlocking {
        val filament = Filament(
            1,
            "Overture",
            "PLA",
            "White",
            0xFFFFFFFF,
            200.0,
            120.0,
            98,
            23,
            "This is my Note"
        )

        dao.insertSpool(filament)

        dao.updateCurrentWeight(1, 90.0)

        val getSpoolById = dao.getSpool(1).first()

        assertThat(getSpoolById).isNotEqualTo(filament) // If this is not equals to the one which we insert first then the weight is updated successfully

    }

}













