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
    version = 1,
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
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDefaultExercises(database.exerciseDao())
                    }
                }
            }

            suspend fun populateDefaultExercises(dao: ExerciseDao) {
                val defaults = listOf(
                    // 胸部 (Chest)
                    ExerciseEntity(name = "上斜卧推", targetMuscle = "胸部", secondaryMuscles = "三角肌前束", equipment = "哑铃/杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "卧推", targetMuscle = "胸部", secondaryMuscles = "肱三头肌, 三角肌前束", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "平板杠铃卧推", targetMuscle = "胸部", secondaryMuscles = "肱三头肌, 三角肌前束", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "30° 上斜哑铃卧推", targetMuscle = "胸部", secondaryMuscles = "三角肌前束", equipment = "哑铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "坐姿器械推胸", targetMuscle = "胸部", secondaryMuscles = "肱三头肌", equipment = "机械器械"),
                    ExerciseEntity(name = "双杠臂屈伸", targetMuscle = "胸部", secondaryMuscles = "肱三头肌", equipment = "自重"),
                    ExerciseEntity(name = "龙门架高位绳索夹胸", targetMuscle = "胸部", secondaryMuscles = "胸下束", equipment = "绳索"),

                    // 背部 (Back)
                    ExerciseEntity(name = "硬拉", targetMuscle = "背部", secondaryMuscles = "臀大肌, 腘绳肌, 竖脊肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "引体向上", targetMuscle = "背部", secondaryMuscles = "肱二头肌", equipment = "自重", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "划船", targetMuscle = "背部", secondaryMuscles = "大圆肌, 竖脊肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "标准引体向上", targetMuscle = "背部", secondaryMuscles = "肱二头肌", equipment = "自重", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "杠铃俯身划船", targetMuscle = "背部", secondaryMuscles = "大圆肌, 竖脊肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "宽握高位下拉", targetMuscle = "背部", secondaryMuscles = "肱二头肌", equipment = "绳索", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "坐姿宽握绳索划船", targetMuscle = "背部", secondaryMuscles = "菱形肌, 中下斜方肌", equipment = "绳索"),

                    // 腿部 (Legs)
                    ExerciseEntity(name = "深蹲", targetMuscle = "腿部", secondaryMuscles = "臀大肌, 核心", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "传统杠铃后深蹲", targetMuscle = "腿部", secondaryMuscles = "臀大肌, 核心", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "罗马尼亚硬拉 (RDL)", targetMuscle = "腿部", secondaryMuscles = "腘绳肌, 臀大肌, 竖脊肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "45° 倒蹬机腿举", targetMuscle = "腿部", secondaryMuscles = "股四头肌", equipment = "机械器械", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "坐姿腿屈伸", targetMuscle = "腿部", secondaryMuscles = "股直肌", equipment = "机械器械"),
                    ExerciseEntity(name = "俯卧腿弯举", targetMuscle = "腿部", secondaryMuscles = "腘绳肌", equipment = "机械器械"),

                    // 肩部 (Shoulders)
                    ExerciseEntity(name = "杠铃肩推", targetMuscle = "肩部", secondaryMuscles = "三角肌前束, 三头肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "侧平飞鸟", targetMuscle = "肩部", secondaryMuscles = "三角肌中束", equipment = "哑铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "后束飞鸟", targetMuscle = "肩部", secondaryMuscles = "三角肌后束", equipment = "哑铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "面拉", targetMuscle = "肩部", secondaryMuscles = "三角肌后束, 肩袖肌群", equipment = "绳索", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "站姿杠铃推举", targetMuscle = "肩部", secondaryMuscles = "三角肌前束, 三头肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "哑铃侧平举", targetMuscle = "肩部", secondaryMuscles = "三角肌中束", equipment = "哑铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "绳索面拉 (Face Pull)", targetMuscle = "肩部", secondaryMuscles = "三角肌后束, 肩袖肌群", equipment = "绳索"),

                    // 手臂 (Arms)
                    ExerciseEntity(name = "三头下压", targetMuscle = "手臂", secondaryMuscles = "肱三头肌", equipment = "绳索", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "二头弯举", targetMuscle = "手臂", secondaryMuscles = "肱二头肌", equipment = "杠铃/哑铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "直杆杠铃弯举", targetMuscle = "手臂", secondaryMuscles = "肱二头肌", equipment = "杠铃", personalRecordWeight = 0.0, personalRecordReps = 0),
                    ExerciseEntity(name = "哑铃对握锤式弯举", targetMuscle = "手臂", secondaryMuscles = "肱肌, 肱桡肌", equipment = "哑铃"),
                    ExerciseEntity(name = "绳索三头下压", targetMuscle = "手臂", secondaryMuscles = "肱三头肌外侧头", equipment = "绳索", personalRecordWeight = 0.0, personalRecordReps = 0),

                    // 核心 (Abs)
                    ExerciseEntity(name = "腹肌训练", targetMuscle = "核心", secondaryMuscles = "腹直肌, 腹斜肌", equipment = "自重", personalRecordWeight = 0.0, personalRecordReps = 0)
                )
                dao.insertAll(defaults)
            }
        }
    }
}
