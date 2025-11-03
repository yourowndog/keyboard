package dev.patrickgold.florisboard.diagnostics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.ArrayDeque

private const val MAX_LOG_LINES = 500

data class DiagnosticsStrings(
    @StringRes val shareAction: Int,
    @StringRes val saveAction: Int,
    @StringRes val shareTitle: Int,
    @StringRes val unavailableMessage: Int,
    @StringRes val exportSuccess: Int,
    @StringRes val exportSavedLegacy: Int,
    @StringRes val exportFailed: Int,
)

enum class DiagnosticsStream {
    WHISPER,
    THEME,
}

class DiagnosticsLogChannel internal constructor(
    private val tag: String,
    private val filePrefix: String,
    private val mask: (String) -> String = { it },
    internal val strings: DiagnosticsStrings,
) {
    private val buf = ArrayDeque<String>(MAX_LOG_LINES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val exportDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val cacheFileName = "$filePrefix-current.log"

    fun maskForUi(message: String): String = mask(message)

    fun log(ctx: Context, message: String) {
        val line = "[${timestamp()}] ${mask(message)}"
        android.util.Log.d(tag, line)
        synchronized(buf) {
            buf.addLast(line)
            if (buf.size > MAX_LOG_LINES) {
                buf.removeFirst()
            }
        }
        val file = File(ctx.cacheDir, cacheFileName)
        runCatching {
            file.appendText("$line\n")
        }
    }

    fun exportLogs(ctx: Context): Uri? {
        return if (Build.VERSION.SDK_INT >= 29) {
            exportViaMediaStore(ctx)
        } else {
            exportViaFileProvider(ctx)
        }
    }

    fun share(ctx: Context) {
        val uri = exportLogs(ctx)
        if (uri == null) {
            log(ctx, "Share logs requested but no log file available")
            Toast.makeText(ctx, strings.unavailableMessage, Toast.LENGTH_SHORT).show()
        } else {
            log(ctx, "Share logs requested")
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(share, ctx.getString(strings.shareTitle))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        }
    }

    fun save(ctx: Context) {
        val uri = exportLogs(ctx)
        if (uri == null) {
            log(ctx, "Save logs requested but export failed")
            Toast.makeText(ctx, strings.exportFailed, Toast.LENGTH_SHORT).show()
        } else {
            log(ctx, "Logs exported")
            val message = if (Build.VERSION.SDK_INT >= 29) {
                strings.exportSuccess
            } else {
                strings.exportSavedLegacy
            }
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportViaMediaStore(ctx: Context): Uri? = runCatching {
        val src = File(ctx.cacheDir, cacheFileName)
        if (!src.exists() || src.length() == 0L) return@runCatching null

        val resolver = ctx.contentResolver
        val name = "${filePrefix}-${exportDateFormat.format(Date())}.log"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/FlorisBoard")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        resolver.openOutputStream(uri, "w")!!.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
        uri
    }.getOrNull()

    private fun exportViaFileProvider(ctx: Context): Uri? = runCatching {
        val src = File(ctx.cacheDir, cacheFileName)
        if (!src.exists() || src.length() == 0L) return@runCatching null

        val day = exportDateFormat.format(Date())
        val name = "${filePrefix}-${day}.log"
        val dstDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return@runCatching null
        val dst = File(dstDir, name)
        src.copyTo(dst, overwrite = true)
        FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", dst)
    }.getOrNull()

    private fun timestamp(): String = synchronized(dateFormat) { dateFormat.format(Date()) }
}

object DiagnosticsCenter {
    private val whisperChannel = DiagnosticsLogChannel(
        tag = "Whisper",
        filePrefix = "whisper",
        mask = ::maskSecrets,
        strings = DiagnosticsStrings(
            shareAction = R.string.whisper_logs_share_action,
            saveAction = R.string.whisper_logs_save_action,
            shareTitle = R.string.whisper_logs_share_title,
            unavailableMessage = R.string.whisper_logs_unavailable,
            exportSuccess = R.string.whisper_logs_export_success,
            exportSavedLegacy = R.string.whisper_log_saved,
            exportFailed = R.string.whisper_logs_export_failed,
        ),
    )

    private val themeChannel = DiagnosticsLogChannel(
        tag = "ThemeDiag",
        filePrefix = "theme",
        mask = { it },
        strings = DiagnosticsStrings(
            shareAction = R.string.theme_logs_share_action,
            saveAction = R.string.whisper_logs_save_action,
            shareTitle = R.string.theme_logs_share_title,
            unavailableMessage = R.string.theme_logs_unavailable,
            exportSuccess = R.string.theme_logs_export_success,
            exportSavedLegacy = R.string.theme_logs_saved_legacy,
            exportFailed = R.string.theme_logs_export_failed,
        ),
    )

    fun channel(stream: DiagnosticsStream): DiagnosticsLogChannel = when (stream) {
        DiagnosticsStream.WHISPER -> whisperChannel
        DiagnosticsStream.THEME -> themeChannel
    }

    fun fromName(name: String?): DiagnosticsStream? = runCatching {
        if (name.isNullOrBlank()) null else DiagnosticsStream.valueOf(name)
    }.getOrNull()
}

object WhisperLogger {
    private val channel get() = DiagnosticsCenter.channel(DiagnosticsStream.WHISPER)

    fun maskForUi(message: String): String = channel.maskForUi(message)
    fun log(ctx: Context, message: String) = channel.log(ctx, message)
    fun exportLogs(ctx: Context): Uri? = channel.exportLogs(ctx)
    fun share(ctx: Context) = channel.share(ctx)
    fun save(ctx: Context) = channel.save(ctx)
    internal val strings get() = channel.strings
}

object ThemeLogger {
    private val channel get() = DiagnosticsCenter.channel(DiagnosticsStream.THEME)

    fun log(ctx: Context, message: String) = channel.log(ctx, message)
    fun exportLogs(ctx: Context): Uri? = channel.exportLogs(ctx)
    fun share(ctx: Context) = channel.share(ctx)
    fun save(ctx: Context) = channel.save(ctx)
    internal val strings get() = channel.strings
}

class DiagnosticsLogReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val stream = DiagnosticsCenter.fromName(intent.getStringExtra(EXTRA_STREAM))
            ?: DiagnosticsStream.WHISPER
        val channel = DiagnosticsCenter.channel(stream)
        when (intent.action) {
            ACTION_SHARE -> channel.share(ctx)
            ACTION_SAVE -> channel.save(ctx)
        }
    }

    companion object {
        const val ACTION_SHARE = "dev.patrickgold.florisboard.diagnostics.action.SHARE"
        const val ACTION_SAVE = "dev.patrickgold.florisboard.diagnostics.action.SAVE"
        const val EXTRA_STREAM = "dev.patrickgold.florisboard.diagnostics.extra.STREAM"

    }
}

private fun Intent.putStream(stream: DiagnosticsStream) = apply {
    putExtra(DiagnosticsLogReceiver.EXTRA_STREAM, stream.name)
}

object WhisperNotify {
    private const val CHANNEL_ID = "whisper_debug"
    private const val NOTIFICATION_ID = 1337

    fun showError(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val shareIntent = Intent(ctx, DiagnosticsLogReceiver::class.java).apply {
            action = DiagnosticsLogReceiver.ACTION_SHARE
            putStream(DiagnosticsStream.WHISPER)
        }
        val sharePi = PendingIntent.getBroadcast(
            ctx,
            1,
            shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val saveIntent = Intent(ctx, DiagnosticsLogReceiver::class.java).apply {
            action = DiagnosticsLogReceiver.ACTION_SAVE
            putStream(DiagnosticsStream.WHISPER)
        }
        val savePi = PendingIntent.getBroadcast(
            ctx,
            2,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val shareThemeIntent = Intent(ctx, DiagnosticsLogReceiver::class.java).apply {
            action = DiagnosticsLogReceiver.ACTION_SHARE
            putStream(DiagnosticsStream.THEME)
        }
        val shareThemePi = PendingIntent.getBroadcast(
            ctx,
            3,
            shareThemeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val masked = WhisperLogger.maskForUi(text)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(masked.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(masked.take(500)))
            .addAction(0, ctx.getString(WhisperLogger.strings.shareAction), sharePi)
            .addAction(0, ctx.getString(WhisperLogger.strings.saveAction), savePi)
            .addAction(0, ctx.getString(ThemeLogger.strings.shareAction), shareThemePi)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.whisper_logs_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }
}

@VisibleForTesting(otherwise = PROTECTED)
fun maskSecrets(s: String): String = s.replace(Regex("sk-[A-Za-z0-9]{10,}")) { match ->
    val token = match.value
    token.take(7) + "…" + token.takeLast(4)
}
