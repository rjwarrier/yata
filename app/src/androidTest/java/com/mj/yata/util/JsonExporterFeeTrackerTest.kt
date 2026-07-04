package com.mj.yata.util

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.feetracker.backup.FeeTaskLinkRestore
import com.mj.yata.feetracker.data.local.entity.ClientEntity
import com.mj.yata.feetracker.data.local.entity.FirmProfileEntity
import com.mj.yata.feetracker.data.local.entity.GroupEntity
import com.mj.yata.feetracker.data.local.entity.InvoiceCounterEntity
import com.mj.yata.feetracker.data.local.entity.InvoiceEntity
import com.mj.yata.feetracker.data.local.entity.InvoiceLineItemEntity
import com.mj.yata.feetracker.data.local.entity.PaymentEntity
import com.mj.yata.feetracker.domain.model.ClientStatus
import com.mj.yata.feetracker.domain.model.EntityType
import com.mj.yata.feetracker.domain.model.InvoiceStatus
import com.mj.yata.feetracker.domain.model.InvoiceType
import com.mj.yata.feetracker.domain.model.PaymentMode
import com.mj.yata.feetracker.domain.model.PaymentType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonExporterFeeTrackerTest {

    private lateinit var database: AppDatabase
    private lateinit var exporter: JsonExporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = JsonExporter(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun feeTrackerJson_serializesAllFeeCollections() = runBlocking {
        val groupId = database.groupDao().insert(
            GroupEntity(groupName = "SME Clients", notes = "Priority")
        )
        val clientId = database.clientDao().insert(
            ClientEntity(
                name = "Aditi & Co",
                entityType = EntityType.COMPANY,
                groupId = groupId,
                status = ClientStatus.ACTIVE,
                pan = "ABCDE1234F"
            )
        )
        val invoiceId = database.invoiceDao().insert(
            InvoiceEntity(
                invoiceNumber = "INV/2025-26/0001",
                type = InvoiceType.TAX_INVOICE,
                clientId = clientId,
                dateRaised = 1_717_200_000_000,
                dueDate = 1_717_286_400_000,
                subTotalPaise = 25_000,
                taxableValuePaise = 25_000,
                totalPaise = 25_000,
                settledPaise = 10_000,
                status = InvoiceStatus.PARTIALLY_PAID,
                financialYear = "2025-26"
            )
        )
        database.invoiceDao().insertLineItem(
            InvoiceLineItemEntity(
                invoiceId = invoiceId,
                description = "GST filing",
                quantity = 1,
                ratePaise = 25_000,
                amountPaise = 25_000
            )
        )
        database.paymentDao().insert(
            PaymentEntity(
                receiptNumber = "RCPT-0001",
                clientId = clientId,
                invoiceId = invoiceId,
                type = PaymentType.PAYMENT,
                amountReceivedPaise = 10_000,
                dateReceived = 1_717_243_200_000,
                mode = PaymentMode.BANK_TRANSFER
            )
        )
        database.invoiceCounterDao().upsert(InvoiceCounterEntity("2025-26", 1))
        database.firmProfileDao().upsert(
            FirmProfileEntity(
                firmName = "Ledger CA",
                proprietorName = "Mira Shah",
                invoicePrefix = "INV"
            )
        )

        val section = invokeFeeTrackerJson()

        assertEquals("Ledger CA", section.getJSONObject("firmProfile").getString("firmName"))
        assertEquals(1, section.getJSONArray("groups").length())
        assertEquals(1, section.getJSONArray("clients").length())
        assertEquals(1, section.getJSONArray("invoices").length())
        assertEquals(1, section.getJSONArray("invoiceLineItems").length())
        assertEquals(1, section.getJSONArray("payments").length())
        assertEquals(1, section.getJSONArray("invoiceCounters").length())
    }

    @Test
    fun exportImport_roundTripsFeeDataAndTaskLinks() = runBlocking {
        val groupId = database.groupDao().insert(
            GroupEntity(groupName = "SME Clients", notes = "Priority")
        )
        val clientId = database.clientDao().insert(
            ClientEntity(
                name = "Aditi & Co",
                entityType = EntityType.COMPANY,
                groupId = groupId,
                status = ClientStatus.ACTIVE,
                pan = "ABCDE1234F"
            )
        )
        val invoiceId = database.invoiceDao().insert(
            InvoiceEntity(
                invoiceNumber = "INV/2025-26/0001",
                type = InvoiceType.TAX_INVOICE,
                clientId = clientId,
                dateRaised = 1_717_200_000_000,
                dueDate = 1_717_286_400_000,
                subTotalPaise = 25_000,
                taxableValuePaise = 25_000,
                totalPaise = 25_000,
                settledPaise = 10_000,
                status = InvoiceStatus.PARTIALLY_PAID,
                financialYear = "2025-26"
            )
        )
        database.invoiceDao().insertLineItem(
            InvoiceLineItemEntity(
                invoiceId = invoiceId,
                description = "GST filing",
                quantity = 1,
                ratePaise = 25_000,
                amountPaise = 25_000
            )
        )
        database.paymentDao().insert(
            PaymentEntity(
                receiptNumber = "RCPT-0001",
                clientId = clientId,
                invoiceId = invoiceId,
                type = PaymentType.PAYMENT,
                amountReceivedPaise = 10_000,
                dateReceived = 1_717_243_200_000,
                mode = PaymentMode.BANK_TRANSFER
            )
        )
        database.invoiceCounterDao().upsert(InvoiceCounterEntity("2025-26", 1))
        database.firmProfileDao().upsert(
            FirmProfileEntity(
                firmName = "Ledger CA",
                proprietorName = "Mira Shah",
                invoicePrefix = "INV"
            )
        )
        val taskId = database.taskDao().insertTask(
            com.mj.yata.data.local.db.entity.TaskEntity(
                title = "Collect GST filing fees",
                createdAt = 1_717_200_000_000,
                updatedAt = 1_717_200_000_000,
                clientId = clientId,
                feeInvoiceId = invoiceId
            )
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val exportFile = kotlin.io.path.createTempFile("fee-export", ".json").toFile()
        val exportUri = Uri.fromFile(exportFile)
        assertTrue(exporter.exportData(exportUri))

        val importedDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val importer = JsonExporter(importedDb, context)
            assertTrue(importer.importData(exportUri))

            assertEquals(1, importedDb.groupDao().getAll().size)
            assertEquals(1, importedDb.clientDao().getAll().size)
            assertEquals(1, importedDb.invoiceDao().getAll().size)
            assertEquals(1, importedDb.invoiceDao().getAllLineItems().size)
            assertEquals(1, importedDb.paymentDao().getAll().size)
            assertEquals(1, importedDb.invoiceCounterDao().getAll().size)
            assertNotNull(importedDb.firmProfileDao().get())

            val importedTask = importedDb.taskDao().getAllTasks().single()
            val importedClient = importedDb.clientDao().getAll().single()
            val importedInvoice = importedDb.invoiceDao().getAll().single()
            assertEquals("Collect GST filing fees", importedTask.title)
            assertEquals(importedClient.id, importedTask.clientId)
            assertEquals(importedInvoice.id, importedTask.feeInvoiceId)

            val resolved = FeeTaskLinkRestore.resolve(
                oldClientId = clientId,
                oldInvoiceId = invoiceId,
                maps = com.mj.yata.feetracker.backup.FeeImportMaps(
                    clientIdMap = mapOf(clientId to importedClient.id),
                    invoiceIdMap = mapOf(invoiceId to importedInvoice.id)
                )
            )
            assertEquals(importedClient.id, resolved.clientId)
            assertEquals(importedInvoice.id, resolved.feeInvoiceId)
            assertTrue(importedTask.id > 0L)
        } finally {
            importedDb.close()
            exportFile.delete()
        }
    }

    private suspend fun invokeFeeTrackerJson(): JSONObject {
        val method = JsonExporter::class.java.getDeclaredMethod(
            "feeTrackerJson",
            FirmProfileEntity::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java
        )
        method.isAccessible = true
        return method.invoke(
            exporter,
            database.firmProfileDao().get(),
            database.groupDao().getAll(),
            database.clientDao().getAll(),
            database.invoiceDao().getAll(),
            database.invoiceDao().getAllLineItems(),
            database.paymentDao().getAll(),
            database.invoiceCounterDao().getAll()
        ) as JSONObject
    }
}
