package com.example.eljurtelegramdemo.eljur

import com.example.eljurtelegramdemo.data.AppDatabase
import com.example.eljurtelegramdemo.data.ScheduleDao
import com.example.eljurtelegramdemo.data.ScheduleEntity
import com.example.eljurtelegramdemo.data.TaskDao
import com.example.eljurtelegramdemo.data.TaskEntity
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object EljurConfig {
    //Для использования апи true
    const val USE_ELJUR_API: Boolean = false

    // Сюда ставится ключ ЭлЖур
    const val ELJUR_API_KEY: String = ""

    const val BASE_URL: String = "https://api.eljur.ru/"
}

interface EljurApiService {

    @GET("auth/login")
    suspend fun login(
        @Query("dev_key") devKey: String,
        @Query("login") login: String,
        @Query("password") password: String
    ): EljurAuthResponse

    @GET("schedule")
    suspend fun getSchedule(
        @Query("auth_token") authToken: String,
        @Query("date") date: String
    ): EljurScheduleResponse

    @GET("homework")
    suspend fun getHomework(
        @Query("auth_token") authToken: String,
        @Query("date") date: String
    ): EljurHomeworkResponse
}

data class EljurAuthResponse(
    val success: Boolean,
    val authToken: String? = null
)

data class EljurScheduleResponse(
    val lessons: List<EljurLessonDto>
)

data class EljurLessonDto(
    val number: Int,
    val subject: String,
    val teacher: String,
    val room: String,
    val startTime: String,
    val endTime: String
)

data class EljurHomeworkResponse(
    val tasks: List<EljurHomeworkDto>
)

data class EljurHomeworkDto(
    val subject: String,
    val title: String,
    val description: String,
    val dueDate: String
)


object EljurApiClient {
    val api: EljurApiService by lazy {
        Retrofit.Builder()
            .baseUrl(EljurConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EljurApiService::class.java)
    }
}

object EljurRemoteDataSource {
    suspend fun refreshScheduleForDate(db: AppDatabase, date: String) {
        if (!EljurConfig.USE_ELJUR_API || EljurConfig.ELJUR_API_KEY.isBlank()) return

        val api = EljurApiClient.api
        val response = api.getSchedule(
            authToken = EljurConfig.ELJUR_API_KEY,
            date = date
        )

        val entities = response.lessons.map { dto ->
            ScheduleEntity(
                date = date,
                lessonNumber = dto.number,
                subject = dto.subject,
                teacher = dto.teacher,
                room = dto.room,
                startTime = dto.startTime,
                endTime = dto.endTime
            )
        }

        val dao: ScheduleDao = db.scheduleDao()
        dao.clearAll()
        dao.insertAll(entities)
    }

    suspend fun refreshHomeworkForDate(db: AppDatabase, date: String) {
        if (!EljurConfig.USE_ELJUR_API || EljurConfig.ELJUR_API_KEY.isBlank()) return

        val api = EljurApiClient.api
        val response = api.getHomework(
            authToken = EljurConfig.ELJUR_API_KEY,
            date = date
        )

        val entities = response.tasks.map { dto ->
            TaskEntity(
                subject = dto.subject,
                title = dto.title,
                description = dto.description,
                dueDate = dto.dueDate.ifBlank { date },
                isArchived = false
            )
        }

        val taskDao: TaskDao = db.taskDao()
        taskDao.insertAll(entities)
    }
}

object EljurAuthRemoteDataSource {

    suspend fun login(login: String, password: String): Boolean {
        if (!EljurConfig.USE_ELJUR_API || EljurConfig.ELJUR_API_KEY.isBlank()) {
            return false
        }

        return try {
            val api = EljurApiClient.api
            val response = api.login(
                devKey = EljurConfig.ELJUR_API_KEY,
                login = login,
                password = password
            )
            response.success
        } catch (e: Exception) {
            false
        }
    }
}
