package com.baoji.fitness

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baoji.fitness.core.database.BaojiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0 硬门槛：v2 → v3 迁移必须保留真实训练史。
 *
 * 回放用的 v2 结构来自 `app/schemas/.../2.json`，其 identityHash 与设备上真实 v2 库
 * 完全一致（f29f4e36d7ce9761b0435dce979bd52f），所以这不是"照着代码猜"的 fixture，
 * 而是真库等价物。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BaojiDatabase::class.java
    )

    @Test
    fun migrate2To3_keepsAllTrainingHistory() {
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2(db) }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, BaojiDatabase.MIGRATION_2_3).use { db ->
            assertEquals("会话一条不能少", 2, count(db, "workout_sessions"))
            assertEquals("组数一条不能少", 31, count(db, "workout_sets"))
            assertEquals("动作库不应被重复播种", 11, count(db, "exercises"))

            // 旧字段内容原样保留
            db.query("SELECT title, notes FROM workout_sessions WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("DAY 1 · 推日 (胸 肩)", c.getString(0))
                assertEquals("", c.getString(1))
            }
            // 外键关系未被破坏
            db.query(
                "SELECT COUNT(*) FROM workout_sets s " +
                    "JOIN workout_sessions w ON s.sessionId = w.id " +
                    "JOIN exercises e ON s.exerciseId = e.id"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(31, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3_createsBodyMetricsWithOneRowPerDay() {
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2(db) }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, BaojiDatabase.MIGRATION_2_3).use { db ->
            assertEquals(0, count(db, "body_metrics"))

            db.execSQL("INSERT INTO body_metrics (date, weightKg, note) VALUES (1000, 73.5, '')")
            db.execSQL("INSERT INTO body_metrics (date, bodyFatPct, note) VALUES (2000, 15.2, '')")
            assertEquals(2, count(db, "body_metrics"))

            // 同一天第二条必须被唯一索引挡住（M3b 用 REPLACE 语义覆盖，不产生两条）
            try {
                db.execSQL("INSERT INTO body_metrics (date, weightKg, note) VALUES (1000, 74.0, '')")
                fail("date 唯一索引缺失：同一天插入了两条")
            } catch (expected: SQLiteConstraintException) {
                // 符合预期
            }
            assertEquals(2, count(db, "body_metrics"))
        }
    }

    /**
     * v3 → v4：身体数据加身高列。旧行 heightCm 为 NULL，且不能把旧的身体数据弄丢。
     */
    @Test
    fun migrate3To4_addsHeightAndKeepsExistingBodyMetrics() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO body_metrics (date, weightKg, bodyFatPct, chestCm, waistCm, armCm, note) " +
                    "VALUES (1000, 73.5, 15.2, 100.0, 80.0, 35.0, 'ok')"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, BaojiDatabase.MIGRATION_3_4).use { db ->
            assertEquals(1, count(db, "body_metrics"))
            db.query("SELECT weightKg, bodyFatPct, heightCm, waistCm FROM body_metrics WHERE date = 1000").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(73.5, c.getDouble(0), 0.001)
                assertEquals(15.2, c.getDouble(1), 0.001)
                assertTrue("旧行身高应为 NULL（未测量）", c.isNull(2))
                assertEquals(80.0, c.getDouble(3), 0.001)
            }
            // 新列可写
            db.execSQL("UPDATE body_metrics SET heightCm = 175.0 WHERE date = 1000")
            db.query("SELECT heightCm FROM body_metrics WHERE date = 1000").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(175.0, c.getDouble(0), 0.001)
            }
        }
    }

    /** v2 用户升级到 v4：必须能走 2→3→4 两跳，数据一条不少。 */
    @Test
    fun migrate2To4_chainsBothMigrations() {
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2(db) }

        helper.runMigrationsAndValidate(
            TEST_DB, 4, true, BaojiDatabase.MIGRATION_2_3, BaojiDatabase.MIGRATION_3_4
        ).use { db ->
            assertEquals(2, count(db, "workout_sessions"))
            assertEquals(31, count(db, "workout_sets"))
            assertEquals(0, count(db, "body_metrics"))
        }
    }

    /**
     * v4 → v5：新增 coach_reports 表。旧数据纹丝不动，(date, scope) 唯一索引要真的挡住重复。
     */
    @Test
    fun migrate4To5_addsCoachReportsWithUniqueDayScope() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL("INSERT INTO body_metrics (date, weightKg, heightCm, note) VALUES (1000, 73.5, 175.0, '')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, BaojiDatabase.MIGRATION_4_5).use { db ->
            assertEquals("旧身体数据不能丢", 1, count(db, "body_metrics"))
            assertEquals(0, count(db, "coach_reports"))

            db.execSQL(
                "INSERT INTO coach_reports (date, scope, content, source, generatedAt) " +
                    "VALUES (1000, 'TODAY', '## 今天做了什么\n练了。', 'LLM', 5)"
            )
            assertEquals(1, count(db, "coach_reports"))

            // 同一天同一范围必须被唯一索引挡住（应用层用 REPLACE 覆盖）
            try {
                db.execSQL(
                    "INSERT INTO coach_reports (date, scope, content, source, generatedAt) " +
                        "VALUES (1000, 'TODAY', '重复', 'LOCAL', 6)"
                )
                fail("(date, scope) 唯一索引缺失")
            } catch (expected: SQLiteConstraintException) {
                // 符合预期
            }

            // 但同一天的不同范围可以共存
            db.execSQL(
                "INSERT INTO coach_reports (date, scope, content, source, generatedAt) " +
                    "VALUES (1000, 'THIS_WEEK', '## 总体判断\n还行。', 'LOCAL', 7)"
            )
            assertEquals(2, count(db, "coach_reports"))
        }
    }

    /** v2 用户直升 v5：三跳迁移链必须通。 */
    @Test
    fun migrate2To5_chainsAllMigrations() {
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2(db) }

        helper.runMigrationsAndValidate(
            TEST_DB, 5, true,
            BaojiDatabase.MIGRATION_2_3, BaojiDatabase.MIGRATION_3_4, BaojiDatabase.MIGRATION_4_5
        ).use { db ->
            assertEquals(2, count(db, "workout_sessions"))
            assertEquals(31, count(db, "workout_sets"))
            assertEquals(0, count(db, "coach_reports"))
        }
    }

    /**
     * v5 → v6：砍掉 AI 私教，删除 coach_reports 表，其余数据一条不少。
     */
    @Test
    fun migrate5To6_dropsCoachReportsTable() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL("INSERT INTO body_metrics (date, weightKg, heightCm, note) VALUES (1000, 73.5, 175.0, '')")
            db.execSQL(
                "INSERT INTO coach_reports (date, scope, content, source, generatedAt) " +
                    "VALUES (1000, 'TODAY', '报告', 'LOCAL', 1)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, BaojiDatabase.MIGRATION_5_6).use { db ->
            assertEquals("身体数据必须保留", 1, count(db, "body_metrics"))
            // coach_reports 表已被 DROP
            db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='coach_reports'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    /** v2 用户直升 v6：全链路迁移通畅。 */
    @Test
    fun migrate2To6_chainsAllMigrations() {
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2(db) }

        helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            BaojiDatabase.MIGRATION_2_3, BaojiDatabase.MIGRATION_3_4, BaojiDatabase.MIGRATION_4_5, BaojiDatabase.MIGRATION_5_6
        ).use { db ->
            assertEquals(2, count(db, "workout_sessions"))
            assertEquals(31, count(db, "workout_sets"))
            assertEquals(11, count(db, "exercises"))
            assertEquals(0, count(db, "body_metrics"))
        }
    }

    /** 回放设备上真实的 v2 数据形态（11 动作 / 2 会话 / 31 组）。 */
    private fun seedV2(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO exercises (id, name, targetMuscle, secondaryMuscles, equipment, isCustom, trackWeight, personalRecordWeight, personalRecordReps) " +
                "VALUES (1, '卧推', '胸部', '肱三头肌, 三角肌前束', '杠铃', 0, 1, 80.0, 8)"
        )
        // 其余 10 个动作：迁移只关心「行数不变」，内容用同构行占位
        for (i in 2..11) {
            db.execSQL(
                "INSERT INTO exercises (id, name, targetMuscle, secondaryMuscles, equipment, isCustom, trackWeight, personalRecordWeight, personalRecordReps) " +
                    "VALUES ($i, '动作$i', '背部', '', '杠铃', 0, 1, 0.0, 0)"
            )
        }

        db.execSQL(
            "INSERT INTO workout_sessions (id, title, startTime, endTime, totalVolumeKg, status, notes) " +
                "VALUES (1, 'DAY 1 · 推日 (胸 肩)', 1789397807360, 1789399000000, 4820.0, 'COMPLETED', '')"
        )
        db.execSQL(
            "INSERT INTO workout_sessions (id, title, startTime, endTime, totalVolumeKg, status, notes) " +
                "VALUES (2, 'DAY 2 · 拉日 (背 二头)', 1789402300868, NULL, 0.0, 'ACTIVE', '')"
        )

        for (i in 1..31) {
            val sessionId = if (i <= 15) 1 else 2
            val exerciseId = (i % 6) + 1
            val done = if (sessionId == 1) 1 else 0
            db.execSQL(
                "INSERT INTO workout_sets (id, sessionId, exerciseId, setOrder, setType, weightKg, reps, rpe, isCompleted, completedAt) " +
                    "VALUES ($i, $sessionId, $exerciseId, ${(i - 1) % 4 + 1}, 'NORMAL', 60.0, 8, NULL, $done, " +
                    (if (done == 1) "1789398000000" else "NULL") + ")"
            )
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
