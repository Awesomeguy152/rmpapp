package com.nano.min.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nano.min.R
import com.nano.min.network.ExtractedMeetingDto
import com.nano.min.network.MeetingDto
import com.nano.min.viewmodel.MeetingsUiState
import com.nano.min.viewmodel.MeetingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsScreen(
    viewModel: MeetingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var meetingToEdit by remember { mutableStateOf<MeetingDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.clearState() // Очищаем старые данные перед загрузкой
        viewModel.loadMeetings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meetings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_meeting)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.meetings.isEmpty() -> {
                    EmptyMeetingsView(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    MeetingsList(
                        meetings = uiState.meetings,
                        onAccept = { viewModel.acceptMeeting(it.id) },
                        onDecline = { viewModel.declineMeeting(it.id) },
                        onDelete = { viewModel.deleteMeeting(it.id) },
                        onEdit = { meetingToEdit = it },
                        formatDate = { viewModel.formatDate(it) }
                    )
                }
            }
        }
    }

    // Error snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Auto-clear after showing
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    // Диалог создания встречи
    if (showCreateDialog) {
        CreateMeetingDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, dateTime, location ->
                viewModel.createMeetingManually(
                    title = title,
                    description = description,
                    scheduledAt = dateTime,
                    location = location
                )
                showCreateDialog = false
            }
        )
    }
    
    // Диалог редактирования встречи
    meetingToEdit?.let { meeting ->
        EditMeetingDialog(
            meeting = meeting,
            onDismiss = { meetingToEdit = null },
            onSave = { title, description, dateTime, location ->
                viewModel.updateMeeting(
                    meetingId = meeting.id,
                    title = title,
                    description = description,
                    scheduledAt = dateTime,
                    location = location
                )
                meetingToEdit = null
            }
        )
    }
}

@Composable
private fun EmptyMeetingsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.meetings_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MeetingsList(
    meetings: List<MeetingDto>,
    onAccept: (MeetingDto) -> Unit,
    onDecline: (MeetingDto) -> Unit,
    onDelete: (MeetingDto) -> Unit,
    onEdit: (MeetingDto) -> Unit,
    formatDate: (String) -> String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(meetings, key = { it.id }) { meeting ->
            MeetingCard(
                meeting = meeting,
                onAccept = { onAccept(meeting) },
                onDecline = { onDecline(meeting) },
                onDelete = { onDelete(meeting) },
                onEdit = { onEdit(meeting) },
                formatDate = formatDate
            )
        }
    }
}

@Composable
private fun MeetingCard(
    meeting: MeetingDto,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    formatDate: (String) -> String
) {
    val statusColor = when (meeting.status) {
        "confirmed" -> MaterialTheme.colorScheme.primary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with title and AI badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                
                if (meeting.aiGenerated) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            meeting.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Date and location row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDate(meeting.scheduledAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                meeting.location?.let { location ->
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status badge - показываем статус участия пользователя
            val displayStatus = meeting.participantStatus ?: meeting.status
            val statusColor2 = when (displayStatus) {
                "accepted" -> MaterialTheme.colorScheme.primary
                "declined" -> MaterialTheme.colorScheme.error
                "confirmed" -> MaterialTheme.colorScheme.primary
                "cancelled" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            
            Surface(
                color = statusColor2.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (displayStatus) {
                        "accepted" -> stringResource(R.string.meeting_confirmed)
                        "confirmed" -> stringResource(R.string.meeting_confirmed)
                        "declined", "cancelled" -> stringResource(R.string.meeting_cancelled)
                        else -> stringResource(R.string.meeting_pending)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor2
                )
            }

            // Action buttons (only for pending participant status)
            val participantStatus = meeting.participantStatus ?: "pending"
            if (participantStatus == "pending" && meeting.status != "cancelled") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.meeting_decline))
                    }
                    
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.meeting_accept))
                    }
                }
            }

            // Delete button (for declined or cancelled meetings)
            if (participantStatus == "declined" || meeting.status == "cancelled") {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.meeting_delete))
                }
            }
            
            // Edit and Delete buttons (для подтверждённых встреч)
            if (participantStatus == "accepted" || meeting.status == "confirmed") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.meeting_edit))
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.meeting_delete))
                    }
                }
            }
        }
    }
}

/**
 * Диалог с предложениями встреч от AI
 */
@Composable
fun ExtractedMeetingsDialog(
    extractedMeetings: List<ExtractedMeetingDto>,
    onCreateMeeting: (ExtractedMeetingDto) -> Unit,
    onDismiss: () -> Unit,
    formatDate: (String) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ai_meetings_found, extractedMeetings.size))
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(extractedMeetings) { meeting ->
                    ExtractedMeetingItem(
                        meeting = meeting,
                        onCreate = { onCreateMeeting(meeting) },
                        formatDate = formatDate
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExtractedMeetingItem(
    meeting: ExtractedMeetingDto,
    onCreate: () -> Unit,
    formatDate: (String) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            meeting.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            
            meeting.dateTime?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            meeting.location?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Confidence indicator
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.meeting_confidence, (meeting.confidence * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Button(
                    onClick = onCreate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.create_meeting))
                }
            }
        }
    }
}

