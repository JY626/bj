package com.baoji.fitness.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baoji.fitness.core.database.dao.BodyMetricDao
import com.baoji.fitness.core.database.dao.CoachReportDao
import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.CoachReportEntity
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
        BodyMetricEntity::class,
        CoachReportEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class BaojiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    /** M3b：身体数据。 */
    abstract fun bodyMetricDao(): BodyMetricDao

    /** 保存下来的私教分析报告（日历可回看）。 */
    abstract fun coachReportDao(): CoachReportDao

    companion object {
        @Volatile
        private var INSTANCE: BaojiDatabase? = null

        /**
         * v2 → v3：唯一的结构变更 = 新增 `body_metrics` 表（M3b 的身体数据）。
         * 列级变更本轮为零；M1 / M2 / M3a 全部不需要迁移。
         *
         * SQL 必须与 `app/schemas/.../3.json` 里 Room 生成的 createSql 逐字一致，
         * 否则运行时 schema 校验（identityHash）会直接抛异常。
         * 由 `MigrationTest` 覆盖。
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
         * v3 → v4：身体数据加身高列（算 BMI）。旧行 heightCm 为 NULL（未测），
         * 语义正确：老记录本就不知道身高。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `body_metrics` ADD COLUMN `heightCm` REAL")
            }
        }

        /**
         * v4 → v5：新增 `coach_reports` 表（保存的私教分析）。
         * SQL 必须与 `app/schemas/.../5.json` 逐字一致。
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

        fun getDatabase(context: Context, scope: CoroutineScope): BaojiDatabase {            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BaojiDatabase::class.java,
                    "baoji_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    // v3 起口径改变：M1 之后会积累真实训练史，M3b/M2 都要动 schema，
                    // 再清一次库等于把路线图目标全抹掉。所有版本升级一律走显式迁移。
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            // 种子只走 onOpen + 空表检查这一条路径：
            // 数据库重建时不会触发 onCreate 回调，在 onCreate 里播种会导致升级后动作库为空。
            // onOpen 每次打开都跑，COUNT 检查保证幂等，将来再 bump 版本也不会漏种。
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
                // 个人固定动作库（11 个），与 PresetTemplates 三日计划一一对应，改这里必须同步模板名
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
