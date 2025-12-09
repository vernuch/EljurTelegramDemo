package com.example.eljurtelegramdemo

import com.example.eljurtelegramdemo.eljur.EljurRemoteDataSource
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Add
import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.eljurtelegramdemo.data.*
import com.example.eljurtelegramdemo.telegram.TelegramService
import com.example.eljurtelegramdemo.telegram.TelegramService.SavedMessage
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import com.example.eljurtelegramdemo.ui.theme.EljurTelegramDemoTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.eljurtelegramdemo.eljur.EljurAuthRemoteDataSource
import com.example.eljurtelegramdemo.eljur.EljurConfig
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableIntStateOf

private const val PREFS_NAME = "telegram_prefs"
private const val KEY_TELEGRAM_CHANNEL = "telegram_channel"

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase

    private val schedulePrefs by lazy {
        getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TelegramService.init(applicationContext)
        val telegramPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedChannel = telegramPrefs.getString(KEY_TELEGRAM_CHANNEL, "") ?: ""
        TelegramService.setCustomChannel(savedChannel)
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "eljur_app.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val schedulePrefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        schedulePrefs.edit {
            putString("current_date", today)
        }

        lifecycleScope.launch {
            DemoDataInitializer.ensureDemoData(db)

            val bellSchedule = db.bellScheduleDao().getAll().firstOrNull()
            if (bellSchedule.isNullOrEmpty()) {
                val defaultSchedule = (1..8).map { number ->
                    val startHour = 8 + (number - 1) * 2
                    BellScheduleEntity(
                        lessonNumber = number,
                        startTime = String.format("%02d:00", startHour),
                        endTime = String.format("%02d:50", startHour),
                        isActive = number <= 6
                    )
                }
                db.bellScheduleDao().insertAll(defaultSchedule)
            }

            val hasSchedule = db.scheduleDao().getCount() > 0
            if (!hasSchedule) {
                regenerateFullSchedule(db)
            }
        }

        setContent {
            EljurApp(db = db)
        }
    }
}

// нижний бар
private enum class BottomTab(val label: String) {
    Schedule("Расписание"),
    Tasks("Задания"),
    Attestation("Аттестации"),
    Messages("Сообщения")
}

// связь с элжуром
@Composable
fun EljurApp(db: AppDatabase) {
    val context = LocalContext.current
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    var isEljurLoggedIn by rememberSaveable {
        mutableStateOf(
            context.getSharedPreferences("eljur_prefs", Context.MODE_PRIVATE)
                .getBoolean("eljur_logged_in", false)
        )
    }

    EljurTelegramDemoTheme(darkTheme = isDarkTheme) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.back),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
            ) {

                MainScaffold(
                    db = db,
                    isDarkTheme = isDarkTheme,
                    isEljurLoggedIn = isEljurLoggedIn,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onEljurLoginChange = { loggedIn ->
                        isEljurLoggedIn = loggedIn
                        context.getSharedPreferences("eljur_prefs", Context.MODE_PRIVATE)
                            .edit { putBoolean("eljur_logged_in", loggedIn) }
                    },
                    onLogout = {
                        (context as? Activity)?.finish()
                    }
                )
            }
        }
    }
}

