package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

internal const val VOICE_TAKES_TABLE = "voice_takes"

internal enum class VoiceTakeState { RECORDING, SAVED, TRANSCRIBING, READY, FAILED }

enum class VoiceOutputMode {
    CLEANED,
    VERBATIM;

    fun select(cleaned: String, verbatim: String?): String = when (this) {
        CLEANED -> cleaned
        VERBATIM -> verbatim?.takeIf { it.isNotBlank() } ?: cleaned
    }
}

@Entity(tableName = VOICE_TAKES_TABLE)
data class VoiceTake(
    @PrimaryKey val id: String,
    val audioPath: String?,
    val capturedAtMs: Long,
    val durationMs: Long,
    val state: String,
    val provider: String,
    val cleanedText: String,
    val verbatimText: String?,
    val rawText: String,
    val error: String?,
) {
    @get:Ignore
    internal val takeState: VoiceTakeState
        get() = VoiceTakeState.entries.firstOrNull { it.name == state } ?: VoiceTakeState.FAILED

    fun textFor(mode: VoiceOutputMode): String = mode.select(cleanedText, verbatimText)
}

@Dao
internal interface VoiceTakeDao {
    @Query("SELECT * FROM $VOICE_TAKES_TABLE ORDER BY capturedAtMs DESC")
    fun observeAll(): Flow<List<VoiceTake>>

    @Query("SELECT * FROM $VOICE_TAKES_TABLE WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VoiceTake?

    @Query("SELECT * FROM $VOICE_TAKES_TABLE WHERE audioPath = :audioPath LIMIT 1")
    suspend fun getByAudioPath(audioPath: String): VoiceTake?

    @Query("SELECT * FROM $VOICE_TAKES_TABLE WHERE cleanedText = :text LIMIT 1")
    suspend fun findByCleanedText(text: String): VoiceTake?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(take: VoiceTake)

    @Query("DELETE FROM $VOICE_TAKES_TABLE WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [VoiceTake::class], version = 1)
internal abstract class VoiceDatabase : RoomDatabase() {
    abstract fun voiceTakeDao(): VoiceTakeDao

    companion object {
        fun new(context: Context): VoiceDatabase = Room.databaseBuilder(
            context.applicationContext,
            VoiceDatabase::class.java,
            "voice_takes.db",
        ).build()
    }
}
