package com.baoji.fitness.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baoji.fitness.core.database.dao.BodyMetricDao
import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        BodyMetricEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class BaojiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    /** 身体数据（体重、体脂率、围度等）。 */
    abstract fun bodyMetricDao(): BodyMetricDao

    companion object {
        @Volatile
        private var INSTANCE: BaojiDatabase? = null

        /**
         * v2 → v3：结构变更 = 新增 `body_metrics` 表。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_metrics` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`weightKg` REAL, " +
                        "`bodyFatPct` REAL, " +
                        "`chestCm` REAL, " +
                        "`waistCm` REAL, " +
                        "`armCm` REAL, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_metrics_date` " +
                        "ON `body_metrics` (`date`)"
                )
            }
        }

        /**
         * v3 → v4：身体数据加身高列（算 BMI）。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `body_metrics` ADD COLUMN `heightCm` REAL")
            }
        }

        /**
         * v4 → v5：历史曾引入 `coach_reports` 表。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coach_reports` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`generatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_coach_reports_date_scope` " +
                        "ON `coach_reports` (`date`, `scope`)"
                )
            }
        }

        /**
         * v5 → v6：移除 AI 私教功能，删除 `coach_reports` 表。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `coach_reports`")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): BaojiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BaojiDatabase::class.java,
                    "baoji_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        val dao = database.exerciseDao()
                        if (dao.getExerciseCount() == 0) {
                            populateDefaultExercises(dao)
                        }
                    }
                }
            }

            suspend fun populateDefaultExercises(dao: ExerciseDao) {
                // 个人固定动作库（11 个），与 PresetTemplates 三日计划一一对应
                val defaults = listOf(
                    // 胸部
                    ExerciseEntity(name = "卧推", targetMuscle = "胸部", secondaryMuscles = "肱三头肌, 三角肌前束", equipment = "杠铃"),
                    ExerciseEntity(name = "上斜卧推", targetMuscle = "胸部", secondaryMuscles = "上胸束, 三角肌前束", equipment = "杠铃"),
                    // 背部
                    ExerciseEntity(name = "硬拉", targetMuscle = "背部", secondaryMuscles = "臀大肌, 腘绳肌, 竖脊肌", equipment = "杠铃"),
                    ExerciseEntity(name = "高位下拉", targetMuscle = "背部", secondaryMuscles = "肱二头肌", equipment = "绳索"),
                    ExerciseEntity(name = "划船", targetMuscle = "背部", secondaryMuscles = "大圆肌, 竖脊肌", equipment = "杠铃"),
                    // 肩部
                    ExerciseEntity(name = "史密斯推肩", targetMuscle = "肩部", secondaryMuscles = "三角肌前束, 三头肌", equipment = "史密斯"),
                    ExerciseEntity(name = "侧平飞鸟", targetMuscle = "肩部", secondaryMuscles = "三角肌中束", equipment = "哑铃"),
                    ExerciseEntity(name = "面拉", targetMuscle = "肩部", secondaryMuscles = "三角肌后束, 肩袖肌群", equipment = "绳索"),
                    // 手臂
                    ExerciseEntity(name = "二头弯举", targetMuscle = "手臂", secondaryMuscles = "肱二头肌", equipment = "杠铃/哑铃"),
                    // 腿部
                    ExerciseEntity(name = "史密斯深蹲", targetMuscle = "腿部", secondaryMuscles = "臀大肌, 核心", equipment = "史密斯"),
                    // 核心 —— 仅记次数，不计重量/容量
                    ExerciseEntity(name = "悬垂举腿", targetMuscle = "核心", secondaryMuscles = "腹直肌", equipment = "自重", trackWeight = false)
                )
                dao.insertAll(defaults)
            }
        }
    }
}
