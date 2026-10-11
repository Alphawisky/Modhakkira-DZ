package com.moudhakkira

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
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
@Entity(
    tableName = "grades",
    indices = [Index(
        value = ["studentId", "subjectId", "trimester", "gradeType"],
        unique = true,
        name = "index_grades_logical_key"
    )]
)
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

    @Query("UPDATE groups SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isDeleted = 0 AND groupId = :groupId ORDER BY lastName ASC, firstName ASC")
    fun getByGroup(groupId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND groupId = :groupId ORDER BY lastName ASC, firstName ASC")
    suspend fun getActiveByGroup(groupId: String): List<Student>

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND id = :id")
    suspend fun getById(id: String): Student?

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND (firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE isDeleted = 0 AND groupId = :groupId AND (firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%') ORDER BY lastName ASC, firstName ASC")
    fun searchByGroup(query: String, groupId: String): Flow<List<Student>>

    @Query("UPDATE students SET isDeleted = 1, updatedAt = :now WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun softDeleteByGroup(groupId: String, now: Long = System.currentTimeMillis())

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
    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND groupId = :groupId AND trimester = :trimester ORDER BY studentId ASC, subjectId ASC, gradeType ASC")
    fun getByGroupAndTrimester(groupId: String, trimester: Int): Flow<List<Grade>>

    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND studentId = :studentId ORDER BY trimester DESC, updatedAt DESC")
    fun getByStudent(studentId: String): Flow<List<Grade>>

    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND groupId = :groupId")
    suspend fun getActiveByGroup(groupId: String): List<Grade>

    @Query("SELECT * FROM grades WHERE isDeleted = 0 AND studentId = :studentId")
    suspend fun getActiveByStudent(studentId: String): List<Grade>

    @Query("SELECT * FROM grades WHERE studentId = :studentId AND subjectId = :subjectId AND trimester = :trimester AND gradeType = :gradeType LIMIT 1")
    suspend fun getByLogicalKey(studentId: String, subjectId: String, trimester: Int, gradeType: String): Grade?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(grade: Grade)

    @Query("UPDATE grades SET isDeleted = 1, updatedAt = :now, version = version + 1 WHERE id = :id AND isDeleted = 0")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE grades SET isDeleted = 1, updatedAt = :now, version = version + 1 WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun softDeleteByGroup(groupId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE grades SET isDeleted = 1, updatedAt = :now, version = version + 1 WHERE studentId = :studentId AND isDeleted = 0")
    suspend fun softDeleteByStudent(studentId: String, now: Long = System.currentTimeMillis())
}

@Dao
interface AbsenceDao {
    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND groupId = :groupId ORDER BY date DESC")
    fun getByGroup(groupId: String): Flow<List<Absence>>

    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND groupId = :groupId")
    suspend fun getActiveByGroup(groupId: String): List<Absence>

    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND studentId = :studentId ORDER BY date DESC")
    fun getByStudent(studentId: String): Flow<List<Absence>>

    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND studentId = :studentId")
    suspend fun getActiveByStudent(studentId: String): List<Absence>

    @Query("SELECT * FROM absences WHERE isDeleted = 0 AND studentId = :studentId AND groupId = :groupId AND period = :period AND date >= :startOfDay AND date < :endOfDay LIMIT 1")
    suspend fun findForDay(studentId: String, groupId: String, period: Int, startOfDay: Long, endOfDay: Long): Absence?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(absence: Absence)

    @Query("UPDATE absences SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE absences SET isDeleted = 1 WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun softDeleteByGroup(groupId: String)

    @Query("UPDATE absences SET isDeleted = 1 WHERE studentId = :studentId AND isDeleted = 0")
    suspend fun softDeleteByStudent(studentId: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC LIMIT 50")
    suspend fun getPending(): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' AND entityId = :entityId AND entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getPendingForEntity(entityId: String, entityType: String): List<SyncQueueItem>

    @Query("DELETE FROM sync_queue WHERE status = 'pending' AND entityId = :entityId AND entityType = :entityType")
    suspend fun deletePendingForEntity(entityId: String, entityType: String)

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
    version = 2,
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
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The previous UI stored a new random row after every keystroke. Keep the
                // latest row for each editable mark before enforcing one row per mark.
                database.execSQL(
                    "DELETE FROM grades WHERE rowid NOT IN (" +
                        "SELECT MAX(rowid) FROM grades " +
                        "GROUP BY studentId, subjectId, trimester, gradeType)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_grades_logical_key " +
                        "ON grades (studentId, subjectId, trimester, gradeType)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moudhakkira_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ==========================================
// 4. AVERAGE CALCULATOR (حساب المعدلات بدقة)
// ==========================================

object AverageCalculator {
    fun calculateSubjectAverage(grades: List<Grade>): Double? {
        val validGrades = grades.filter { grade ->
            !grade.isDeleted && grade.maxScore > 0.0 && grade.maxScore.isFinite() &&
                grade.value.isFinite() && grade.value in 0.0..grade.maxScore
        }
        if (validGrades.isEmpty()) return null

        // Keep all marks on the Algerian /20 scale if a different maximum is introduced later.
        val exams = validGrades.filter { it.gradeType == "اختبار" }.map { it.value * 20.0 / it.maxScore }
        val assignments = validGrades.filter { it.gradeType == "فرض" }.map { it.value * 20.0 / it.maxScore }
        if (exams.isEmpty() && assignments.isEmpty()) return null

        val assignAvg = assignments.averageOrZero()
        val examAvg = exams.averageOrZero()
        val raw = when {
            assignments.isNotEmpty() && exams.isNotEmpty() -> (assignAvg + (examAvg * 2.0)) / 3.0
            exams.isNotEmpty() -> examAvg
            else -> assignAvg
        }
        return roundToQuarter(raw.coerceIn(0.0, 20.0))
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    fun roundToQuarter(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 4).roundToInt() / 4.0
    }
}

// ==========================================
// 5. SECURITY MANAGER
// ==========================================

class SecurityManager(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "moudhakkira_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Recover a usable local profile if the device keystore was reset or corrupted.
            appContext.getSharedPreferences("moudhakkira_local_profile", Context.MODE_PRIVATE)
        }
    }

    fun saveUserEmail(email: String) = prefs.edit().putString("user_email", email.trim()).apply()
    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun clear() = prefs.edit().clear().apply()
}
