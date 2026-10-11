package com.moudhakkira

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

/** Local profile storage only; this is not a remote authentication service. */
class AuthService(context: Context) {
    private val security = SecurityManager(context)

    val isLoggedIn: Boolean
        get() = !security.getUserEmail().isNullOrBlank()

    val userEmail: String?
        get() = security.getUserEmail()

    fun loginWithEmail(email: String) {
        security.saveUserEmail(email.trim())
    }

    fun logout() {
        security.clear()
    }
}

/**
 * Local-first repository. Changes are committed with their outbox record in one transaction.
 * The outbox is intentionally retained until a real remote sync endpoint is configured.
 */
class AppRepository(private val db: AppDatabase) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // Groups
    fun getGroups(): Flow<List<Group>> = db.groupDao().getAll()

    suspend fun addGroup(name: String, level: String, year: String) {
        val group = Group(
            name = name.trim(),
            level = level.trim(),
            academicYear = year.trim()
        )
        db.withTransaction {
            db.groupDao().insert(group)
            enqueue(group.id, "groups", "create", json.encodeToString(group))
        }
    }

    suspend fun deleteGroup(id: String) {
        db.withTransaction {
            val students = db.studentDao().getActiveByGroup(id)
            val grades = db.gradeDao().getActiveByGroup(id)
            val absences = db.absenceDao().getActiveByGroup(id)
            val now = System.currentTimeMillis()

            db.groupDao().softDelete(id, now)
            db.studentDao().softDeleteByGroup(id, now)
            db.gradeDao().softDeleteByGroup(id, now)
            db.absenceDao().softDeleteByGroup(id)

            students.forEach { enqueue(it.id, "students", "delete", deletionJson(it.id)) }
            grades.forEach { enqueue(it.id, "grades", "delete", deletionJson(it.id)) }
            absences.forEach { enqueue(it.id, "absences", "delete", deletionJson(it.id)) }
            enqueue(id, "groups", "delete", deletionJson(id))
        }
    }

    // Students
    fun getStudents(groupId: String): Flow<List<Student>> = db.studentDao().getByGroup(groupId)
    fun searchStudents(query: String, groupId: String): Flow<List<Student>> =
        db.studentDao().searchByGroup(query.trim(), groupId)

    suspend fun addStudent(student: Student) {
        val cleanStudent = student.copy(
            firstName = student.firstName.trim(),
            lastName = student.lastName.trim(),
            registrationNumber = student.registrationNumber?.trim()?.takeIf(String::isNotEmpty)
        )
        db.withTransaction {
            db.studentDao().insert(cleanStudent)
            enqueue(cleanStudent.id, "students", "create", json.encodeToString(cleanStudent))
        }
    }

    suspend fun updateStudent(student: Student) {
        val updated = student.copy(
            firstName = student.firstName.trim(),
            lastName = student.lastName.trim(),
            registrationNumber = student.registrationNumber?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = System.currentTimeMillis(),
            version = student.version + 1
        )
        db.withTransaction {
            db.studentDao().update(updated)
            enqueue(updated.id, "students", "update", json.encodeToString(updated))
        }
    }

    suspend fun deleteStudent(id: String) {
        db.withTransaction {
            val grades = db.gradeDao().getActiveByStudent(id)
            val absences = db.absenceDao().getActiveByStudent(id)
            val now = System.currentTimeMillis()

            db.studentDao().softDelete(id, now)
            db.gradeDao().softDeleteByStudent(id, now)
            db.absenceDao().softDeleteByStudent(id)

            grades.forEach { enqueue(it.id, "grades", "delete", deletionJson(it.id)) }
            absences.forEach { enqueue(it.id, "absences", "delete", deletionJson(it.id)) }
            enqueue(id, "students", "delete", deletionJson(id))
        }
    }

    // Grades: the logical key is one mark per student, subject, trimester and type.
    fun getGrades(groupId: String, trimester: Int): Flow<List<Grade>> =
        db.gradeDao().getByGroupAndTrimester(groupId, trimester)

    fun getGradesForStudent(studentId: String): Flow<List<Grade>> =
        db.gradeDao().getByStudent(studentId)

    suspend fun saveGrade(grade: Grade) {
        require(grade.maxScore.isFinite() && grade.maxScore > 0.0) { "Invalid maximum score" }
        require(grade.value.isFinite() && grade.value in 0.0..grade.maxScore) { "Grade is outside its allowed range" }
        require(grade.trimester in 1..3) { "Invalid trimester" }

        db.withTransaction {
            val gradeDao = db.gradeDao()
            val existing = gradeDao.getByLogicalKey(
                studentId = grade.studentId,
                subjectId = grade.subjectId,
                trimester = grade.trimester,
                gradeType = grade.gradeType
            )
            val now = System.currentTimeMillis()
            val saved = grade.copy(
                id = existing?.id ?: grade.id,
                createdAt = existing?.createdAt ?: grade.createdAt,
                updatedAt = now,
                version = (existing?.version ?: 0L) + 1L,
                isDeleted = false
            )
            gradeDao.insert(saved)
            val operation = if (existing == null || existing.isDeleted) "create" else "update"
            enqueue(saved.id, "grades", operation, json.encodeToString(saved))
        }
    }

    suspend fun deleteGrade(studentId: String, subjectId: String, trimester: Int, gradeType: String) {
        db.withTransaction {
            val grade = db.gradeDao().getByLogicalKey(studentId, subjectId, trimester, gradeType)
                ?: return@withTransaction
            if (grade.isDeleted) return@withTransaction

            val now = System.currentTimeMillis()
            db.gradeDao().softDelete(grade.id, now)
            val deletedGrade = grade.copy(
                isDeleted = true,
                updatedAt = now,
                version = grade.version + 1
            )
            enqueue(grade.id, "grades", "delete", json.encodeToString(deletedGrade))
        }
    }

    // Absences
    fun getAbsences(groupId: String): Flow<List<Absence>> = db.absenceDao().getByGroup(groupId)

    suspend fun addAbsence(absence: Absence): Boolean {
        val day = Calendar.getInstance().apply {
            timeInMillis = absence.date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = day.timeInMillis
        day.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = day.timeInMillis

        return db.withTransaction {
            val existing = db.absenceDao().findForDay(
                studentId = absence.studentId,
                groupId = absence.groupId,
                period = absence.period,
                startOfDay = startOfDay,
                endOfDay = endOfDay
            )
            if (existing != null) return@withTransaction false

            db.absenceDao().insert(absence)
            enqueue(absence.id, "absences", "create", json.encodeToString(absence))
            true
        }
    }

    private suspend fun enqueue(entityId: String, entityType: String, operation: String, dataJson: String) {
        val queue = db.syncQueueDao()
        val pending = queue.getPendingForEntity(entityId, entityType)
        val hasPendingCreate = pending.any { it.operation == "create" }

        // Keep only the newest payload for an entity. A local create followed by a local
        // delete never needs to be sent to a server, so cancel the pending pair entirely.
        queue.deletePendingForEntity(entityId, entityType)
        if (operation == "delete" && hasPendingCreate) return

        val effectiveOperation = if (operation == "update" && hasPendingCreate) "create" else operation
        queue.enqueue(
            SyncQueueItem(
                entityId = entityId,
                entityType = entityType,
                operation = effectiveOperation,
                dataJson = dataJson
            )
        )
    }

    private fun deletionJson(id: String): String = json.encodeToString(mapOf("id" to id))
}
