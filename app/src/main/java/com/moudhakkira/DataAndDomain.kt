package com.moudhakkira

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

// ==========================================
// 1. ENTITIES (الكيانات وقواعد البيانات)
// ==========================================

@Serializable
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val level: String,
    val academicYear: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Serializable
@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val firstName: String,
    val lastName: String,
    val registrationNumber: String? = null,
    val groupId: String,
    val gender: String = "M",
    val phone: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1,
    val isDeleted: Boolean = false
)

@Serializable
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coefficient: Double = 1.0,
    val groupId: String,
    val isDeleted: Boolean = false
)

@Serializable
@Entity(tableName = "grades")
data class Grade(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val subjectId: String,
    val groupId: String,
    val value: Double,
    val maxScore: Double = 20.0,
    val gradeType: String, // "فرض", "اختبار"
    val trimester: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1,
    val isDeleted: Boolean = false
)

@Serializable
@Entity(tableName = "absences")
data class Absence(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val groupId: String,
    val date: Long = System.currentTimeMillis(),
    val period: Int = 1,
    val isJustified: Boolean = false,
    val reason: String? = null,
    val isDeleted: Boolean = false
)

@Serializable
@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val entityId: String,
    val entityType: String,
    val operation: String, // "create", "update", "delete"
    val dataJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending"
)

// ==========================================
// 2. DAOs (واجهات الاستعلام)
// ==========================================

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAll(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: String): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: Group)

    @Query("UPDATE groups SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isDeleted = 0 AND groupId = :groupId ORDER BY lastName ASC, firstName ASC")
    fun getByGroup(groupId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND id = :id")
    suspend fun getById(id: String): Student?

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND (firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<Student>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: Student)

    @Update
    suspend fun update(student: Student)

    @Query("UPDATE students SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE isDeleted = 0 AND groupId = :groupId")
    fun getByGroup(groupId: String): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: Subject)
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND groupId = :groupId AND trimester = :trimester")
    fun getByGroupAndTrimester(groupId: String, trimester: Int): Flow<List<Grade>>

    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND studentId = :studentId")
    fun getByStudent(studentId: String): Flow<List<Grade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(grade: Grade)

    @Query("UPDATE grades SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)
}

@Dao
interface AbsenceDao {
    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND groupId = :groupId ORDER BY date DESC")
    fun getByGroup(groupId: String): Flow<List<Absence>>

    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND studentId = :studentId ORDER BY date DESC")
    fun getByStudent(studentId: String): Flow<List<Absence>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(absence: Absence)

    @Query("UPDATE absences SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC LIMIT 50")
    suspend fun getPending(): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItem)

    @Query("UPDATE sync_queue SET status = 'completed' WHERE queueId = :id")
    suspend fun markCompleted(id: Long)
}

// ==========================================
// 3. DATABASE
// ==========================================

@Database(
    entities = [Group::class, Student::class, Subject::class, Grade::class, Absence::class, SyncQueueItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun studentDao(): StudentDao
    abstract fun subjectDao(): SubjectDao
    abstract fun gradeDao(): GradeDao
    abstract fun absenceDao(): AbsenceDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moudhakkira_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

// ==========================================
// 4. AVERAGE CALCULATOR (حساب المعدلات بدقة)
// ==========================================

object AverageCalculator {
    fun calculateSubjectAverage(grades: List<Grade>): Double? {
        if (grades.isEmpty()) return null
        val exams = grades.filter { it.gradeType == "اختبار" }
        val assignments = grades.filter { it.gradeType == "فرض" }

        if (exams.isEmpty() && assignments.isEmpty()) return null

        val assignAvg = if (assignments.isNotEmpty()) assignments.map { it.value }.average() else 0.0
        val examAvg = if (exams.isNotEmpty()) exams.map { it.value }.average() else 0.0

        val raw = when {
            assignments.isNotEmpty() && exams.isNotEmpty() -> (assignAvg + (examAvg * 2)) / 3.0
            exams.isNotEmpty() -> examAvg
            else -> assignAvg
        }
        return roundToQuarter(raw)
    }

    fun roundToQuarter(value: Double): Double {
        return (value * 4).roundToInt() / 4.0
    }
}

// ==========================================
// 5. SECURITY MANAGER
// ==========================================

class SecurityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "moudhakkira_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserEmail(email: String) = prefs.edit().putString("user_email", email).apply()
    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun clear() = prefs.edit().clear().apply()
}
