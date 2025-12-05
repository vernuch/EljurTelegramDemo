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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

private const val PREFS_NAME = "telegram_prefs"
private const val KEY_TELEGRAM_CHANNEL = "telegram_channel"

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase
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
        val prefs = getSharedPreferences("eljur_prefs", Context.MODE_PRIVATE)
        val alreadyLoggedIn = prefs.getBoolean("logged_in", false)
        lifecycleScope.launch {
            DemoDataInitializer.ensureDemoData(db)
        }
        setContent {
            EljurApp(
                db = db,
                initialLoggedIn = alreadyLoggedIn
            )
        }
    }
}

private enum class BottomTab(val label: String) {
    Schedule("Расписание"),
    Tasks("Задания"),
    Attestation("Аттестации"),
    Messages("Сообщения")
}

@Composable
fun EljurApp(
    db: AppDatabase,
    initialLoggedIn: Boolean
) {
    val context = LocalContext.current
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    var isLoggedIn by rememberSaveable { mutableStateOf(initialLoggedIn) }

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
                if (!isLoggedIn) {
                    LoginScreen(
                        onLogin = { _, _ ->
                            val prefs = context.getSharedPreferences("eljur_prefs", Context.MODE_PRIVATE)
                            prefs.edit { putBoolean("logged_in", true) }
                            isLoggedIn = true
                        }
                    )
                } else {
                    MainScaffold(
                        db = db,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                        onLogout = {
                            val prefs = context.getSharedPreferences("eljur_prefs", Context.MODE_PRIVATE)
                            prefs.edit { putBoolean("logged_in", false) }
                            isLoggedIn = false
                        }
                    )
                }
            }
        }
    }
}

//экран логина
@Composable
fun LoginScreen(
    onLogin: (login: String, password: String) -> Unit
) {
    val context = LocalContext.current
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler {
        (context as? Activity)?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Электронный дневник",
                    style = MaterialTheme.typography.titleLarge
                )

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
                            // Режим: авторизация через ЭлЖур
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
                            // Режим: локальный логин
                            onLogin(trimmedLogin, trimmedPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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

                if (EljurConfig.USE_ELJUR_API) {
                    Text(
                        text = "Используется авторизация через ЭлЖур API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "Сейчас используется локальная авторизация",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    db: AppDatabase,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Schedule) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openTelegramTab by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        when {
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
                                imageVector = Icons.Default.School,
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
            if (!showSettings) {
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
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onLogoutApp = onLogout,
                    onNavigateToTelegramAuth = {
                        showSettings = false
                        selectedTab = BottomTab.Messages
                        openTelegramTab = true
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

//Экран расписания
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(db: AppDatabase) {
    val today = remember { todayString() }
    val tomorrow = remember { tomorrowString() }

    var currentDate by rememberSaveable { mutableStateOf(today) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    //сейчас ничего не делае пока USE_ELJUR_API = false
    LaunchedEffect(currentDate) {
        EljurRemoteDataSource.refreshScheduleForDate(db, currentDate)
    }

    val lessons by db.scheduleDao()
        .getScheduleForDate(currentDate)
        .collectAsState(initial = emptyList())

    val isTodayOrTomorrow = currentDate == today || currentDate == tomorrow
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

        if (!isTodayOrTomorrow || lessons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "На этот день занятий нет.",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lessons) { lesson ->
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
            }
        }
    }
}

//Экран заданий
@Composable
fun TasksScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Привязка К ЭлЖур API
    LaunchedEffect(Unit) {
        // Сейчас этот вызов ничего не делает
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
    val items by db.attestationDao().getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }

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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
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
                }
            }
        }
    }
}

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

//Экран сообщений
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
                        color = MaterialTheme.colorScheme.onBackground
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
                    }
                }
            }
        }
    }
}

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

//Экран настроек
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogoutApp: () -> Unit,
    onNavigateToTelegramAuth: () -> Unit
) {
    val context = LocalContext.current
    val telegramAuthorized = remember { mutableStateOf(TelegramService.isAuthorized()) }

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
                        if (channelUsername.isNotEmpty()) "Канал: $channelUsername"
                        else "Читать избранное"
                    )
                },
                supportingContent = {
                    Text("Укажите username канала (без @) или оставьте пустым для избранного")
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
                            "Нажмите чтобы подключить Telegram и читать избранные сообщения."
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    // УМНАЯ КНОПКА
                    if (telegramAuthorized.value) {
                        // Если привязан - кнопка "Отвязать"
                        TextButton(onClick = { TelegramService.logout() }) {
                            Text("Отвязать")
                        }
                    } else {
                        // Если не привязан - кнопка "Привязать"
                        TextButton(
                            onClick = {
                                onNavigateToTelegramAuth()
                            }
                        ) {
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
                headlineContent = { Text("Выйти из аккаунта") },
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
                    Text("Введите username канала (без @) или оставьте пустым для чтения избранного")
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

private fun todayString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

private fun tomorrowString(): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(cal.time)
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
