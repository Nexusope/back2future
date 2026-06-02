package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "users")
data class TemporalUser(
    @PrimaryKey val email: String,
    val name: String,
    val age: Int,
    val profession: String,
    val currentGoals: String,
    val biggestDream: String,
    val personalValues: String,
    val currentChallenges: String,
    val desiredFutureYear: String,
    val futureVersionDescription: String,
    val subscriptionTier: String = "Free", // "Free", "Premium", "Enterprise"
    val isLoggedIn: Boolean = false
)

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "Journal Entry", "Voice Log", "Achievement", "Text"
    val content: String,
    val timestamp: Long,
    val tags: String, // comma-separated strings
    val deviceSource: String = "Android Device"
)

@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: String, // e.g. "3 Months", "6 Months", "2028", "2031", "2036"
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "letters")
data class Letter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String, // "Incoming", "Outgoing"
    val title: String,
    val content: String,
    val isLocked: Boolean,
    val unlockDate: String, // Date string, e.g., "2026-12-01" or unlocked
    val timestamp: Long = System.currentTimeMillis(),
    val stamphash: String = "0x" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String, // Year or Target timeline (e.g. "6 Months")
    val role: String, // "user", "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reflections")
data class Reflection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rangeType: String, // "Weekly", "Monthly"
    val periodString: String, // e.g. "Week of June 1, 2026"
    val insights: String, // AI-generated analysis summary
    val timestamp: Long = System.currentTimeMillis()
)


// --- Room DAOs ---

@Dao
interface TemporalUserDao {
    @Query("SELECT * FROM users WHERE isLoggedIn = 1 LIMIT 1")
    fun getActiveUser(): Flow<TemporalUser?>

    @Query("SELECT * FROM users WHERE isLoggedIn = 1 LIMIT 1")
    suspend fun getActiveUserSync(): TemporalUser?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): TemporalUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: TemporalUser)

    @Query("UPDATE users SET isLoggedIn = 0")
    suspend fun logoutAllUsers()

    @Query("UPDATE users SET isLoggedIn = 1 WHERE email = :email")
    suspend fun loginUserByEmail(email: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getMemoriesFlow(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getMemoriesSync(): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)
}

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones ORDER BY year ASC, timestamp DESC")
    fun getMilestonesFlow(): Flow<List<Milestone>>

    @Query("SELECT * FROM milestones ORDER BY year ASC, timestamp DESC")
    suspend fun getMilestonesSync(): List<Milestone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: Milestone)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    @Query("UPDATE milestones SET isCompleted = :completed WHERE id = :id")
    suspend fun updateMilestoneCompletion(id: Long, completed: Boolean)
}

@Dao
interface LetterDao {
    @Query("SELECT * FROM letters ORDER BY timestamp DESC")
    fun getLettersFlow(): Flow<List<Letter>>

    @Query("SELECT * FROM letters ORDER BY timestamp DESC")
    suspend fun getLettersSync(): List<Letter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLetter(letter: Letter)

    @Query("UPDATE letters SET isLocked = 0 WHERE id = :id")
    suspend fun unlockLetter(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getChatMessagesFlow(conversationId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getChatMessagesSync(conversationId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clearChatHistory(conversationId: String)
}

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM reflections ORDER BY timestamp DESC")
    fun getReflectionsFlow(): Flow<List<Reflection>>

    @Query("SELECT * FROM reflections ORDER BY timestamp DESC")
    suspend fun getReflectionsSync(): List<Reflection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReflection(reflection: Reflection)
}


// --- Main Room Database ---

@Database(
    entities = [
        TemporalUser::class,
        Memory::class,
        Milestone::class,
        Letter::class,
        ChatMessage::class,
        Reflection::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TemporalDatabase : RoomDatabase() {
    abstract fun userDao(): TemporalUserDao
    abstract fun memoryDao(): MemoryDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun letterDao(): LetterDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun reflectionDao(): ReflectionDao

    companion object {
        @Volatile
        private var INSTANCE: TemporalDatabase? = null

        fun getDatabase(context: Context): TemporalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TemporalDatabase::class.java,
                    "temporal_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