// каркас
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    db: AppDatabase,
    isDarkTheme: Boolean,
    isEljurLoggedIn: Boolean,
    onToggleTheme: () -> Unit,
    onEljurLoginChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Schedule) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openTelegramTab by rememberSaveable { mutableStateOf(false) }
    var showEljurLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showEditSchedule by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        when {
            showEditSchedule -> {
                showEditSchedule = false
            }
            showSettings -> {
                showSettings = false
            }
            selectedTab != BottomTab.Schedule -> {
                selectedTab = BottomTab.Schedule
            }
            else -> {
                activity?.finish()
            }
        }
    }

    if (showEditSchedule) {
        EditScheduleScreen(
            onBack = { showEditSchedule = false },
            onSave = {
                Toast.makeText(context, "Расписание сохранено", Toast.LENGTH_SHORT).show()
            },
            db = db
        )
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            if (showSettings) "Настройки"
                            else selectedTab.label
                        )
                    },
                    navigationIcon = {
                        if (showSettings) {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    actions = {
                        if (!showSettings) {
                            IconButton(onClick = {
                                showSettings = true
                                openTelegramTab = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Настройки"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!showSettings && !showEditSchedule) {
                    NavigationBar {
                        BottomTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    openTelegramTab = false
                                },
                                icon = {
                                    when (tab) {
                                        BottomTab.Schedule ->
                                            Icon(Icons.Default.CalendarToday, contentDescription = null)
                                        BottomTab.Tasks ->
                                            Icon(Icons.Default.Assignment, contentDescription = null)
                                        BottomTab.Attestation ->
                                            Icon(Icons.Default.School, contentDescription = null)
                                        BottomTab.Messages ->
                                            Icon(Icons.Default.ChatBubble, contentDescription = null)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showSettings) {
                    SettingsScreen(
                        db = db,
                        isDarkTheme = isDarkTheme,
                        isEljurLoggedIn = isEljurLoggedIn,
                        onToggleTheme = onToggleTheme,
                        onLogoutApp = onLogout,
                        onNavigateToTelegramAuth = {
                            showSettings = false
                            selectedTab = BottomTab.Messages
                            openTelegramTab = true
                        },
                        onShowEljurLogin = {
                            showEljurLoginDialog = true
                        },
                        onEljurLogout = {
                            onEljurLoginChange(false)
                        },
                        onEditSchedule = {
                            showSettings = false
                            showEditSchedule = true
                        }
                    )
                } else {
                    when (selectedTab) {
                        BottomTab.Schedule -> ScheduleScreen(db)
                        BottomTab.Tasks -> TasksScreen(db)
                        BottomTab.Attestation -> AttestationsScreen(db)
                        BottomTab.Messages -> MessagesScreen(
                            db = db,
                            openTelegramTab = openTelegramTab
                        )
                    }
                }
            }
        }
    }

    if (showEljurLoginDialog) {
        EljurLoginDialog(
            onLogin = { login, password ->
                onEljurLoginChange(true)
                showEljurLoginDialog = false
                Toast.makeText(context, "ЭлЖур успешно подключен", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showEljurLoginDialog = false
            }
        )
    }
}

//экран расписания
@Composable
fun ScheduleScreen(db: AppDatabase) {
    val context = LocalContext.current
    val schedulePrefs = remember {
        context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
    }

    var currentDate by rememberSaveable {
        mutableStateOf(
            schedulePrefs.getString("current_date", todayString()) ?: todayString()
        )
    }

    LaunchedEffect(Unit) {
        val savedDate = schedulePrefs.getString("current_date", null)
        if (savedDate == null) {
            schedulePrefs.edit {
                putString("current_date", todayString())
            }
        }
    }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val weekDates = remember(currentDate) {
        getWeekDates(currentDate)
    }

    val bellSchedule by db.bellScheduleDao().getAll().collectAsState(initial = emptyList())

    val lessons by db.scheduleDao()
        .getScheduleForDate(currentDate)
        .collectAsState(initial = emptyList())

    val activeLessons = remember(lessons, bellSchedule) {
        lessons.filter { lesson ->
            val bell = bellSchedule.find { it.lessonNumber == lesson.lessonNumber }
            bell?.isActive == true && lesson.subject.isNotBlank()
        }.map { lesson ->
            val bell = bellSchedule.find { it.lessonNumber == lesson.lessonNumber }
            if (bell != null) {
                lesson.copy(
                    startTime = bell.startTime,
                    endTime = bell.endTime
                )
            } else {
                lesson
            }
        }.sortedBy { it.lessonNumber }
    }

    val hasAnyLessons = remember(lessons) {
        lessons.any { it.subject.isNotBlank() }
    }

    val activeLessonNumbers = remember(bellSchedule) {
        bellSchedule.filter { it.isActive }.map { it.lessonNumber }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateStringToMillis(currentDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            currentDate = millisToDateString(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        WeekSelector(
            weekDates = weekDates,
            currentDate = currentDate,
            onDateSelected = { dateStr ->
                currentDate = dateStr
            }
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    try {
                        val date = sdf.parse(currentDate)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            calendar.add(Calendar.DAY_OF_YEAR, -1)
                            currentDate = sdf.format(calendar.time)
                        }
                    } catch (e: Exception) {
                    }
                }
            ) {
                Text("<", style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { showDatePicker = true }
            ) {
                Text(
                    text = formatDateHuman(currentDate),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    try {
                        val date = sdf.parse(currentDate)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                            currentDate = sdf.format(calendar.time)
                        }
                    } catch (e: Exception) {
                    }
                }
            ) {
                Text(">", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (activeLessons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!hasAnyLessons) {
                    Text(
                        text = "На выбранную дату занятий нет",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (activeLessonNumbers.isEmpty()) {
                    Text(
                        text = "Все пары выключены в настройках",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Активных занятий нет",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Проверьте заполнение предметов в шаблонах",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeLessons) { lesson ->
                    LessonCard(
                        lesson = lesson,
                        showReplacementDemo = false
                    )
                }
            }
        }
    }
}

// панелька с неделей
@Composable
fun WeekSelector(
    weekDates: List<WeekDate>,
    currentDate: String,
    onDateSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            weekDates.forEach { weekDate ->
                WeekDayItem(
                    weekDate = weekDate,
                    isSelected = weekDate.dateString == currentDate,
                    onClick = { onDateSelected(weekDate.dateString) }
                )
            }
        }
    }
}

// день в календаре
@Composable
fun WeekDayItem(
    weekDate: WeekDate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(36.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = weekDate.dayOfMonth,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(4.dp))

        if (weekDate.isToday) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

// макеты пар
@Composable
fun LessonCard(
    lesson: ScheduleEntity,
    showReplacementDemo: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = lesson.lessonNumber.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${lesson.startTime}\n${lesson.endTime}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Кабинет: ${lesson.room}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Учитель: ${lesson.teacher}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// экран заданий
@Composable
fun TasksScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // привязка К ЭлЖур API
    LaunchedEffect(Unit) {
        // сейчас этот вызов ничего не делает
        EljurRemoteDataSource.refreshHomeworkForDate(db, todayString())
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Актуальные", "Архив")

    val activeTasks by db.taskDao().getActiveTasks().collectAsState(initial = emptyList())
    val archivedTasks by db.taskDao().getArchivedTasks().collectAsState(initial = emptyList())

    val attachmentsDao = db.taskAttachmentDao()
    val allAttachments by attachmentsDao.getAll().collectAsState(initial = emptyList())

    val tasksToShow = if (selectedTab == 0) activeTasks else archivedTasks
    val isArchiveTab = selectedTab == 1

    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    var taskIdForPicker by rememberSaveable { mutableStateOf<Long?>(null) }

    var dialogTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialogTaskTitle by rememberSaveable { mutableStateOf("") }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val taskId = taskIdForPicker
        if (uri != null && taskId != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = getFileNameFromUri(context, uri) ?: "Файл"
            scope.launch {
                attachmentsDao.insert(
                    TaskAttachmentEntity(
                        taskId = taskId,
                        uri = uri.toString(),
                        name = name
                    )
                )
            }
        }
    }
    if (showAddDialog) {
        AddTaskDialog(
            onAdd = { subject, title, description, dueDate ->
                scope.launch {
                    db.taskDao().insert(
                        TaskEntity(
                            subject = subject.trim(),
                            title = title.trim(),
                            description = description.trim(),
                            dueDate = dueDate.trim().ifBlank { todayString() },
                            isArchived = false
                        )
                    )
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
    if (dialogTaskId != null) {
        val currentTaskId = dialogTaskId!!
        val taskAttachments = allAttachments.filter { it.taskId == currentTaskId }

        TaskAttachmentsDialog(
            title = dialogTaskTitle,
            attachments = taskAttachments,
            onOpenAttachment = { att ->
                val uri = att.uri.toUri()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteAttachment = { att ->
                scope.launch {
                    attachmentsDao.delete(att)
                }
            },
            onDismiss = {
                dialogTaskId = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить задачу"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasksToShow) { task ->
                val taskAttachments = allAttachments.filter { it.taskId == task.id }
                val attachmentsCount = taskAttachments.size

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    if (!isArchiveTab) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.isArchived,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                db.taskDao().insert(
                                                    task.copy(isArchived = checked)
                                                )
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${task.subject}: ${task.title}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (task.isArchived) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("Выполнено") }
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                // Удаляем вложения задачи
                                                attachmentsDao.deleteForTask(task.id)
                                                db.taskDao().delete(task)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить задачу"
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(task.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Срок: ${task.dueDate}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        dialogTaskId = task.id
                                        dialogTaskTitle = "${task.subject}: ${task.title}"
                                    }
                                ) {
                                    Text(
                                        text = if (attachmentsCount == 0)
                                            "Файлы: нет"
                                        else
                                            "Файлы: $attachmentsCount"
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        taskIdForPicker = task.id
                                        pickFileLauncher.launch(arrayOf("*/*"))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Прикрепить файл"
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isArchived,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        db.taskDao().insert(
                                            task.copy(isArchived = checked)
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${task.subject}: ${task.title}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Срок: ${task.dueDate}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    onClick = {
                                        dialogTaskId = task.id
                                        dialogTaskTitle = "${task.subject}: ${task.title}"
                                    }
                                ) {
                                    val count = allAttachments.count { it.taskId == task.id }
                                    Text(
                                        text = if (count == 0)
                                            "Файлы: нет"
                                        else
                                            "Файлы: $count"
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        attachmentsDao.deleteForTask(task.id)
                                        db.taskDao().delete(task)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить задачу"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// существующее дз
@Composable
fun TaskAttachmentsDialog(
    title: String,
    attachments: List<TaskAttachmentEntity>,
    onOpenAttachment: (TaskAttachmentEntity) -> Unit,
    onDeleteAttachment: (TaskAttachmentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = { Text("Файлы: $title") },
        text = {
            if (attachments.isEmpty()) {
                Text("К этому заданию пока нет прикреплённых файлов.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { att ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(att.name ?: "Файл", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row {
                                TextButton(onClick = { onOpenAttachment(att) }) {
                                    Text("Открыть")
                                }
                                IconButton(onClick = { onDeleteAttachment(att) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить файл"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// создание дз
@Composable
fun AddTaskDialog(
    onAdd: (subject: String, title: String, description: String, dueDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(todayString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (subject.isNotBlank() && title.isNotBlank()) {
                        onAdd(subject, title, description, dueDate)
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text("Новая задача") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Предмет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Срок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

//Экран аттестаций
@Composable
fun AttestationsScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items by db.attestationDao().getAll().collectAsState(initial = emptyList())
    val attachmentsDao = db.attestationAttachmentDao()
    val allAttachments by attachmentsDao.getAll().collectAsState(initial = emptyList())

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var attestationIdForPicker by rememberSaveable { mutableStateOf<Long?>(null) }

    var dialogAttestationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialogAttestationTitle by rememberSaveable { mutableStateOf("") }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val attestationId = attestationIdForPicker
        if (uri != null && attestationId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = getFileNameFromUri(context, uri) ?: "Файл"
            scope.launch {
                attachmentsDao.insert(
                    AttestationAttachmentEntity(
                        attestationId = attestationId,
                        uri = uri.toString(),
                        name = name
                    )
                )
            }
        }
    }

    if (showAddDialog) {
        AddAttestationDialog(
            onAdd = { subject, type, date, room, time, description ->
                scope.launch {
                    db.attestationDao().insert(
                        AttestationEntity(
                            subject = subject.trim(),
                            type = type.trim(),
                            date = date.trim().ifBlank { todayString() },
                            room = room.trim(),
                            time = time.trim(),
                            description = description.trim()
                        )
                    )
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (dialogAttestationId != null) {
        val currentAttestationId = dialogAttestationId!!
        val attestationAttachments = allAttachments.filter { it.attestationId == currentAttestationId }

        AttestationAttachmentsDialog(
            title = dialogAttestationTitle,
            attachments = attestationAttachments,
            onOpenAttachment = { att ->
                val uri = att.uri.toUri()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteAttachment = { att ->
                scope.launch {
                    attachmentsDao.delete(att)
                }
            },
            onDismiss = {
                dialogAttestationId = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Аттестационные мероприятия",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить мероприятие"
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { att ->
                val attestationAttachments = allAttachments.filter { it.attestationId == att.id }
                val attachmentsCount = attestationAttachments.size

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${att.subject} — ${att.type}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("Дата: ${att.date}", style = MaterialTheme.typography.bodySmall)
                                Text("Кабинет: ${att.room}", style = MaterialTheme.typography.bodySmall)
                                Text("Время: ${att.time}", style = MaterialTheme.typography.bodySmall)
                                if (att.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        att.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        // Удаляем вложения аттестации
                                        attachmentsDao.deleteForAttestation(att.id)
                                        db.attestationDao().delete(att)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить мероприятие"
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    dialogAttestationId = att.id
                                    dialogAttestationTitle = "${att.subject}: ${att.type}"
                                }
                            ) {
                                Text(
                                    text = if (attachmentsCount == 0)
                                        "Файлы: нет"
                                    else
                                        "Файлы: $attachmentsCount"
                                )
                            }

                            IconButton(
                                onClick = {
                                    attestationIdForPicker = att.id
                                    pickFileLauncher.launch(arrayOf("*/*"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Прикрепить файл"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// существующие аттестации
@Composable
fun AttestationAttachmentsDialog(
    title: String,
    attachments: List<AttestationAttachmentEntity>,
    onOpenAttachment: (AttestationAttachmentEntity) -> Unit,
    onDeleteAttachment: (AttestationAttachmentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = { Text("Файлы: $title") },
        text = {
            if (attachments.isEmpty()) {
                Text("К этому мероприятию пока нет прикреплённых файлов.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments) { att ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    att.name ?: "Файл",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Row {
                                TextButton(onClick = { onOpenAttachment(att) }) {
                                    Text("Открыть")
                                }
                                IconButton(onClick = { onDeleteAttachment(att) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить файл"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// создание аттестации
@Composable
fun AddAttestationDialog(
    onAdd: (
        subject: String,
        type: String,
        date: String,
        room: String,
        time: String,
        description: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayString()) }
    var room by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (subject.isNotBlank() && type.isNotBlank()) {
                        onAdd(subject, type, date, room, time, description)
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text("Новое мероприятие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Предмет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Тип (контрольная, зачёт...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Дата (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Кабинет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Время (например, 10:00–10:45)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                )
            }
        }
    )
}

//экран сообщений
@Composable
fun MessagesScreen(
    db: AppDatabase,
    openTelegramTab: Boolean = false
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(if (openTelegramTab) 1 else 0)
    }
    val tabs = listOf("ЭлЖур", "ТГ", "Заметки")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (selectedTab) {
            0 -> EljurMessagesTab(db)
            1 -> TelegramMessagesTab()
            2 -> NotesTab(db)
        }
    }
}

// таб эж
@Composable
fun EljurMessagesTab(db: AppDatabase) {
    val messages by db.eljurMessageDao().getAll().collectAsState(initial = emptyList())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(msg.subject, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "От: ${msg.fromName} → Кому: ${msg.toName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Дата: ${msg.date}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// таб тг
@Composable
fun TelegramMessagesTab() {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isAuthorized by remember { mutableStateOf(TelegramService.isAuthorized()) }
    var authStage by remember { mutableStateOf(TelegramService.getAuthStage()) }
    var savedMessages by remember { mutableStateOf<List<SavedMessage>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val currentAuth = TelegramService.isAuthorized()
            if (currentAuth != isAuthorized) {
                isAuthorized = currentAuth
            }
            val stage = TelegramService.getAuthStage()
            if (stage != authStage) {
                authStage = stage
            }
            delay(1000)
        }
    }

    LaunchedEffect(isAuthorized) {
        if (isAuthorized) {
            TelegramService.loadSavedMessages { msgs ->
                savedMessages = msgs
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isAuthorized) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Номер телефона") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль Telegram") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                                )
                            }
                        }
                    )

                    Button(
                        onClick = {
                            TelegramService.setPendingPassword(password.trim())
                            TelegramService.sendPhoneNumber(phone.trim())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отправить")
                    }

                    val needCodeInput =
                        authStage == TelegramService.AuthStage.WAIT_CODE ||
                                authStage == TelegramService.AuthStage.WAIT_PASSWORD

                    if (needCodeInput) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Код из Telegram") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                TelegramService.sendAuthCode(code.trim())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отправить код")
                        }
                    }

                    Text(
                        text = if (isAuthorized) "Статус: авторизован"
                        else "Статус: не авторизован",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        TelegramService.loadSavedMessages { msgs ->
                            savedMessages = msgs
                        }
                    }
                ) {
                    Text("Обновить")
                }
            }

            Text(
                text = "Статус: авторизован",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedMessages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)

                        if (msg.hasPhoto || msg.hasVideo || msg.hasDocument) {
                            Spacer(Modifier.height(4.dp))

                            val fileInfo = buildString {
                                if (msg.hasPhoto) append("📷 Фото")
                                if (msg.hasVideo) {
                                    if (isNotEmpty()) append(", ")
                                    append("🎥 Видео")
                                }
                                if (msg.hasDocument) {
                                    if (isNotEmpty()) append(", ")
                                    append("📄 Документ")
                                }

                                if (msg.fileSize > 0) {
                                    append(" (")
                                    append(formatFileSize(msg.fileSize))
                                    append(")")
                                }
                            }

                            Text(
                                text = fileInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// таб заметок
@Composable
fun NotesTab(db: AppDatabase) {
    val notes by db.noteDao().getAll().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Заметки", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Заголовок") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Текст заметки") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
        )

        Button(
            onClick = {
                if (title.isNotBlank() || content.isNotBlank()) {
                    scope.launch {
                        db.noteDao().insert(
                            NoteEntity(
                                title = title.ifBlank { "Без названия" },
                                content = content
                            )
                        )
                        title = ""
                        content = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить заметку")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notes) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(note.content, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    db.noteDao().delete(note)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить заметку"
                            )
                        }
                    }
                }
            }
        }
    }
}

//экран настроек
@Composable
fun SettingsScreen(
    db: AppDatabase,
    isDarkTheme: Boolean,
    isEljurLoggedIn: Boolean,
    onToggleTheme: () -> Unit,
    onLogoutApp: () -> Unit,
    onNavigateToTelegramAuth: () -> Unit,
    onShowEljurLogin: () -> Unit,
    onEljurLogout: () -> Unit,
    onEditSchedule: () -> Unit
) {
    val context = LocalContext.current
    val telegramAuthorized = remember { mutableStateOf(TelegramService.isAuthorized()) }
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var channelUsername by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_TELEGRAM_CHANNEL, "") ?: "")
    }
    var showChannelDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            telegramAuthorized.value = TelegramService.isAuthorized()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        if (isEljurLoggedIn) "ЭлЖур подключен"
                        else "Авторизация ЭлЖур"
                    )
                },
                supportingContent = {
                    Text(
                        if (isEljurLoggedIn)
                            "Ваш аккаунт ЭлЖур привязан"
                        else
                            "Войдите в аккаунт ЭлЖур для загрузки данных"
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    if (isEljurLoggedIn) {
                        TextButton(onClick = onEljurLogout) {
                            Text("Отвязать")
                        }
                    } else {
                        TextButton(onClick = onShowEljurLogin) {
                            Text("Войти")
                        }
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        if (channelUsername.isNotEmpty()) "Канал: $channelUsername"
                        else "Читаемый канал"
                    )
                },
                supportingContent = {
                    Text("Укажите username канала в телеграмме (без @) или оставьте пустым для чтения избранного")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showChannelDialog = true }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        if (telegramAuthorized.value) "Telegram подключён"
                        else "Telegram не подключён"
                    )
                },
                supportingContent = {
                    Text(
                        if (telegramAuthorized.value)
                            "Связка с Telegram используется для чтения избранных сообщений."
                        else
                            "Нажмите чтобы подключить Telegram"
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    if (telegramAuthorized.value) {
                        TextButton(onClick = { TelegramService.logout() }) {
                            Text("Отвязать")
                        }
                    } else {
                        TextButton(onClick = { onNavigateToTelegramAuth() }) {
                            Text("Привязать")
                        }
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = { Text("Тёмная тема") },
                supportingContent = { Text("Переключить светлую/тёмную тему") },
                leadingContent = {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.WbSunny,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() }
                    )
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = { Text("Редактировать расписание") },
                supportingContent = {
                    Text("Настроить расписание звонков и занятий")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.EditCalendar,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onEditSchedule() }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = { Text("Очистить расписание") },
                supportingContent = {
                    Text("Удалить записанное расписание")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch {
                       val allLessons = db.scheduleDao().getAllGenerated().firstOrNull() ?: emptyList()
                        allLessons.forEach { lesson ->
                            if (lesson.date.isNotBlank()) {
                                db.scheduleDao().clearForDate(lesson.date)
                            }
                        }

                        val evenTemplate = db.scheduleDao().getTemplateByParity("even").firstOrNull() ?: emptyList()
                        val oddTemplate = db.scheduleDao().getTemplateByParity("odd").firstOrNull() ?: emptyList()

                        if (evenTemplate.isEmpty()) {
                            db.scheduleDao().clearTemplateByParity("even")
                        }
                        if (oddTemplate.isEmpty()) {
                            db.scheduleDao().clearTemplateByParity("odd")
                        }

                        Toast.makeText(context, "Сгенерированные занятия очищены", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = { Text("Выйти из приложения") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onLogoutApp() }
            )
        }
    }

    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = { Text("Настройка канала Telegram") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите username публичного канала (без @) или оставьте пустым для чтения избранного")
                    OutlinedTextField(
                        value = channelUsername,
                        onValueChange = { channelUsername = it },
                        label = { Text("Username канала") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Пример: news_channel (без @)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit {
                            putString(KEY_TELEGRAM_CHANNEL, channelUsername)
                        }
                        TelegramService.setCustomChannel(channelUsername)
                        showChannelDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChannelDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

// конструктор расписаний
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    db: AppDatabase
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Звонки", "Четная неделя", "Нечетная неделя")

    var hasUnsavedWeekChanges by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }

            Text(
                "Редактирование расписания",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (selectedTab > 0 && hasUnsavedWeekChanges) {
                IconButton(
                    onClick = {
                        hasUnsavedWeekChanges = false
                        onSave()
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Сохранить")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> BellScheduleTab(db = db)
            1 -> WeekScheduleTab(
                parity = "even",
                db = db,
                onHasChanges = { hasChanges -> hasUnsavedWeekChanges = hasChanges }
            )
            2 -> WeekScheduleTab(
                parity = "odd",
                db = db,
                onHasChanges = { hasChanges -> hasUnsavedWeekChanges = hasChanges }
            )
        }
    }
}

// таб расписание звонков
@Composable
fun BellScheduleTab(
    db: AppDatabase
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bellScheduleFlow = db.bellScheduleDao().getAll()
    val bellSchedule by bellScheduleFlow.collectAsState(initial = emptyList())

    val lessons = rememberSaveable {
        mutableStateListOf<BellScheduleEntity>()
    }

    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(bellSchedule) {
        if (bellSchedule.isNotEmpty()) {
            lessons.clear()
            lessons.addAll(bellSchedule.sortedBy { it.lessonNumber })
        } else {
            val defaultLessons = (1..8).map { number ->
                BellScheduleEntity(
                    lessonNumber = number,
                    startTime = String.format("%02d:00", 8 + (number - 1) * 2),
                    endTime = String.format("%02d:50", 8 + (number - 1) * 2),
                    isActive = number <= 6
                )
            }
            lessons.clear()
            lessons.addAll(defaultLessons)
        }
    }

    // Функция для автосохранения изменений с задержкой
    fun saveChangesAuto(updatedLesson: BellScheduleEntity?) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500) //
            val sortedLessons = lessons.sortedBy { it.lessonNumber }
            db.bellScheduleDao().clearAll()
            db.bellScheduleDao().insertAll(sortedLessons)

            updateTemplatesWithNewTimes(db, sortedLessons)
            regenerateFullSchedule(db)
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (lessonNumber in 1..8) {
            item {
                val lesson = lessons.find { it.lessonNumber == lessonNumber }
                    ?: BellScheduleEntity(
                        lessonNumber = lessonNumber,
                        startTime = String.format("%02d:00", 8 + (lessonNumber - 1) * 2),
                        endTime = String.format("%02d:50", 8 + (lessonNumber - 1) * 2),
                        isActive = lessonNumber <= 6
                    ).also { newLesson ->
                        lessons.add(newLesson)
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        // Можно сделать визуальную разницу для неактивных
                        containerColor = if (lesson.isActive)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Пара $lessonNumber",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = lesson.isActive,
                                onCheckedChange = { checked ->
                                    val index = lessons.indexOf(lesson)
                                    if (index >= 0) {
                                        val updatedLesson = lesson.copy(isActive = checked)
                                        lessons[index] = updatedLesson
                                        saveChangesAuto(updatedLesson)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Начало", style = MaterialTheme.typography.bodySmall)
                                TimeInputField(
                                    time = lesson.startTime,
                                    onTimeChange = { newTime ->
                                        val index = lessons.indexOf(lesson)
                                        if (index >= 0) {
                                            val updatedLesson = lesson.copy(startTime = newTime)
                                            lessons[index] = updatedLesson
                                            saveChangesAuto(updatedLesson)
                                        }
                                    }
                                )
                            }

                            Text("—", style = MaterialTheme.typography.titleMedium)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Конец", style = MaterialTheme.typography.bodySmall)
                                TimeInputField(
                                    time = lesson.endTime,
                                    onTimeChange = { newTime ->
                                        val index = lessons.indexOf(lesson)
                                        if (index >= 0) {
                                            val updatedLesson = lesson.copy(endTime = newTime)
                                            lessons[index] = updatedLesson
                                            saveChangesAuto(updatedLesson)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(debounceJob) {
        if (debounceJob != null) {
            Toast.makeText(context, "Расписание звонков сохранено", Toast.LENGTH_SHORT).show()
        }
    }
}

// часы для расписания звонков
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputField(time: String, onTimeChange: (String) -> Unit) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var selectedHour by rememberSaveable { mutableIntStateOf(0) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(time) {
        val parts = time.split(":")
        if (parts.size == 2) {
            selectedHour = parts[0].toIntOrNull() ?: 8
            selectedMinute = parts[1].toIntOrNull() ?: 0
        }
    }

    TextButton(
        onClick = { showTimePicker = true }
    ) {
        Text(time, style = MaterialTheme.typography.titleMedium)
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )

        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        onTimeChange(newTime)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Отмена")
                }
            },
            title = { Text("Выберите время") }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

// таб заполненения расписания
@Composable
fun WeekScheduleTab(
    parity: String,
    db: AppDatabase,
    onHasChanges: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bellSchedule by db.bellScheduleDao().getAll().collectAsState(initial = emptyList())
    val existingTemplate by db.scheduleDao().getTemplateByParity(parity).collectAsState(emptyList())

    val templateMap = remember(existingTemplate) {
        mutableStateMapOf<String, MutableList<ScheduleEntity>>()
    }

    LaunchedEffect(existingTemplate, bellSchedule) {
        templateMap.clear()
        val daysOfWeek = listOf(
            "Понедельник" to 1,
            "Вторник" to 2,
            "Среда" to 3,
            "Четверг" to 4,
            "Пятница" to 5,
            "Суббота" to 6
        )

        daysOfWeek.forEach { (day, dayNumber) ->
            val allDayLessons = existingTemplate.filter { it.dayOfWeek == dayNumber }
            val dayLessons = mutableListOf<ScheduleEntity>()

            allDayLessons.forEach { existingLesson ->
                dayLessons.add(existingLesson)
            }

            for (lessonNum in 1..8) {
                if (!dayLessons.any { it.lessonNumber == lessonNum }) {
                    val bell = bellSchedule.find { it.lessonNumber == lessonNum }

                    dayLessons.add(
                        ScheduleEntity(
                            date = "",
                            lessonNumber = lessonNum,
                            subject = "",
                            teacher = "",
                            room = "",
                            startTime = bell?.startTime ?: "08:00",
                            endTime = bell?.endTime ?: "09:30",
                            weekParity = parity,
                            dayOfWeek = dayNumber,
                            isReplacement = false
                        )
                    )
                }
            }

            templateMap[day] = dayLessons.sortedBy { it.lessonNumber }.toMutableList()
        }
    }

    val daysOfWeek = listOf(
        "Понедельник" to 1,
        "Вторник" to 2,
        "Среда" to 3,
        "Четверг" to 4,
        "Пятница" to 5,
        "Суббота" to 6
    )

    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }

    fun saveChangesManual() {
        scope.launch {
            db.scheduleDao().clearTemplateByParity(parity)

            val allLessons = templateMap.flatMap { (_, lessons) -> lessons }
                .filter { lesson -> true }

            if (allLessons.isNotEmpty()) {
                db.scheduleDao().insertAll(allLessons)
            }

            regenerateFullSchedule(db)

            hasUnsavedChanges = false
            Toast.makeText(context, "Расписание сохранено", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasUnsavedChanges) {
            Button(
                onClick = { saveChangesManual() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Сохранить изменения")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(daysOfWeek) { (dayName, dayNumber) ->
                val dayLessons = templateMap[dayName] ?: mutableListOf()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            dayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        dayLessons
                            .filter { lesson ->
                                val bell = bellSchedule.find { it.lessonNumber == lesson.lessonNumber }
                                bell?.isActive == true
                            }
                            .forEach { lesson ->
                                LessonInputRow(
                                    dayNumber = dayNumber,
                                    lessonNumber = lesson.lessonNumber,
                                    timeRange = "${lesson.startTime}-${lesson.endTime}",
                                    initialSubject = lesson.subject,
                                    initialTeacher = lesson.teacher,
                                    initialRoom = lesson.room,
                                    onLessonChange = { subject, teacher, room ->
                                        val newDayLessons = dayLessons.toMutableList()
                                        val index = newDayLessons.indexOfFirst { it.lessonNumber == lesson.lessonNumber }
                                        val newLesson = lesson.copy(
                                            subject = subject,
                                            teacher = teacher,
                                            room = room
                                        )

                                        if (index >= 0) {
                                            newDayLessons[index] = newLesson
                                        }

                                        templateMap[dayName] = newDayLessons
                                        hasUnsavedChanges = true
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}

//редакция 1 пары
@Composable
fun LessonInputRow(
    dayNumber: Int,
    lessonNumber: Int,
    timeRange: String,
    initialSubject: String,
    initialTeacher: String,
    initialRoom: String,
    onLessonChange: (subject: String, teacher: String, room: String) -> Unit
) {
    var subject by rememberSaveable { mutableStateOf(initialSubject) }
    var teacher by rememberSaveable { mutableStateOf(initialTeacher) }
    var room by rememberSaveable { mutableStateOf(initialRoom) }

    LaunchedEffect(subject, teacher, room) {
        if (subject != initialSubject || teacher != initialTeacher || room != initialRoom) {
            onLessonChange(subject, teacher, room)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "$lessonNumber. $timeRange",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Предмет") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("Преподаватель") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text("Кабинет") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// экран авторизация элжура
@Composable
fun EljurLoginDialog(
    onLogin: (login: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    errorText = null
                    val trimmedLogin = login.trim()
                    val trimmedPassword = password.trim()

                    if (trimmedLogin.isBlank() || trimmedPassword.isBlank()) {
                        errorText = "Введите логин и пароль"
                        return@Button
                    }

                    if (EljurConfig.USE_ELJUR_API) {
                        // Режим: авторизация через ЭлЖур API
                        scope.launch {
                            isLoading = true
                            val ok = EljurAuthRemoteDataSource.login(
                                trimmedLogin,
                                trimmedPassword
                            )
                            isLoading = false
                            if (ok) {
                                onLogin(trimmedLogin, trimmedPassword)
                            } else {
                                errorText = "Не удалось авторизоваться через ЭлЖур"
                            }
                        }
                    } else {
                        // Режим: локальный логин (демо)
                        onLogin(trimmedLogin, trimmedPassword)
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading && EljurConfig.USE_ELJUR_API) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Войти")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text("Вход в ЭлЖур") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин ЭлЖур") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль ЭлЖур") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    }
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (EljurConfig.USE_ELJUR_API) {
                    Text(
                        text = "Используется авторизация через ЭлЖур API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "Сейчас используется локальная авторизация (демо-режим)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
    )
}

private suspend fun regenerateFullSchedule(db: AppDatabase) {
    val bellSchedule = db.bellScheduleDao().getAll().firstOrNull() ?: emptyList()
    val evenTemplate = db.scheduleDao().getTemplateByParity("even").firstOrNull() ?: emptyList()
    val oddTemplate = db.scheduleDao().getTemplateByParity("odd").firstOrNull() ?: emptyList()

    if (evenTemplate.isEmpty() && oddTemplate.isEmpty()) {
        return
    }

    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Генерируем расписание на 8 недель вперед (2 месяца)
    for (weekOffset in -4..4) {
        calendar.time = Date()
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        // Устанавливаем на понедельник этой недели
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        // Проходим по всем дням недели
        for (dayOffset in 0..5) { // Пн-Сб
            val currentCalendar = calendar.clone() as Calendar
            currentCalendar.add(Calendar.DAY_OF_YEAR, dayOffset)

            val date = sdf.format(currentCalendar.time)

            val weekParity = if (currentCalendar.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"
            val dayOfWeek = dayOffset + 1 // 1-Пн, 2-Вт, ..., 6-Сб

            val template = if (weekParity == "even") evenTemplate else oddTemplate
            val dayLessons = template.filter { it.dayOfWeek == dayOfWeek }

            // Сначала удаляем старые занятия на этот день
            db.scheduleDao().clearForDate(date)

            // Добавляем только активные занятия
            dayLessons.forEach { templateLesson ->
                // Проверяем, активна ли эта пара в расписании звонков
                val bell = bellSchedule.find {
                    it.lessonNumber == templateLesson.lessonNumber && it.isActive
                }

                // Урок должен быть активным И иметь заполненный предмет
                if (bell != null && templateLesson.subject.isNotBlank()) {
                    val lesson = ScheduleEntity(
                        date = date,
                        lessonNumber = templateLesson.lessonNumber,
                        subject = templateLesson.subject,
                        teacher = templateLesson.teacher,
                        room = templateLesson.room,
                        startTime = bell.startTime,
                        endTime = bell.endTime,
                        weekParity = weekParity,
                        dayOfWeek = dayOfWeek,
                        isReplacement = false
                    )
                    db.scheduleDao().insert(lesson)
                }
            }
        }
    }
}

private suspend fun updateTemplatesWithNewTimes(db: AppDatabase, bellSchedule: List<BellScheduleEntity>) {
    // Обновляем время в шаблонах для четной и нечетной недель
    val parities = listOf("even", "odd")

    for (parity in parities) {
        val templates = db.scheduleDao().getTemplateByParity(parity).firstOrNull() ?: continue

        // Создаем новые шаблоны с обновленным временем
        val updatedTemplates = templates.map { template ->
            val bell = bellSchedule.find { it.lessonNumber == template.lessonNumber }
            if (bell != null) {
                // Сохраняем урок, даже если пара неактивна
                template.copy(
                    startTime = bell.startTime,
                    endTime = bell.endTime
                )
            } else {
                template
            }
        }

        // Сохраняем обновленные шаблоны
        db.scheduleDao().clearTemplateByParity(parity)
        if (updatedTemplates.isNotEmpty()) {
            db.scheduleDao().insertAll(updatedTemplates)
        }
    }
}

private fun todayString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

private fun formatDateHuman(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr
        val out = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        out.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun dateStringToMillis(dateStr: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

private fun millisToDateString(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size Б"
        size < 1024 * 1024 -> "${size / 1024} КБ"
        else -> "${size / (1024 * 1024)} МБ"
    }
}

data class WeekDate(
    val dateString: String,
    val dayOfWeekShort: String,
    val dayOfMonth: String,
    val isToday: Boolean = false
)

private fun getWeekDates(selectedDate: String): List<WeekDate> {
    val dates = mutableListOf<WeekDate>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val daySdf = SimpleDateFormat("dd", Locale.getDefault())
    val weekDaySdf = SimpleDateFormat("E", Locale.getDefault())

    try {
        val baseDate = sdf.parse(selectedDate)
        if (baseDate != null) {
            val calendar = Calendar.getInstance()
            calendar.time = baseDate

            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

            for (i in 0 until 7) {
                val currentDate = calendar.time
                val dateStr = sdf.format(currentDate)
                val dayOfMonth = daySdf.format(currentDate)
                val dayOfWeek = weekDaySdf.format(currentDate)

                val isToday = dateStr == todayString()

                dates.add(
                    WeekDate(
                        dateString = dateStr,
                        dayOfWeekShort = dayOfWeek,
                        dayOfMonth = dayOfMonth,
                        isToday = isToday
                    )
                )

                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return dates
}

private suspend fun generateScheduleFromTemplates(db: AppDatabase) {
    val bellSchedule = db.bellScheduleDao().getAll().firstOrNull() ?: emptyList()
    val evenTemplate = db.scheduleDao().getTemplateByParity("even").firstOrNull() ?: emptyList()
    val oddTemplate = db.scheduleDao().getTemplateByParity("odd").firstOrNull() ?: emptyList()

    if (evenTemplate.isEmpty() && oddTemplate.isEmpty()) {
        return
    }

    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val allDates = mutableListOf<String>()

    for (weekOffset in 0..12) {
        calendar.time = Date()
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        for (dayOffset in 0..12) {
            val currentCalendar = calendar.clone() as Calendar
            currentCalendar.add(Calendar.DAY_OF_YEAR, dayOffset)

            val date = sdf.format(currentCalendar.time)
            allDates.add(date)

            val weekParity = if (currentCalendar.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"
            val dayOfWeek = dayOffset + 1

            val template = if (weekParity == "even") evenTemplate else oddTemplate
            val dayLessons = template.filter { it.dayOfWeek == dayOfWeek }

            db.scheduleDao().clearForDate(date)

            dayLessons.forEach { templateLesson ->
                val bell = bellSchedule.find { it.lessonNumber == templateLesson.lessonNumber && it.isActive }
                if (bell != null) {
                    val lesson = ScheduleEntity(
                        date = date,
                        lessonNumber = templateLesson.lessonNumber,
                        subject = templateLesson.subject,
                        teacher = templateLesson.teacher,
                        room = templateLesson.room,
                        startTime = bell.startTime,
                        endTime = bell.endTime,
                        weekParity = weekParity,
                        dayOfWeek = dayOfWeek,
                        isReplacement = false
                    )
                    db.scheduleDao().insert(lesson)
                }
            }
        }
    }
}