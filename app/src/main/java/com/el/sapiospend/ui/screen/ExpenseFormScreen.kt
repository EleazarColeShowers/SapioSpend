package com.el.sapiospend.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.payment.PaymentStatus
import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.receipt.ReceiptStore
import com.el.sapiospend.ui.component.DayCalendarDialog
import com.el.sapiospend.ui.component.ReceiptImage
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.DateUtils
import com.el.sapiospend.util.formatAmountInput
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.settings.ActiveCurrency
import kotlinx.coroutines.launch

/**
 * Everything the form collected, in one value.
 *
 * A data class rather than a ten-argument lambda: the form now carries a vendor, a
 * payment position, a due date and a receipt on top of the original four fields, and a
 * positional callback that long is one reordering away from filing every amount as a
 * date.
 */
data class ExpenseFormResult(
    val eventId: String,
    val title: String,
    val category: String,
    val amount: Double,
    val notes: String,
    val date: Long,
    val vendor: String,
    val amountPaid: Double,
    val dueDate: Long?,
    val receiptPath: String?
)

/**
 * One form for recording an expense and for correcting one.
 *
 * The two are the same form because they are the same decision — what was bought, from
 * whom, for how much, against which category, on what day, and how much of it has
 * actually been paid. A separate edit screen would be the same fields written twice, and
 * the pair would drift: the category list would gain a planned category on one screen and
 * not the other, and only one of them would ever get the date field.
 */
