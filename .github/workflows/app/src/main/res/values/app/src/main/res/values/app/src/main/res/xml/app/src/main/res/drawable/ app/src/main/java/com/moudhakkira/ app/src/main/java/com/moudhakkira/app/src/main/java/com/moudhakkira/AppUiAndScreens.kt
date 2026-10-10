package com.moudhakkira

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

// ==========================================
// 1. NAVIGATION
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
        composable(Screen.Students.route) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            StudentsScreen(navController, groupId)
        }
        composable(Screen.StudentDetail.route) { back ->
            val studentId = back.arguments?.getString("studentId") ?: ""
            StudentDetailScreen(navController, studentId)
        }
        composable(Screen.Grades.route) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            GradesScreen(navController, groupId)
        }
        composable(Screen.Absences.route) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            AbsencesScreen(navController, groupId)
        }
        composable(Screen.Reports.route) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            ReportsScreen(navController, groupId)
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.SyncStatus.route) { SyncStatusScreen(navController) }
    }
}

// ==========================================
// 2. VIEWMODELS
// ==========================================

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    val repo = AppRepository(db)
    val auth = AuthService(application)

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
    val repo = AppRepository(db)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getStudents(groupId: String): Flow<List<Student>> {
        return _query.flatMapLatest { q ->
            if (q.isBlank()) repo.getStudents(groupId)
            else repo.searchStudents(q).map { l -> l.filter { it.groupId == groupId } }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun addStudent(firstName: String, lastName: String, regNo: String?, groupId: String, gender: String) {
        viewModelScope.launch {
            repo.addStudent(Student(firstName = firstName, lastName = lastName, registrationNumber = regNo, groupId = groupId, gender = gender))
        }
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch { repo.deleteStudent(id) }
    }
}

// ==========================================
// 3. ALL SCREENS (كافة الشاشات بدون أي نقص)
// ==========================================

// --- LOGIN SCREEN ---
@Composable
fun LoginScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { AuthService(context) }
    var email by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("مذكرة الأستاذ الإلكترونية", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("تطبيق إدارة الأقسام والعلامات وحساب المعدلات", color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني للأستاذ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val targetEmail = if (email.isBlank()) "professeur@education.dz" else email
                    auth.loginWithEmail(targetEmail)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("دخول إلى المذكرة")
            }
        }
    }
}

// --- HOME SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MainViewModel = viewModel()) {
    val groups by vm.groups.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("أفواجي التربوية") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.SyncStatus.route) }) {
                        Icon(Icons.Default.CloudDone, "المزامنة", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, "الإعدادات")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "إضافة فوج")
            }
        }
    ) { p ->
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("لا توجد أفواج حالياً. اضغط + لإضافة فوجك الأول.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.id }) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.Students.create(group.id)) },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Class, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${group.level} • ${group.academicYear}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteGroup(group.id) }) {
                                Icon(Icons.Default.DeleteOutline, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var level by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("2024-2025") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة فوج تربوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم القسم (مثال: 1م1)") })
                    OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("المستوى (مثال: الأولى متوسط)") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("السنة الدراسية") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            vm.addGroup(name, level, year)
                            showAddDialog = false
                        }
                    }
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

// --- STUDENTS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(navController: NavController, groupId: String, vm: StudentsViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val students by vm.getStudents(groupId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قائمة التلاميذ") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Grades.create(groupId)) }) { Icon(Icons.Default.EditNote, "العلامات") }
                    IconButton(onClick = { navController.navigate(Screen.Absences.create(groupId)) }) { Icon(Icons.Default.EventBusy, "الغيابات") }
                    IconButton(onClick = { navController.navigate(Screen.Reports.create(groupId)) }) { Icon(Icons.Default.Assessment, "المداولات") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.PersonAdd, "إضافة تلميذ") } }
    ) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("بحث عن تلميذ...") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(students, key = { it.id }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.StudentDetail.create(s.id)) },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s.gender == "F") Icons.Default.Face3 else Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${s.lastName} ${s.firstName}", fontWeight = FontWeight.Bold)
                                s.registrationNumber?.let { Text("رقم: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            IconButton(onClick = { vm.deleteStudent(s.id) }) {
                                Icon(Icons.Default.DeleteOutline, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var first by remember { mutableStateOf("") }
        var last by remember { mutableStateOf("") }
        var reg by remember { mutableStateOf("") }
        var gender by remember { mutableStateOf("M") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة تلميذ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = last, onValueChange = { last = it }, label = { Text("اللقب *") })
                    OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("الاسم *") })
                    OutlinedTextField(value = reg, onValueChange = { reg = it }, label = { Text("رقم التسجيل") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = gender == "M", onClick = { gender = "M" })
                        Text("ذكر")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = gender == "F", onClick = { gender = "F" })
                        Text("أنثى")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (first.isNotBlank() && last.isNotBlank()) {
                            vm.addStudent(first, last, reg.ifBlank { null }, groupId, gender)
                            showAddDialog = false
                        }
                    }
                ) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

