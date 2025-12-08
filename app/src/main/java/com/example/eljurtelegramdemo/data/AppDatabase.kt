package com.example.eljurtelegramdemo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val lessonNumber: Int,
    val subject: String,
    val teacher: String,
    val room: String,
    val startTime: String,
    val endTime: String
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule WHERE date = :date ORDER BY lessonNumber")
    fun getScheduleForDate(date: String): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScheduleEntity>)

    @Query("SELECT COUNT(*) FROM schedule")
    suspend fun count(): Int

    @Query("DELETE FROM schedule")
    suspend fun clearAll()
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val isArchived: Boolean = false
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY dueDate")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isArchived = 1 ORDER BY dueDate DESC")
    fun getArchivedTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int
}

@Entity(tableName = "attestations")
data class AttestationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val type: String,
    val date: String,
    val room: String,
    val time: String,
    val description: String
)

@Dao
interface AttestationDao {
    @Query("SELECT * FROM attestations ORDER BY date")
    fun getAll(): Flow<List<AttestationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AttestationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AttestationEntity)

    @Delete
    suspend fun delete(item: AttestationEntity)

    @Query("SELECT COUNT(*) FROM attestations")
    suspend fun count(): Int
}

@Entity(tableName = "eljur_messages")
data class EljurMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromName: String,
    val toName: String,
    val subject: String,
    val body: String,
    val date: String
)

@Dao
interface EljurMessageDao {
    @Query("SELECT * FROM eljur_messages ORDER BY date DESC")
    fun getAll(): Flow<List<EljurMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EljurMessageEntity>)

    @Query("SELECT COUNT(*) FROM eljur_messages")
    suspend fun count(): Int
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Entity(tableName = "task_attachments")
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val uri: String,
    val name: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface TaskAttachmentDao {
    @Query("SELECT * FROM task_attachments ORDER BY addedAt DESC")
    fun getAll(): Flow<List<TaskAttachmentEntity>>

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId ORDER BY addedAt DESC")
    fun getForTask(taskId: Long): Flow<List<TaskAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(att: TaskAttachmentEntity)

    @Delete
    suspend fun delete(att: TaskAttachmentEntity)

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)
}

@Entity(tableName = "attestation_attachments")
data class AttestationAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attestationId: Long,
    val uri: String,
    val name: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface AttestationAttachmentDao {
    @Query("SELECT * FROM attestation_attachments ORDER BY addedAt DESC")
    fun getAll(): Flow<List<AttestationAttachmentEntity>>

    @Query("SELECT * FROM attestation_attachments WHERE attestationId = :attestationId ORDER BY addedAt DESC")
    fun getForAttestation(attestationId: Long): Flow<List<AttestationAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(att: AttestationAttachmentEntity)

    @Delete
    suspend fun delete(att: AttestationAttachmentEntity)

    @Query("DELETE FROM attestation_attachments WHERE attestationId = :attestationId")
    suspend fun deleteForAttestation(attestationId: Long)
}




@Database(
    entities = [
        ScheduleEntity::class,
        TaskEntity::class,
        AttestationEntity::class,
        EljurMessageEntity::class,
        NoteEntity::class,
        TaskAttachmentEntity::class,
        AttestationAttachmentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun taskDao(): TaskDao
    abstract fun attestationDao(): AttestationDao
    abstract fun eljurMessageDao(): EljurMessageDao
    abstract fun noteDao(): NoteDao
    abstract fun taskAttachmentDao(): TaskAttachmentDao
    abstract fun attestationAttachmentDao(): AttestationAttachmentDao
}

object DemoDataInitializer {

    suspend fun ensureDemoData(db: AppDatabase) {
        seedSchedule(db)
        seedMessages(db)
    }

    private suspend fun seedSchedule(db: AppDatabase) {
        val dao = db.scheduleDao()
        dao.clearAll()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayDate = Date()
        val today = sdf.format(todayDate)
        val cal = Calendar.getInstance()
        cal.time = todayDate
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = sdf.format(cal.time)
        val lessons = listOf(
            ScheduleEntity(
                date = today,
                lessonNumber = 1,
                subject = "Математика",
                teacher = "Иванова И.И.",
                room = "201",
                startTime = "08:20",
                endTime = "09:50"
            ),
            ScheduleEntity(
                date = today,
                lessonNumber = 2,
                subject = "Русский язык",
                teacher = "Петрова А.А.",
                room = "203",
                startTime = "10:00",
                endTime = "11:30"
            ),
            ScheduleEntity(
                date = today,
                lessonNumber = 3,
                subject = "Информатика",
                teacher = "Сидоров В.В.",
                room = "305",
                startTime = "11:40",
                endTime = "13:10"
            ),
            ScheduleEntity(
                date = tomorrow,
                lessonNumber = 1,
                subject = "Физика",
                teacher = "Кузнецов К.К.",
                room = "210",
                startTime = "08:20",
                endTime = "09:50"
            ),
            ScheduleEntity(
                date = tomorrow,
                lessonNumber = 2,
                subject = "Английский язык",
                teacher = "Смирнова Е.Е.",
                room = "204",
                startTime = "10:00",
                endTime = "11:30"
            )
        )
        dao.insertAll(lessons)
    }

    private suspend fun seedMessages(db: AppDatabase) {
        val dao = db.eljurMessageDao()
        if (dao.count() > 0) return

        val today = todayString()
        val messages = listOf(
            EljurMessageEntity(
                fromName = "Классный руководитель",
                toName = "Ученик",
                subject = "Собрание родителей",
                body = "Уважаемые родители, собрание состоится в пятницу в 18:00 в кабинете 201.",
                date = today
            ),
            EljurMessageEntity(
                fromName = "Администрация школы",
                toName = "Ученик",
                subject = "График каникул",
                body = "Опубликован новый график каникул. Просьба ознакомиться в разделе «Документы».",
                date = today
            )
        )
        dao.insertAll(messages)
    }

    private fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