@Composable
fun ExpenseFormScreen(
    /** The event this expense belongs to, and the one a new expense is recorded against. */
    eventId: String,
    /**
     * Every event, so an expense recorded against the wrong one can be moved. Left empty
     * when there is nothing to move it to, which hides the picker entirely.
     */
    events: List<EventEntity> = emptyList(),
    /** Budget lines across all events; the category chips come from whichever is selected. */
    budgetLines: List<BudgetLineEntity> = emptyList(),
    /** The expense being corrected, or null when recording a new one. */
    existing: ExpenseEntity? = null,
    onBack: () -> Unit = {},
    onSave: (ExpenseFormResult) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val receipts = remember(context) { ReceiptStore(context) }
    val isEditing = existing != null

    var targetEventId by remember(eventId) { mutableStateOf(eventId) }

    /**
     * Categories the selected event actually budgeted for. They lead the list so spend
     * lands against the plan — a generic "Food" chip recorded against a plan that says
     * "Catering & Drinks" would make every expense look unplanned and leave the whole
     * planned-vs-actual comparison reading as broken.
     */
    fun plannedFor(id: String) = budgetLines.filter { it.eventId == id }.map { it.category }

    val fallbackCategories =
        listOf("Food", "Venue", "Transport", "Decoration", "Entertainment", "Clothing", "Others")

    var selectedCategory by remember(existing, eventId) {
        mutableStateOf(
            existing?.category
                ?: (plannedFor(eventId) + fallbackCategories).first { it.isNotBlank() }
        )
    }

    val categories = remember(budgetLines, targetEventId, existing, selectedCategory) {
        // The selection is appended rather than promoted: it guarantees the chosen chip
        // is on screen after a move to an event that never planned for it, without the
        // list reordering itself under the user's finger as they tap along it.
        (listOfNotNull(existing?.category) + plannedFor(targetEventId) + fallbackCategories + selectedCategory)
            .filter { it.isNotBlank() }
            .distinct()
    }

    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var vendor by remember(existing) { mutableStateOf(existing?.vendor.orEmpty()) }
    var amount by remember(existing) { mutableStateOf(existing?.amount?.formatAmountInput().orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var date by remember(existing) { mutableLongStateOf(existing?.dateCreated ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    /**
     * A new expense defaults to paid.
     *
     * That is what recording an expense has meant in every version of this app, and it
     * is what most entries are — the money has already gone and the user is writing it
     * down. Defaulting to unpaid would file every ordinary entry as a debt and put an
     * outstanding balance on a screen where nothing is outstanding.
     */
    var paymentStatus by remember(existing) {
        mutableStateOf(existing?.let(Payments::statusOf) ?: PaymentStatus.PAID)
    }
    var deposit by remember(existing) {
        mutableStateOf(existing?.takeIf { Payments.statusOf(it) == PaymentStatus.PARTIAL }?.amountPaid?.formatAmountInput().orEmpty())
    }

    var dueDate by remember(existing) { mutableStateOf(existing?.dueDate) }
    var showDuePicker by remember { mutableStateOf(false) }

    var receiptPath by remember(existing) { mutableStateOf(existing?.receiptPath) }
    var showReceipt by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }

    /**
     * Receipts written by this screen that are not (yet) the saved one.
     *
     * The file has to be copied in the moment it is picked — that is when the read grant
     * on the picked URI is valid — but the expense may never be saved. Tracking what was
     * written is what stops a user who attaches three photos and backs out from leaving
     * three orphans in storage forever.
     */
    val writtenPaths = remember { mutableStateListOf<String>() }

    val pickReceipt = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        attaching = true
        scope.launch {
            receipts.save(uri)?.let { saved ->
                writtenPaths += saved
                receiptPath = saved
            }
            attaching = false
        }
    }

    /** Drops every file this screen wrote except [keep], which is the one being committed. */
    fun cleanUpReceipts(keep: String?) {
        val orphans = writtenPaths.filter { it != keep }
        if (orphans.isEmpty()) return
        scope.launch { orphans.forEach { receipts.delete(it) } }
    }

    fun leave() {
        // Nothing is saved, so every file this screen wrote is an orphan — including one
        // that would have replaced the receipt already on the expense, which is left
        // exactly where it was.
        cleanUpReceipts(keep = null)
        onBack()
    }

    if (showDatePicker) {
        DayCalendarDialog(
            initialDay = date,
            title = "Date of expense",
            onDismiss = { showDatePicker = false },
            onConfirm = { day ->
                date = DateUtils.instantOnDay(day)
                showDatePicker = false
            }
        )
    }

    if (showDuePicker) {
        DayCalendarDialog(
            initialDay = dueDate ?: System.currentTimeMillis(),
            title = "Payment due",
            onDismiss = { showDuePicker = false },
            onConfirm = { day ->
                dueDate = DateUtils.instantOnDay(day)
                showDuePicker = false
            }
        )
    }

    if (showReceipt && receiptPath != null) {
        Dialog(onDismissRequest = { showReceipt = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptImage(
                        path = receiptPath!!,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )
                    TextButton(onClick = { showReceipt = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = AppColors.Black)
                    }
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.OnSurface,
        unfocusedTextColor = AppColors.OnSurface,
        focusedLabelColor = AppColors.Black,
        unfocusedLabelColor = AppColors.Secondary,
        focusedPlaceholderColor = AppColors.Border,
        unfocusedPlaceholderColor = AppColors.Border,
        focusedBorderColor = AppColors.Black,
        unfocusedBorderColor = AppColors.Border,
        cursorColor = AppColors.Black,
        focusedContainerColor = AppColors.Surface,
        unfocusedContainerColor = AppColors.Surface
    )

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val depositValue = deposit.toDoubleOrNull() ?: 0.0
    val depositTooLarge = paymentStatus == PaymentStatus.PARTIAL && depositValue > amountValue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BG)
            // The form grew a date row and can carry a long planned-category list, which
            // together outrun a short screen with the keyboard open.
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::leave) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.Secondary)
            }
            Spacer(Modifier.width(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isEditing) "Edit Expense" else "Add Expense",
                    color = AppColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    if (isEditing) "Correct what you recorded" else "Record a spending item",
                    color = AppColors.Secondary,
                    fontSize = 13.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title") },
                    placeholder = { Text("e.g. Catering service") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                // Separate from the title because it is the thing people search on later:
                // "what have I paid Chidi" is a question about the payee, and answering it
                // out of a free-text title only works if every title happens to name one.
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor / paid to (optional)") },
                    placeholder = { Text("e.g. Chidi Catering") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount (${ActiveCurrency.value.symbol})") },
                    placeholder = { Text("e.g. 25000") },
                    supportingText = { Text("The full cost, whether or not it is all paid yet", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                // --- Payment ---------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payment", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaymentStatus.entries.forEach { status ->
                            FilterChip(
                                selected = paymentStatus == status,
                                onClick = { paymentStatus = status },
                                label = { Text(status.label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = AppColors.BG,
                                    labelColor = AppColors.Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = paymentStatus == status,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.Black
                                )
                            )
                        }
                    }

                    if (paymentStatus == PaymentStatus.PARTIAL) {
                        OutlinedTextField(
                            value = deposit,
                            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) deposit = v },
                            label = { Text("Deposit paid (${ActiveCurrency.value.symbol})") },
                            placeholder = { Text("e.g. 100000") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true,
                            isError = depositTooLarge,
                            supportingText = {
                                Text(
                                    if (depositTooLarge) "A deposit cannot be more than the amount"
                                    else "${(amountValue - depositValue).coerceAtLeast(0.0).formatMoney()} still owing",
                                    color = if (depositTooLarge) AppColors.Danger else AppColors.Secondary,
                                    fontSize = 11.sp
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }

                    // A due date is only a question worth asking while money is still
                    // owed; on a settled expense it would be a field with nothing to say.
                    if (paymentStatus != PaymentStatus.PAID) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AppColors.BG, RoundedCornerShape(12.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    showDuePicker = true
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dueDate?.let { "Balance due ${it.formatDate()}" } ?: "Add a due date",
                                color = if (dueDate != null) AppColors.OnSurface else AppColors.Secondary,
                                fontSize = 14.sp
                            )
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Set due date",
                                tint = AppColors.Secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (dueDate != null) {
                            TextButton(
                                onClick = { dueDate = null },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Remove due date", color = AppColors.Secondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // --- Receipt ---------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Receipt", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        receiptPath?.let { path ->
                            ReceiptImage(
                                path = path,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable { showReceipt = true }
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                pickReceipt.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !attaching,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (attaching) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Secondary)
                            } else {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppColors.Secondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (receiptPath == null) "Attach a photo" else "Replace",
                                color = AppColors.OnSurface,
                                fontSize = 13.sp
                            )
                        }
                        if (receiptPath != null) {
                            IconButton(onClick = { receiptPath = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove receipt",
                                    tint = AppColors.Secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    // The photo picker on Android 13+ needs no permission at all, and the
                    // system picker below it grants access to the single chosen image —
                    // worth saying, because "attach a photo" reads like a gallery
                    // permission request to anyone who has used an older app.
                    Text(
                        "Stored inside the app. Nothing else can read it.",
                        color = AppColors.Border,
                        fontSize = 11.sp
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. receipt #, agreed terms") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.BG, RoundedCornerShape(12.dp))
                            .clickable {
                                focusManager.clearFocus()
                                showDatePicker = true
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date.formatDate(), color = AppColors.OnSurface, fontSize = 14.sp)
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Change date",
                            tint = AppColors.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Offered only when correcting an expense: a new one is always recorded
                // from inside an event, so asking which event it belongs to would be
                // asking a question the user has already answered.
                if (isEditing && events.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Event", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            events.forEach { candidate ->
                                FilterChip(
                                    selected = targetEventId == candidate.id,
                                    onClick = { targetEventId = candidate.id },
                                    label = { Text(candidate.name, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppColors.BG,
                                        labelColor = AppColors.Secondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = targetEventId == candidate.id,
                                        borderColor = AppColors.Border,
                                        selectedBorderColor = AppColors.Black
                                    )
                                )
                            }
                        }
                        if (targetEventId != eventId) {
                            Text(
                                "Moving this expense takes its amount off the old event's total and onto this one.",
                                color = AppColors.Secondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = AppColors.BG,
                                    labelColor = AppColors.Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == category,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.Black
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val paid = when (paymentStatus) {
                            PaymentStatus.PAID -> amountValue
                            PaymentStatus.UNPAID -> 0.0
                            PaymentStatus.PARTIAL -> depositValue.coerceIn(0.0, amountValue)
                        }
                        val committed = receiptPath
                        // The save is going through, so anything this screen wrote and is
                        // not committing is now genuinely orphaned — including the photo
                        // the expense used to carry, if it has been replaced or removed.
                        cleanUpReceipts(keep = committed)
                        existing?.receiptPath
                            ?.takeIf { it != committed }
                            ?.let { old -> scope.launch { receipts.delete(old) } }

                        onSave(
                            ExpenseFormResult(
                                eventId = targetEventId,
                                title = title.trim(),
                                category = selectedCategory,
                                amount = amountValue,
                                notes = notes.trim(),
                                date = date,
                                vendor = vendor.trim(),
                                amountPaid = paid,
                                // A settled expense keeps no deadline: the date would sit
                                // in the database waiting to make a paid line look overdue
                                // if its amount were later corrected upwards.
                                dueDate = dueDate.takeIf { paymentStatus != PaymentStatus.PAID },
                                receiptPath = committed
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Black,
                        contentColor = Color.White,
                        disabledContainerColor = AppColors.Border,
                        disabledContentColor = AppColors.Secondary
                    ),
                    enabled = title.isNotBlank() && amount.isNotBlank() && !depositTooLarge
                ) {
                    Text(if (isEditing) "Save Changes" else "Save Expense", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
