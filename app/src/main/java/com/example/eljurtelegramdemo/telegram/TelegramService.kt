package com.example.eljurtelegramdemo.telegram

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.eljurtelegramdemo.BuildConfig
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean

object TelegramService {


    private const val TAG = "TelegramService"
    private var client: Client? = null
    private var appContext: Context? = null
    private val isAuthorized = AtomicBoolean(false)
    private var savedMessagesChatId: Long? = null

    enum class AuthStage { NONE, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, READY }

    private var customChannelUsername: String = ""

    fun setCustomChannel(username: String) {
        customChannelUsername = username.trim().removePrefix("@")
    }

    fun getCustomChannel(): String = customChannelUsername

    @Volatile
    private var authStage: AuthStage = AuthStage.NONE
    fun getAuthStage(): AuthStage = authStage

    @Volatile
    private var pendingPassword: String? = null

    fun setPendingPassword(password: String) {
        pendingPassword = password.takeIf { it.isNotBlank() }
    }

    private const val TELEGRAM_API_ID = BuildConfig.TELEGRAM_API_ID
    private const val TELEGRAM_API_HASH = BuildConfig.TELEGRAM_API_HASH

    data class SavedMessage(
        val id: Long,
        val text: String,
        val date: Int,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        if (client != null) {
            return
        }
        createClient()
    }

    private fun createClient() {
        val ctx = appContext ?: return
        Client.execute(TdApi.SetLogVerbosityLevel(1))

        val updatesHandler = Client.ResultHandler { obj ->
            when (obj) {
                is TdApi.UpdateAuthorizationState -> {
                    onAuthorizationStateUpdated(obj.authorizationState, ctx)
                }
                is TdApi.UpdateNewMessage -> {
                }
                else -> {
                }
            }
        }

        val updateExceptionHandler = Client.ExceptionHandler { e ->
        }

        val defaultExceptionHandler = Client.ExceptionHandler { e ->
        }

        client = Client.create(
            updatesHandler,
            updateExceptionHandler,
            defaultExceptionHandler
        )
    }

    private fun fetchSavedMessagesChat() {
        val c = client ?: return

        // Используем кастомный канал, если он задан
        val username = if (customChannelUsername.isNotEmpty()) {
            customChannelUsername
        } else {
            // Если не задан - читаем избранное
            ""
        }

        if (username.isNotEmpty()) {
            c.send(TdApi.SearchPublicChat(username)) { res ->
                if (res is TdApi.Chat) {
                    savedMessagesChatId = res.id
                } else {
                    // Если не нашли канал, возвращаемся к избранному
                    openSavedMessagesFallback(c)
                }
            }
        } else {
            openSavedMessagesFallback(c)
        }
    }

    private fun openSavedMessagesFallback(c: Client) {
        c.send(TdApi.GetMe()) { res ->
            if (res is TdApi.User) {
                c.send(TdApi.CreatePrivateChat(res.id, false)) { chatRes ->
                    if (chatRes is TdApi.Chat) {
                        savedMessagesChatId = chatRes.id
                    }
                }
            }
        }
    }

    private fun createTdlibParameters(context: Context): TdApi.SetTdlibParameters {
        val filesDir = context.filesDir.absolutePath
        return TdApi.SetTdlibParameters().apply {
            databaseDirectory = "$filesDir/tdlib"
            filesDirectory = "$filesDir/tdlib"
            databaseEncryptionKey = ByteArray(32) { 1 }
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = TELEGRAM_API_ID
            apiHash = TELEGRAM_API_HASH
            systemLanguageCode = "ru"
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = Build.VERSION.RELEASE ?: "Unknown"
            applicationVersion = "1.0"
            useTestDc = false
        }
    }

    private fun onAuthorizationStateUpdated(
        state: TdApi.AuthorizationState,
        context: Context
    ) {
        when (state.constructor) {

            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val params = createTdlibParameters(context)
                client?.send(params) { result ->
                }
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.WAIT_PHONE
            }

            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.WAIT_CODE
            }

            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.WAIT_PASSWORD

                val pwd = pendingPassword
                if (!pwd.isNullOrEmpty()) {
                    client?.send(TdApi.CheckAuthenticationPassword(pwd)) { result ->
                    }
                }
            }

            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                isAuthorized.set(true)
                authStage = AuthStage.READY
                pendingPassword = null

                fetchSavedMessagesChat()
            }

            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.NONE
            }

            TdApi.AuthorizationStateClosing.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.NONE
            }

            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                isAuthorized.set(false)
                authStage = AuthStage.NONE
                pendingPassword = null
                savedMessagesChatId = null
                client = null
                createClient()
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
        }
    }

    fun sendAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
        }
    }

    fun logout() {
        client?.send(TdApi.LogOut()) { result ->
        }
    }

    fun isAuthorized(): Boolean = isAuthorized.get()
    fun loadSavedMessages(
        limit: Int = 50,
        onResult: (List<SavedMessage>) -> Unit
    ) {
        val chatId = savedMessagesChatId
        val c = client

        if (c == null) {
            onMain { onResult(emptyList()) }
            return
        }

        if (chatId == null) {
            fetchSavedMessagesChat()
            onMain { onResult(emptyList()) }
            return
        }

        c.send(
            TdApi.GetChatHistory(
                chatId,
                0,
                0,
                limit,
                false
            )
        ) { res ->
            if (res is TdApi.Messages) {
                val list = res.messages.mapNotNull { msg ->
                    val text = when (val content = msg.content) {
                        is TdApi.MessageText -> content.text.text
                        else -> null
                    }
                    text?.let {
                        SavedMessage(
                            id = msg.id,
                            text = it,
                            date = msg.date
                        )
                    }
                }
                onMain { onResult(list) }
            } else {
                onMain { onResult(emptyList()) }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post { block() }
        }
    }
}
