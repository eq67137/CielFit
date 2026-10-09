package com.cielfit.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FoodItem): Long

    @Query("SELECT * FROM food_item WHERE externalId = :externalId LIMIT 1")
    suspend fun byExternalId(externalId: String): FoodItem?

    @Query("SELECT * FROM food_item WHERE id = :id")
    suspend fun byId(id: Long): FoodItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodItem>)

    @Query("SELECT * FROM food_item WHERE name LIKE '%' || :q || '%' ORDER BY name LIMIT 60")
    suspend fun search(q: String): List<FoodItem>
}

@Dao
interface MealDao {
    @Insert
    suspend fun add(entry: MealEntry): Long

    @Update
    suspend fun update(entry: MealEntry)

    @Query("DELETE FROM meal_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM meal_entry WHERE dateMillis BETWEEN :from AND :to ORDER BY dateMillis DESC, slot")
    fun entriesForDay(from: Long, to: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entry WHERE dateMillis BETWEEN :from AND :to ORDER BY dateMillis DESC, slot")
    suspend fun entriesForDayOnce(from: Long, to: Long): List<MealEntry>

    @Query("SELECT * FROM meal_entry WHERE dateMillis BETWEEN :from AND :to")
    suspend fun entriesInRange(from: Long, to: Long): List<MealEntry>
}

@Dao
interface WaterDao {
    @Insert
    suspend fun add(log: WaterLog): Long

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_log WHERE dateMillis BETWEEN :from AND :to")
    fun totalForDay(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_log WHERE dateMillis BETWEEN :from AND :to")
    suspend fun totalForDayOnce(from: Long, to: Long): Int
}

@Dao
interface BodyDao {
    @Insert
    suspend fun add(metric: BodyMetric): Long

    @Query("SELECT * FROM body_metric ORDER BY dateMillis DESC")
    fun all(): Flow<List<BodyMetric>>

    @Query("SELECT * FROM body_metric ORDER BY dateMillis ASC")
    suspend fun allAsc(): List<BodyMetric>

    @Query("SELECT * FROM body_metric ORDER BY dateMillis DESC LIMIT 1")
    fun latest(): Flow<BodyMetric?>
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    fun observe(key: String): Flow<String?>
}

@Dao
interface UsdaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: UsdaCache)

    @Query("SELECT * FROM usda_cache WHERE queryKey = :key LIMIT 1")
    suspend fun get(key: String): UsdaCache?
}

@Database(
    entities = [FoodItem::class, MealEntry::class, WaterLog::class,
                BodyMetric::class, Setting::class, UsdaCache::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun bodyDao(): BodyDao
    abstract fun settingDao(): SettingDao
    abstract fun usdaDao(): UsdaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "cielfit.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}