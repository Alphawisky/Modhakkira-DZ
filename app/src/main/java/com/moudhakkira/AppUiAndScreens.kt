package com.moudhakkira

import android.app.Application
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

// ==========================================
// Navigation
// ==========================================

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Students : Screen("students/{groupId}") {
        fun create(groupId: String) = "students/$groupId"
    }
    object StudentDetail : Screen("student/{studentId}") {
        fun create(studentId: String) = "student/$studentId"
    }
    object Grades : Screen("grades/{groupId}") {
        fun create(groupId: String) = "grades/$groupId"
    }
    object Absences : Screen("absences/{groupId}") {
        fun create(groupId: String) = "absences/$groupId"
    }
    object Reports : Screen("reports/{groupId}") {
        fun create(groupId: String) = "reports/$groupId"
    }
    object Settings : Screen("settings")
    object SyncStatus : Screen("sync_status")
}

@Composable
fun AppNavigation(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Students.route) { entry ->
            StudentsScreen(navController, entry.arguments?.getString("groupId").orEmpty())
        }
        composable(Screen.StudentDetail.route) { entry ->
            StudentDetailScreen(navController, entry.arguments?.getString("studentId").orEmpty())
        }
        composable(Screen.Grades.route) { entry ->
            GradesScreen(navController, entry.arguments?.getString("groupId").orEmpty())
        }
        composable(Screen.Absences.route) { entry ->
            AbsencesScreen(navController, entry.arguments?.getString("groupId").orEmpty())
        }
        composable(Screen.Reports.route) { entry ->
            ReportsScreen(navController, entry.arguments?.getString("groupId").orEmpty())
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.SyncStatus.route) { SyncStatusScreen(navController) }
    }
}

// ==========================================
// View models
// ==========================================

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = AppRepository(db)
    val groups = repo.getGroups()

    fun addGroup(name: String, level: String, year: String) {
        viewModelScope.launch { repo.addGroup(name, level, year) }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch { repo.deleteGroup(id) }
    }
}

class StudentsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = AppRepository(db)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getStudents(groupId: String): Flow<List<Student>> = _query.flatMapLatest { query ->
        if (query.isBlank()) repo.getStudents(groupId) else repo.searchStudents(query, groupId)
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun addStudent(firstName: String, lastName: String, registrationNumber: String?, groupId: String, gender: String) {
        viewModelScope.launch {
            repo.addStudent(
                Student(
                    firstName = firstName,
                    lastName = lastName,
                    registrationNumber = registrationNumber,
                    groupId = groupId,
                    gender = gender
                )
            )
        }
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch { repo.deleteStudent(id) }
    }
}

// ==========================================
// Login and home
// ==========================================

@Composable
fun LoginScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { AuthService(context) }
    var email by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFE6F2EC), MaterialTheme.colorScheme.background)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "مذكّرة الأستاذ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "نظّم أفواجك، تابع العلامات والغيابات، واحتفظ بسجلّك الدراسي على هذا الجهاز.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        error = null
                    },
                    label = { Text("البريد الإلكتروني للأستاذ") },
                    supportingText = {
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                        else Text("يُستخدم لتعريف ملفك المحلي فقط؛ لا يوجد تسجيل دخول سحابي حالياً.")
                    },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val cleanEmail = email.trim()
                        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            error = "أدخل بريداً إلكترونياً صحيحاً للمتابعة."
                        } else {
                            auth.loginWithEmail(cleanEmail)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("متابعة إلى المذكرة", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MainViewModel = viewModel()) {
    val groups by vm.groups.collectAsState(initial = emptyList())
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            PageTopBar(
                title = "أفواجي التربوية",
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.SyncStatus.route) }) {
                        Icon(Icons.Default.CloudOff, contentDescription = "حالة المزامنة")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة فوج")
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مساحتك التعليمية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${groups.size} ${if (groups.size == 1) "فوج دراسي" else "أفواج دراسية"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الأفواج", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        groups.size.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Class,
                        title = "ابدأ بتنظيم أقسامك",
                        message = "أضف فوجك الأول لتسجيل التلاميذ والعلامات والغيابات."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.Students.create(group.id)) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Class, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        listOf(group.level, group.academicYear).filter(String::isNotBlank).joinToString(" • "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { groupToDelete = group }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الفوج", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGroupDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, level, year ->
                vm.addGroup(name, level, year)
                showAddDialog = false
            }
        )
    }
    groupToDelete?.let { group ->
        ConfirmActionDialog(
            title = "حذف الفوج؟",
            message = "سيُخفى الفوج وتلاميذه وعلاماتهم وغياباتهم من القوائم. لا يمكن التراجع عن ذلك من داخل التطبيق.",
            confirmLabel = "حذف الفوج",
            onDismiss = { groupToDelete = null },
            onConfirm = {
                vm.deleteGroup(group.id)
                groupToDelete = null
            }
        )
    }
}

