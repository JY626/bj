package com.baoji.fitness.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
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
        WorkoutSetEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BaojiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: BaojiDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BaojiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BaojiDatabase::class.java,
                    "baoji_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    // v2: 动作库裁剪为个人实际训练的 11 个 + 新增 trackWeight 字段。
                    // 旧库全是开发期演示数据（无真实训练史），重建比写迁移便宜。
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            // 种子只走 onOpen + 空表检查这一条路径：
            // fallbackToDestructiveMigration 重建数据库时不会触发 onCreate 回调，
            // 在 onCreate 里播种会导致升级后动作库为空。onOpen 每次打开都跑，
            // COUNT 检查保证幂等，将来再 bump 版本也不会漏种。
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
