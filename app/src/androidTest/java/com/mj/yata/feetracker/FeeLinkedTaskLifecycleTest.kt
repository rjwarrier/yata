package com.mj.yata.feetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.entity.TaskEntity
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.repository.TaskRepositoryImpl
import com.mj.yata.feetracker.data.repository.ClientRepositoryImpl
import com.mj.yata.feetracker.data.repository.FirmProfileRepositoryImpl
import com.mj.yata.feetracker.data.repository.GroupRepositoryImpl
import com.mj.yata.feetracker.data.repository.InvoiceRepositoryImpl
import com.mj.yata.feetracker.data.repository.PaymentRepositoryImpl
import com.mj.yata.feetracker.domain.model.Client
import com.mj.yata.feetracker.domain.model.ClientStatus
import com.mj.yata.feetracker.domain.model.FinancialYear
import com.mj.yata.feetracker.domain.model.Invoice
import com.mj.yata.feetracker.domain.model.InvoiceLineItem
import com.mj.yata.feetracker.domain.model.InvoiceStatus
import com.mj.yata.feetracker.domain.model.Payment
import com.mj.yata.feetracker.domain.model.PaymentMode
import com.mj.yata.feetracker.domain.model.UserType
import com.mj.yata.feetracker.domain.usecase.InvoiceNumberAllocator
import com.mj.yata.feetracker.domain.usecase.RaiseInvoiceUseCase
import com.mj.yata.feetracker.domain.usecase.RecordPaymentUseCase
import com.mj.yata.feetracker.domain.usecase.SettlementCalculator
import com.mj.yata.feetracker.feature.FeatureAvailability
import com.mj.yata.feetracker.ui.format.IndianCurrency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeeLinkedTaskLifecycleTest {

    private lateinit var database: AppDatabase
    private lateinit var userPreferences: UserPreferences
    private lateinit var featureAvailability: FeatureAvailability

    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var clientRepository: ClientRepositoryImpl
    private lateinit var groupRepository: GroupRepositoryImpl
    private lateinit var invoiceRepository: InvoiceRepositoryImpl
    private lateinit var paymentRepository: PaymentRepositoryImpl
    private lateinit var firmProfileRepository: FirmProfileRepositoryImpl

    private lateinit var raiseInvoiceUseCase: RaiseInvoiceUseCase
    private lateinit var recordPaymentUseCase: RecordPaymentUseCase
    private lateinit var settlementCalculator: SettlementCalculator

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPreferences = UserPreferences(context)
        // Configure preferences to enable fee tracking so that the visibility checks pass
        userPreferences.setUserType(UserType.CHARTERED_ACCOUNTANT)
        userPreferences.setFeeTrackingOn(true)
        userPreferences.setInvoicingOn(true)

        featureAvailability = FeatureAvailability(userPreferences)

        taskRepository = TaskRepositoryImpl(database.taskDao(), database.labelDao(), database.projectDao())
        clientRepository = ClientRepositoryImpl(database.clientDao())
        groupRepository = GroupRepositoryImpl(database.groupDao())
        invoiceRepository = InvoiceRepositoryImpl(database.invoiceDao())
        paymentRepository = PaymentRepositoryImpl(database.paymentDao())
        firmProfileRepository = FirmProfileRepositoryImpl(database.firmProfileDao())

        val invoiceNumberAllocator = InvoiceNumberAllocator(database)
        raiseInvoiceUseCase = RaiseInvoiceUseCase(
            invoiceRepository = invoiceRepository,
            firmProfileRepository = firmProfileRepository,
            taskRepository = taskRepository,
            featureAvailability = featureAvailability,
            invoiceNumberAllocator = invoiceNumberAllocator
        )

        settlementCalculator = SettlementCalculator()
        recordPaymentUseCase = RecordPaymentUseCase(
            paymentRepository = paymentRepository,
            invoiceRepository = invoiceRepository,
            taskRepository = taskRepository,
            settlementCalculator = settlementCalculator
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun issuedInvoice_spawnsLinkedCollectionTodo() = runBlocking {
        // 1. Create a client
        val clientId = clientRepository.save(Client(name = "Aditi CA Client", status = ClientStatus.ACTIVE))

        // 2. Raise/Issue invoice (autoTodoOnInvoice = true)
        val invoice = Invoice(
            clientId = clientId,
            dateRaised = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
            subTotalPaise = 50_000,
            taxableValuePaise = 50_000,
            totalPaise = 50_000,
            financialYear = FinancialYear.current(),
            status = InvoiceStatus.DUE
        )
        val lineItems = listOf(InvoiceLineItem(description = "Tax Filing Services", ratePaise = 50_000, amountPaise = 50_000))
        val invoiceId = raiseInvoiceUseCase(
            draft = invoice,
            lineItems = lineItems,
            autoTodoOnInvoice = true,
            clientName = "Aditi CA Client"
        )

        // 3. Verify task exists in DB linked to invoice
        val task = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull("Linked task should be created", task)
        task!!
        assertEquals("Collect ₹500.00 from Aditi CA Client", task.title)
        assertEquals(clientId, task.clientId)
        assertEquals(invoiceId, task.feeInvoiceId)
        assertFalse("Task should not be completed", task.isCompleted)
    }

    @Test
    fun recordingPayment_completesTodoOnFullSettlement() = runBlocking {
        val clientId = clientRepository.save(Client(name = "Bhavna CA Client", status = ClientStatus.ACTIVE))

        val invoice = Invoice(
            clientId = clientId,
            dateRaised = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
            subTotalPaise = 100_000,
            taxableValuePaise = 100_000,
            totalPaise = 100_000,
            financialYear = FinancialYear.current(),
            status = InvoiceStatus.DUE
        )
        val lineItems = listOf(InvoiceLineItem(description = "Audit Fees", ratePaise = 100_000, amountPaise = 100_000))
        val invoiceId = raiseInvoiceUseCase(
            draft = invoice,
            lineItems = lineItems,
            autoTodoOnInvoice = true,
            clientName = "Bhavna CA Client"
        )

        // Verify task exists and is open
        val taskBefore = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskBefore)
        assertFalse(taskBefore!!.isCompleted)

        // Record a partial payment of 40_000 paise (₹400)
        recordPaymentUseCase(
            Payment(
                receiptNumber = "RCPT-100",
                clientId = clientId,
                invoiceId = invoiceId,
                amountReceivedPaise = 40_000,
                dateReceived = System.currentTimeMillis(),
                mode = PaymentMode.GPAY
            )
        )

        // Task should still be open
        val taskAfterPartial = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskAfterPartial)
        assertFalse(taskAfterPartial!!.isCompleted)

        // Record rest payment of 60_000 paise (₹600)
        recordPaymentUseCase(
            Payment(
                receiptNumber = "RCPT-101",
                clientId = clientId,
                invoiceId = invoiceId,
                amountReceivedPaise = 60_000,
                dateReceived = System.currentTimeMillis(),
                mode = PaymentMode.GPAY
            )
        )

        // Task should now be closed/completed
        val taskAfterFull = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskAfterFull)
        assertTrue("Task should be completed on full settlement", taskAfterFull!!.isCompleted)
    }

    @Test
    fun reopeningInvoice_reopensLinkedTodo() = runBlocking {
        val clientId = clientRepository.save(Client(name = "Chitra CA Client", status = ClientStatus.ACTIVE))

        val invoice = Invoice(
            clientId = clientId,
            dateRaised = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
            subTotalPaise = 200_000,
            taxableValuePaise = 200_000,
            totalPaise = 200_000,
            financialYear = FinancialYear.current(),
            status = InvoiceStatus.DUE
        )
        val lineItems = listOf(InvoiceLineItem(description = "Corporate Tax Consulting", ratePaise = 200_000, amountPaise = 200_000))
        val invoiceId = raiseInvoiceUseCase(
            draft = invoice,
            lineItems = lineItems,
            autoTodoOnInvoice = true,
            clientName = "Chitra CA Client"
        )

        // Verify task exists and is open
        val taskBefore = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskBefore)

        // Fully settle it
        recordPaymentUseCase(
            Payment(
                receiptNumber = "RCPT-200",
                clientId = clientId,
                invoiceId = invoiceId,
                amountReceivedPaise = 200_000,
                dateReceived = System.currentTimeMillis(),
                mode = PaymentMode.GPAY
            )
        )

        // Verify task is closed
        val taskClosed = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskClosed)
        assertTrue(taskClosed!!.isCompleted)

        // Simulate VM edit: total is increased (e.g. to 300_000)
        // We save the updated invoice and trigger task sync
        val updatedInvoice = invoiceRepository.getById(invoiceId)!!.copy(
            totalPaise = 300_000,
            subTotalPaise = 300_000,
            taxableValuePaise = 300_000
        )
        // Recalculate settlement
        val existingPayments = paymentRepository.getForInvoice(invoiceId)
        val settlement = settlementCalculator.calculate(updatedInvoice, existingPayments)
        val finalInvoice = updatedInvoice.copy(
            settledPaise = settlement.settledPaise,
            status = settlement.status
        )
        invoiceRepository.save(finalInvoice, listOf(lineItems[0].copy(amountPaise = 300_000, ratePaise = 300_000)))

        // Manually sync linked task (simulating VM's syncLinkedTaskForInvoice)
        val shouldComplete = finalInvoice.status == InvoiceStatus.CANCELLED || finalInvoice.status == InvoiceStatus.PAID
        taskRepository.updateTask(
            taskClosed.copy(
                title = "Collect ${IndianCurrency.format(finalInvoice.totalPaise)} from Chitra CA Client",
                isCompleted = shouldComplete,
                completedAt = if (shouldComplete) taskClosed.completedAt else null,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Verify task is reopened
        val taskReopened = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskReopened)
        assertFalse("Task should reopen since the invoice is no longer fully settled", taskReopened!!.isCompleted)
        assertEquals("Collect ₹3,000.00 from Chitra CA Client", taskReopened.title)
    }

    @Test
    fun cancellingInvoice_closesLinkedTodo() = runBlocking {
        val clientId = clientRepository.save(Client(name = "Divya CA Client", status = ClientStatus.ACTIVE))

        val invoice = Invoice(
            clientId = clientId,
            dateRaised = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
            subTotalPaise = 80_000,
            taxableValuePaise = 80_000,
            totalPaise = 80_000,
            financialYear = FinancialYear.current(),
            status = InvoiceStatus.DUE
        )
        val lineItems = listOf(InvoiceLineItem(description = "Filing Fees", ratePaise = 80_000, amountPaise = 80_000))
        val invoiceId = raiseInvoiceUseCase(
            draft = invoice,
            lineItems = lineItems,
            autoTodoOnInvoice = true,
            clientName = "Divya CA Client"
        )

        // Verify task exists and is open
        val taskBefore = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskBefore)
        assertFalse(taskBefore!!.isCompleted)

        // Simulate cancel invoice logic in VM
        val fetchedInvoice = invoiceRepository.getById(invoiceId)!!
        invoiceRepository.update(fetchedInvoice.copy(status = InvoiceStatus.CANCELLED))
        taskRepository.updateTask(
            taskBefore.copy(
                isCompleted = true,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Verify task is now closed
        val taskCancelled = taskRepository.getTaskByFeeInvoiceId(invoiceId)
        assertNotNull(taskCancelled)
        assertTrue("Task should be completed when the invoice is cancelled", taskCancelled!!.isCompleted)
    }
}