// ==========================================
// Students and student details
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(navController: NavController, groupId: String, vm: StudentsViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val studentFlow = remember(vm, groupId) { vm.getStudents(groupId) }
    val students by studentFlow.collectAsState(initial = emptyList())
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }

    Scaffold(
        topBar = {
            PageTopBar(
                title = "تلاميذ الفوج",
                onBack = { navController.navigateUp() },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Grades.create(groupId)) }) {
                        Icon(Icons.Default.EditNote, contentDescription = "العلامات")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Absences.create(groupId)) }) {
                        Icon(Icons.Default.EventBusy, contentDescription = "الغيابات")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Reports.create(groupId)) }) {
                        Icon(Icons.Default.Assessment, contentDescription = "المداولات")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة تلميذ")
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("ابحث بالاسم أو اللقب") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Face,
                        title = if (query.isBlank()) "لا يوجد تلاميذ بعد" else "لم نعثر على نتائج",
                        message = if (query.isBlank()) "أضف أسماء التلاميذ لهذا الفوج لتبدأ المتابعة." else "جرّب كتابة الاسم أو اللقب بطريقة أخرى."
                    )
                }
            } else {
                Text(
                    "${students.size} تلميذ",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(students, key = { it.id }) { student ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.StudentDetail.create(student.id)) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (student.gender == "F") Icons.Default.Face3 else Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${student.lastName} ${student.firstName}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    student.registrationNumber?.let {
                                        Text("رقم التسجيل: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { studentToDelete = student }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف التلميذ", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStudentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { first, last, registration, gender ->
                vm.addStudent(first, last, registration, groupId, gender)
                showAddDialog = false
            }
        )
    }
    studentToDelete?.let { student ->
        ConfirmActionDialog(
            title = "حذف التلميذ؟",
            message = "سيُخفى ${student.firstName} ${student.lastName} مع سجل علاماته وغياباته من هذا الفوج.",
            confirmLabel = "حذف",
            onDismiss = { studentToDelete = null },
            onConfirm = {
                vm.deleteStudent(student.id)
                studentToDelete = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(navController: NavController, studentId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var student by remember(studentId) { mutableStateOf<Student?>(null) }
    var isLoading by remember(studentId) { mutableStateOf(true) }
    val grades by db.gradeDao().getByStudent(studentId).collectAsState(initial = emptyList())
    val absences by db.absenceDao().getByStudent(studentId).collectAsState(initial = emptyList())
    val latestTrimester = grades.maxOfOrNull { it.trimester }
    val average = remember(grades, latestTrimester) {
        latestTrimester?.let { term ->
            AverageCalculator.calculateSubjectAverage(grades.filter { it.trimester == term })
        }
    }

    LaunchedEffect(studentId) {
        isLoading = true
        student = db.studentDao().getById(studentId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            PageTopBar(
                title = student?.let { "${it.lastName} ${it.firstName}" } ?: "بطاقة التلميذ",
                onBack = { navController.navigateUp() }
            )
        }
    ) { contentPadding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            student == null -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Face,
                    title = "تعذّر العثور على التلميذ",
                    message = "ربما حُذف هذا السجل أو لم يعد متاحاً."
                )
            }
            else -> {
                val currentStudent = student!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(shape = RoundedCornerShape(22.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("البيانات الشخصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("الاسم واللقب: ${currentStudent.lastName} ${currentStudent.firstName}")
                                Text("رقم التسجيل: ${currentStudent.registrationNumber ?: "غير مسجل"}")
                                Text("الجنس: ${if (currentStudent.gender == "F") "أنثى" else "ذكر"}")
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailStatCard(
                                modifier = Modifier.weight(1f),
                                label = latestTrimester?.let { "معدل الفصل $it" } ?: "المعدل المسجل",
                                value = average?.let { "${formatNumber(it)} / 20" } ?: "—"
                            )
                            DetailStatCard(
                                modifier = Modifier.weight(1f),
                                label = "الغيابات",
                                value = absences.size.toString()
                            )
                        }
                    }
                    item {
                        Text("سجل العلامات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (grades.isEmpty()) {
                        item { Text("لم تُسجّل علامات لهذا التلميذ بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(grades, key = { it.id }) { grade ->
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(grade.gradeType, fontWeight = FontWeight.SemiBold)
                                        Text("الفصل ${grade.trimester}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "${formatNumber(grade.value)} / ${formatNumber(grade.maxScore)}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (grade.value >= grade.maxScore / 2.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text("سجل الغيابات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (absences.isEmpty()) {
                        item { Text("لا توجد غيابات مسجلة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(absences, key = { it.id }) { absence ->
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(formatDate(absence.date), fontWeight = FontWeight.SemiBold)
                                        absence.reason?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    }
                                    Text(
                                        if (absence.isJustified) "مبرّر" else "غير مبرّر",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (absence.isJustified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Grades and absence tracking
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val repo = remember(db) { AppRepository(db) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    var trimester by rememberSaveable { mutableIntStateOf(1) }
    val grades by repo.getGrades(groupId, trimester).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            PageTopBar(title = "رصد العلامات", onBack = { navController.navigateUp() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = trimester - 1) {
                (1..3).forEach { term ->
                    Tab(
                        selected = trimester == term,
                        onClick = { trimester = term },
                        text = { Text("الفصل $term") }
                    )
                }
            }
            Text(
                "العلامات من 20 — احفظ بعد إدخال الفرض والاختبار.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.EditNote,
                        title = "لا يوجد تلاميذ في هذا الفوج",
                        message = "أضف التلاميذ أولاً لتتمكن من رصد علاماتهم."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(students, key = { it.id }) { student ->
                        val studentGrades = grades.filter { it.studentId == student.id && it.subjectId == MAIN_SUBJECT_ID }
                        val assignment = studentGrades.firstOrNull { it.gradeType == ASSIGNMENT_TYPE }
                        val exam = studentGrades.firstOrNull { it.gradeType == EXAM_TYPE }
                        val average = remember(studentGrades) { AverageCalculator.calculateSubjectAverage(studentGrades) }
                        GradeEntryCard(
                            student = student,
                            trimester = trimester,
                            assignment = assignment,
                            exam = exam,
                            average = average,
                            onSave = { assignmentValue, examValue ->
                                scope.launch {
                                    try {
                                        persistGrade(repo, student, groupId, trimester, ASSIGNMENT_TYPE, assignment, assignmentValue)
                                        persistGrade(repo, student, groupId, trimester, EXAM_TYPE, exam, examValue)
                                        snackbarHostState.showSnackbar("تم حفظ علامات ${student.firstName} بنجاح")
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar("تعذّر حفظ العلامات. حاول مرة أخرى.")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private const val MAIN_SUBJECT_ID = "main"
private const val ASSIGNMENT_TYPE = "فرض"
private const val EXAM_TYPE = "اختبار"

private suspend fun persistGrade(
    repo: AppRepository,
    student: Student,
    groupId: String,
    trimester: Int,
    type: String,
    current: Grade?,
    value: Double?
) {
    if (value == null) {
        if (current != null) repo.deleteGrade(student.id, MAIN_SUBJECT_ID, trimester, type)
        return
    }
    repo.saveGrade(
        Grade(
            id = current?.id ?: java.util.UUID.randomUUID().toString(),
            studentId = student.id,
            subjectId = MAIN_SUBJECT_ID,
            groupId = groupId,
            value = value,
            maxScore = 20.0,
            gradeType = type,
            trimester = trimester,
            version = current?.version ?: 1L
        )
    )
}

@Composable
private fun GradeEntryCard(
    student: Student,
    trimester: Int,
    assignment: Grade?,
    exam: Grade?,
    average: Double?,
    onSave: (Double?, Double?) -> Unit
) {
    var assignmentText by remember(student.id, trimester, assignment?.id, assignment?.version) {
        mutableStateOf(assignment?.value?.let(::formatNumber).orEmpty())
    }
    var examText by remember(student.id, trimester, exam?.id, exam?.version) {
        mutableStateOf(exam?.value?.let(::formatNumber).orEmpty())
    }
    val assignmentValue = parseGrade(assignmentText)
    val examValue = parseGrade(examText)
    val assignmentInvalid = assignmentText.isNotBlank() && assignmentValue == null
    val examInvalid = examText.isNotBlank() && examValue == null
    val hasChanges = assignmentValue != assignment?.value || examValue != exam?.value

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${student.lastName} ${student.firstName}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    average?.let { "${formatNumber(it)} / 20" } ?: "—",
                    fontWeight = FontWeight.Bold,
                    color = average?.let { if (it >= 10.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = assignmentText,
                    onValueChange = { assignmentText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("الفرض") },
                    suffix = { Text("/20") },
                    singleLine = true,
                    isError = assignmentInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = examText,
                    onValueChange = { examText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("الاختبار") },
                    suffix = { Text("/20") },
                    singleLine = true,
                    isError = examInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            if (assignmentInvalid || examInvalid) {
                Text(
                    "أدخل علامة صحيحة بين 0 و20، أو اترك الحقل فارغاً.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("معدل الفصل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        average?.let { "${formatNumber(it)} / 20" } ?: "لم يُحسب بعد",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onSave(assignmentValue, examValue) },
                    enabled = !assignmentInvalid && !examInvalid && hasChanges,
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ العلامات")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsencesScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val repo = remember(db) { AppRepository(db) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    val absences by repo.getAbsences(groupId).collectAsState(initial = emptyList())
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { PageTopBar(title = "سجل الغيابات", onBack = { navController.navigateUp() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        if (students.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.EventBusy,
                    title = "لا يوجد تلاميذ في هذا الفوج",
                    message = "أضف التلاميذ أولاً لتسجيل الغيابات."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "اختر اسم التلميذ لتسجيل غياب اليوم.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(students, key = { it.id }) { student ->
                    val count = absences.count { it.studentId == student.id }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedStudentId = student.id },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${student.lastName} ${student.firstName}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (count == 0) "لا توجد غيابات مسجلة" else "$count غياب مسجل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (count > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    count.toString(),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedStudentId?.let { studentId ->
        val selectedStudent = students.firstOrNull { it.id == studentId }
        AlertDialog(
            onDismissRequest = { selectedStudentId = null },
            title = { Text("تسجيل غياب") },
            text = {
                Text(
                    selectedStudent?.let { "هل تريد تسجيل غياب ${it.firstName} ${it.lastName} بتاريخ اليوم؟" }
                        ?: "هل تريد تسجيل غياب لهذا التلميذ اليوم؟"
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            val added = repo.addAbsence(Absence(studentId = studentId, groupId = groupId))
                            selectedStudentId = null
                            snackbarHostState.showSnackbar(
                                if (added) "تم تسجيل الغياب" else "سُجّل غياب لهذا التلميذ اليوم بالفعل"
                            )
                        } catch (_: Exception) {
                            selectedStudentId = null
                            snackbarHostState.showSnackbar("تعذّر تسجيل الغياب. حاول مرة أخرى.")
                        }
                    }
                }) { Text("تأكيد الغياب") }
            },
            dismissButton = {
                TextButton(onClick = { selectedStudentId = null }) { Text("إلغاء") }
            }
        )
    }
}

// ==========================================
// Reports and settings
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    var trimester by rememberSaveable { mutableIntStateOf(1) }
    val grades by db.gradeDao().getByGroupAndTrimester(groupId, trimester).collectAsState(initial = emptyList())
    val rankedStudents = remember(students, grades) {
        students.map { student ->
            val studentGrades = grades.filter { it.studentId == student.id }
            student to AverageCalculator.calculateSubjectAverage(studentGrades)
        }.sortedWith(
            compareBy<Pair<Student, Double?>> { it.second == null }
                .thenByDescending { it.second ?: -1.0 }
                .thenBy { it.first.lastName }
                .thenBy { it.first.firstName }
        )
    }

    Scaffold(
        topBar = { PageTopBar(title = "المداولات والترتيب", onBack = { navController.navigateUp() }) }
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = trimester - 1) {
                (1..3).forEach { term ->
                    Tab(
                        selected = trimester == term,
                        onClick = { trimester = term },
                        text = { Text("الفصل $term") }
                    )
                }
            }
            Text(
                "ترتيب الفوج حسب المعدل المسجّل للفصل $trimester",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (rankedStudents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Assessment,
                        title = "لا يوجد تلاميذ",
                        message = "ستظهر المداولات هنا بعد إضافة تلاميذ الفوج."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rankedStudents, key = { it.first.id }) { (student, average) ->
                        val rank = if (average == null) null else rankedStudents
                            .takeWhile { it.first.id != student.id }
                            .count { it.second != null } + 1
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(rank?.let { "#$it" } ?: "—", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${student.lastName} ${student.firstName}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (average == null) "لم تُرصد علامات بعد" else "معدل الفصل $trimester",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    average?.let { "${formatNumber(it)} / 20" } ?: "—",
                                    fontWeight = FontWeight.Bold,
                                    color = average?.let { if (it >= 10.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { AuthService(context) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { PageTopBar(title = "الإعدادات", onBack = { navController.navigateUp() }) }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ملف الأستاذ المحلي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(auth.userEmail ?: "غير مسجل", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "هذا الملف محفوظ على الجهاز ولا يمثل حساباً سحابياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("التخزين والخصوصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "سجلات التلاميذ محفوظة محلياً. النسخ الاحتياطي التلقائي للنظام معطّل لهذا التطبيق.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("الخروج من الملف المحلي")
            }
        }
    }

    if (confirmLogout) {
        ConfirmActionDialog(
            title = "الخروج من الملف؟",
            message = "سيُطلب منك إدخال البريد الإلكتروني مجدداً. لن تُحذف سجلاتك المحلية.",
            confirmLabel = "خروج",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                auth.logout()
                confirmLogout = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val pendingCount by db.syncQueueDao().observePendingCount().collectAsState(initial = 0)

    Scaffold(
        topBar = { PageTopBar(title = "حالة المزامنة", onBack = { navController.navigateUp() }) }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("المزامنة السحابية غير مفعّلة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "لا يتصل هذا الإصدار بخادم خارجي. بياناتك تُحفظ على الجهاز، ولا نعرض العمليات المحلية على أنها تمت مزامنتها.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("عمليات بانتظار خدمة المزامنة", fontWeight = FontWeight.SemiBold)
                        Text("ستبقى محفوظة حتى إعداد اتصال سحابي فعلي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(pendingCount.toString(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ==========================================
// Shared UI components and formatting
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        modifier = Modifier.graphicsLayer { rotationZ = 180f }
                    )
                }
            }
        },
        actions = actions
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddGroupDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf(currentAcademicYear()) }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة فوج جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم القسم، مثال: 1م1") },
                    singleLine = true,
                    isError = error && name.isBlank()
                )
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("المستوى الدراسي") },
                    singleLine = true,
                    isError = error && level.isBlank()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("السنة الدراسية") },
                    singleLine = true,
                    isError = error && year.isBlank()
                )
                if (error) Text("أكمل جميع الحقول المطلوبة.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || level.isBlank() || year.isBlank()) error = true
                else onSave(name.trim(), level.trim(), year.trim())
            }) { Text("حفظ الفوج") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddStudentDialog(onDismiss: () -> Unit, onSave: (String, String, String?, String) -> Unit) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var registrationNumber by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("M") }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تلميذ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اللقب *") },
                    singleLine = true,
                    isError = error && lastName.isBlank()
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الاسم *") },
                    singleLine = true,
                    isError = error && firstName.isBlank()
                )
                OutlinedTextField(
                    value = registrationNumber,
                    onValueChange = { registrationNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رقم التسجيل (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = gender == "M", onClick = { gender = "M" })
                    Text("ذكر", modifier = Modifier.clickable { gender = "M" })
                    Spacer(Modifier.width(14.dp))
                    RadioButton(selected = gender == "F", onClick = { gender = "F" })
                    Text("أنثى", modifier = Modifier.clickable { gender = "F" })
                }
                if (error) Text("أدخل الاسم واللقب للمتابعة.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (firstName.isBlank() || lastName.isBlank()) error = true
                else onSave(firstName.trim(), lastName.trim(), registrationNumber.trim().ifBlank { null }, gender)
            }) { Text("إضافة التلميذ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun currentAcademicYear(): String {
    val date = LocalDate.now()
    val firstYear = if (date.monthValue >= 9) date.year else date.year - 1
    return "$firstYear-${firstYear + 1}"
}

private fun parseGrade(text: String): Double? {
    if (text.isBlank()) return null
    return text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..20.0 }
}

private fun formatNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