// --- STUDENT DETAIL SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(navController: NavController, studentId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var student by remember { mutableStateOf<Student?>(null) }
    val grades by db.gradeDao().getByStudent(studentId).collectAsState(initial = emptyList())
    val absences by db.absenceDao().getByStudent(studentId).collectAsState(initial = emptyList())

    LaunchedEffect(studentId) {
        student = db.studentDao().getById(studentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(student?.let { "${it.lastName} ${it.firstName}" } ?: "تفاصيل التلميذ") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        student?.let { s ->
            Column(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الاسم واللقب: ${s.lastName} ${s.firstName}", fontWeight = FontWeight.Bold)
                        Text("رقم التسجيل: ${s.registrationNumber ?: "غير مسجل"}")
                        Text("الجنس: ${if (s.gender == "M") "ذكر" else "أنثى"}")
                        Text("عدد الغيابات المسجلة: ${absences.size}")
                    }
                }

                Text("سجل النقاط:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(grades) { g ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${g.gradeType} (فصل ${g.trimester})")
                                Text("${g.value} / ${g.maxScore}", fontWeight = FontWeight.Bold, color = if (g.value >= 10) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

// --- GRADES SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val repo = remember { AppRepository(db) }
    var trimester by remember { mutableStateOf(1) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    val grades by repo.getGrades(groupId, trimester).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رصد العلامات والتقييم") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p)) {
            TabRow(selectedTabIndex = trimester - 1) {
                Tab(selected = trimester == 1, onClick = { trimester = 1 }, text = { Text("الفصل 1") })
                Tab(selected = trimester == 2, onClick = { trimester = 2 }, text = { Text("الفصل 2") })
                Tab(selected = trimester == 3, onClick = { trimester = 3 }, text = { Text("الفصل 3") })
            }

            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp)) {
                Text("التلميذ", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("الفرض", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Text("الاختبار", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Text("المعدل", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(students, key = { it.id }) { s ->
                    val sGrades = grades.filter { it.studentId == s.id }
                    val assign = sGrades.firstOrNull { it.gradeType == "فرض" }?.value
                    val exam = sGrades.firstOrNull { it.gradeType == "اختبار" }?.value
                    val avg = AverageCalculator.calculateSubjectAverage(sGrades)

                    var aText by remember(assign) { mutableStateOf(assign?.toString() ?: "") }
                    var eText by remember(exam) { mutableStateOf(exam?.toString() ?: "") }

                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.lastName} ${s.firstName}", modifier = Modifier.weight(2f), fontSize = 13.sp)
                        OutlinedTextField(
                            value = aText,
                            onValueChange = {
                                aText = it
                                it.toDoubleOrNull()?.let { v ->
                                    if (v in 0.0..20.0) {
                                        scope.launch { repo.saveGrade(Grade(studentId = s.id, subjectId = "main", groupId = groupId, value = v, gradeType = "فرض", trimester = trimester)) }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = eText,
                            onValueChange = {
                                eText = it
                                it.toDoubleOrNull()?.let { v ->
                                    if (v in 0.0..20.0) {
                                        scope.launch { repo.saveGrade(Grade(studentId = s.id, subjectId = "main", groupId = groupId, value = v, gradeType = "اختبار", trimester = trimester)) }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Text(
                            text = avg?.let { String.format("%.2f", it) } ?: "--",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = if ((avg ?: 0.0) >= 10.0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        )
                    }
                    Divider()
                }
            }
        }
    }
}

// --- ABSENCES SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsencesScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val repo = remember { AppRepository(db) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    val absences by repo.getAbsences(groupId).collectAsState(initial = emptyList())
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الغيابات والتأخرات") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Text("اختر التلميذ لتسجيل غياب:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(students) { s ->
                    val count = absences.count { it.studentId == s.id }
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedStudentId = s.id }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${s.lastName} ${s.firstName}")
                            Text("الغيابات: $count", fontWeight = FontWeight.Bold, color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    selectedStudentId?.let { sId ->
        AlertDialog(
            onDismissRequest = { selectedStudentId = null },
            title = { Text("تسجيل غياب جديد") },
            text = { Text("هل تريد تسجيل غياب لهذا التلميذ الآن؟") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repo.addAbsence(Absence(studentId = sId, groupId = groupId))
                        selectedStudentId = null
                    }
                }) { Text("تسجيل غياب") }
            },
            dismissButton = { TextButton(onClick = { selectedStudentId = null }) { Text("إلغاء") } }
        )
    }
}

// --- REPORTS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, groupId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val students by db.studentDao().getByGroup(groupId).collectAsState(initial = emptyList())
    val grades by db.gradeDao().getByGroupAndTrimester(groupId, 1).collectAsState(initial = emptyList())

    val averages = remember(students, grades) {
        students.map { s ->
            val sGrades = grades.filter { it.studentId == s.id }
            val avg = AverageCalculator.calculateSubjectAverage(sGrades) ?: 0.0
            s to avg
        }.sortedByDescending { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المداولات والترتيب الفصلي") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Text("الترتيب العام حسب المعدل (الفصل 1)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(averages.size) { index ->
                    val (student, avg) = averages[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${student.lastName} ${student.firstName}", modifier = Modifier.weight(1f))
                            Text(String.format("%.2f / 20", avg), fontWeight = FontWeight.Bold, color = if (avg >= 10) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
    }
}

// --- SETTINGS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { AuthService(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات التطبيق") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حساب الأستاذ", fontWeight = FontWeight.Bold)
                    Text(auth.userEmail ?: "غير مسجل", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    auth.logout()
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تسجيل الخروج")
            }
        }
    }
}

// --- SYNC STATUS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var pendingCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        pendingCount = db.syncQueueDao().getPendingCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حالة المزامنة السحابية") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        }
    ) { p ->
        Box(modifier = Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Text("قاعدة البيانات المحلية مؤمنة 100%", fontWeight = FontWeight.Bold)
                Text("العمليات المعلقة في قائمة المزامنة: $pendingCount")
            }
        }
    }
}
