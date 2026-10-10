package com.moudhakkira

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

// ==========================================
// 1. AUTH SERVICE (محلي وقابل للربط السحابي)
// ==========================================

class AuthService(private val context: Context) {
    private val security = SecurityManager(context)

    val isLoggedIn: Boolean
        get() = security.getUserEmail() != null

    val userEmail: String?
        get() = security.getUserEmail()

    fun loginWithEmail(email: String) {
        security.saveUserEmail(email)
    }

    fun logout() {
        security.clear()
    }
}

// ==========================================
// 2. REPOSITORIES
// ==========================================

class AppRepository(private val db: AppDatabase) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // Groups
    fun getGroups(): Flow<List<Group>> = db.groupDao().getAll()
    suspend fun addGroup(name: String, level: String, year: String) {
        val group = Group(name = name, level = level, academicYear = year)
        db.groupDao().insert(group)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = group.id, entityType = "groups", operation = "create", dataJson = json.encodeToString(group)))
    }
    suspend fun deleteGroup(id: String) {
        db.groupDao().softDelete(id)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = id, entityType = "groups", operation = "delete", dataJson = """{"id":"$id"}"""))
    }

    // Students
    fun getStudents(groupId: String): Flow<List<Student>> = db.studentDao().getByGroup(groupId)
    fun searchStudents(q: String): Flow<List<Student>> = db.studentDao().search(q)
    suspend fun addStudent(student: Student) {
        db.studentDao().insert(student)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = student.id, entityType = "students", operation = "create", dataJson = json.encodeToString(student)))
    }
    suspend fun updateStudent(student: Student) {
        val updated = student.copy(updatedAt = System.currentTimeMillis(), version = student.version + 1)
        db.studentDao().update(updated)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = updated.id, entityType = "students", operation = "update", dataJson = json.encodeToString(updated)))
    }
    suspend fun deleteStudent(id: String) {
        db.studentDao().softDelete(id)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = id, entityType = "students", operation = "delete", dataJson = """{"id":"$id"}"""))
    }

    // Grades
    fun getGrades(groupId: String, trimester: Int): Flow<List<Grade>> = db.gradeDao().getByGroupAndTrimester(groupId, trimester)
    fun getGradesForStudent(studentId: String): Flow<List<Grade>> = db.gradeDao().getByStudent(studentId)
    suspend fun saveGrade(grade: Grade) {
        db.gradeDao().insert(grade)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = grade.id, entityType = "grades", operation = "create", dataJson = json.encodeToString(grade)))
    }

    // Absences
    fun getAbsences(groupId: String): Flow<List<Absence>> = db.absenceDao().getByGroup(groupId)
    suspend fun addAbsence(absence: Absence) {
        db.absenceDao().insert(absence)
        db.syncQueueDao().enqueue(SyncQueueItem(entityId = absence.id, entityType = "absences", operation = "create", dataJson = json.encodeToString(absence)))
    }
}

// ==========================================
// 3. SYNC WORKER (مزامنة خلفية ذكية)
// ==========================================

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val pending = db.syncQueueDao().getPending()
        for (item in pending) {
            // محاكاة الإرسال السحابي الآمن
            db.syncQueueDao().markCompleted(item.queueId)
        }
        Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "moudhakkira_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