/**
 * Диалог для ручного создания встречи
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMeetingDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, dateTime: String, location: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.create_meeting),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Название встречи
                OutlinedTextField(
                    value = title,
                    onValueChange = { 
                        title = it
                        titleError = false
                    },
                    label = { Text(stringResource(R.string.meeting_title_label)) },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Описание
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.meeting_description_label)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Дата (формат DD.MM.YYYY)
                OutlinedTextField(
                    value = date,
                    onValueChange = { 
                        date = it
                        dateError = false
                    },
                    label = { Text(stringResource(R.string.meeting_date_label)) },
                    placeholder = { Text("25.01.2026") },
                    isError = dateError,
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Время (формат HH:MM)
                OutlinedTextField(
                    value = time,
                    onValueChange = { 
                        time = it
                        timeError = false
                    },
                    label = { Text(stringResource(R.string.meeting_time_label)) },
                    placeholder = { Text("14:30") },
                    isError = timeError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Место
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.meeting_location_label)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Валидация
                    var hasError = false
                    if (title.isBlank()) {
                        titleError = true
                        hasError = true
                    }
                    if (!date.matches(Regex("""\d{2}\.\d{2}\.\d{4}"""))) {
                        dateError = true
                        hasError = true
                    }
                    if (!time.matches(Regex("""\d{2}:\d{2}"""))) {
                        timeError = true
                        hasError = true
                    }
                    
                    if (!hasError) {
                        // Конвертируем в ISO формат
                        val parts = date.split(".")
                        val isoDateTime = "${parts[2]}-${parts[1]}-${parts[0]}T${time}:00Z"
                        onCreate(
                            title,
                            description.ifBlank { null },
                            isoDateTime,
                            location.ifBlank { null }
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.create_meeting))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * Диалог для редактирования встречи
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMeetingDialog(
    meeting: MeetingDto,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, dateTime: String, location: String?) -> Unit
) {
    // Парсим существующую дату
    val existingDate = try {
        val instant = java.time.Instant.parse(meeting.scheduledAt)
        val zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault())
        Triple(
            String.format("%02d.%02d.%04d", zonedDateTime.dayOfMonth, zonedDateTime.monthValue, zonedDateTime.year),
            String.format("%02d:%02d", zonedDateTime.hour, zonedDateTime.minute),
            true
        )
    } catch (e: Exception) {
        Triple("", "", false)
    }
    
    var title by remember { mutableStateOf(meeting.title) }
    var description by remember { mutableStateOf(meeting.description ?: "") }
    var location by remember { mutableStateOf(meeting.location ?: "") }
    var date by remember { mutableStateOf(existingDate.first) }
    var time by remember { mutableStateOf(existingDate.second) }
    var titleError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.meeting_edit),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Название встречи
                OutlinedTextField(
                    value = title,
                    onValueChange = { 
                        title = it
                        titleError = false
                    },
                    label = { Text(stringResource(R.string.meeting_title_label)) },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Описание
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.meeting_description_label)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Дата (формат DD.MM.YYYY)
                OutlinedTextField(
                    value = date,
                    onValueChange = { 
                        date = it
                        dateError = false
                    },
                    label = { Text(stringResource(R.string.meeting_date_label)) },
                    placeholder = { Text("25.01.2026") },
                    isError = dateError,
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Время (формат HH:MM)
                OutlinedTextField(
                    value = time,
                    onValueChange = { 
                        time = it
                        timeError = false
                    },
                    label = { Text(stringResource(R.string.meeting_time_label)) },
                    placeholder = { Text("14:30") },
                    isError = timeError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Место
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.meeting_location_label)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Валидация
                    var hasError = false
                    if (title.isBlank()) {
                        titleError = true
                        hasError = true
                    }
                    if (!date.matches(Regex("""\d{2}\.\d{2}\.\d{4}"""))) {
                        dateError = true
                        hasError = true
                    }
                    if (!time.matches(Regex("""\d{2}:\d{2}"""))) {
                        timeError = true
                        hasError = true
                    }
                    
                    if (!hasError) {
                        // Конвертируем в ISO формат
                        val parts = date.split(".")
                        val isoDateTime = "${parts[2]}-${parts[1]}-${parts[0]}T${time}:00Z"
                        onSave(
                            title,
                            description.ifBlank { null },
                            isoDateTime,
                            location.ifBlank { null }
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
