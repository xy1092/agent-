package dev.agentone.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.agentone.core.model.AgentRun
import dev.agentone.core.model.AgentStepLog
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.model.ChatSession
import dev.agentone.core.model.MemoryEntry
import dev.agentone.core.model.MountedWorkspace
import dev.agentone.core.model.ProviderConfig
import dev.agentone.core.model.ReminderEntity

@Database(
    entities = [
        ChatSession::class,
        ChatMessage::class,
        AgentRun::class,
        AgentStepLog::class,
        ProviderConfig::class,
        MemoryEntry::class,
        ReminderEntity::class,
        MountedWorkspace::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AgentOneDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun agentStepLogDao(): AgentStepLogDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        @Volatile
        private var INSTANCE: AgentOneDatabase? = null

        fun getInstance(context: Context): AgentOneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentOneDatabase::class.java,
                    "agentone.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
