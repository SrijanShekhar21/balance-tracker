package com.dbt.tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite, no ORM and no annotation processing. The schema is small enough that
 * hand-written SQL is clearer than generated code, and it keeps the Gradle build to
 * two plugins, which matters when the APK is compiled in CI rather than locally.
 */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE txn(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              ts INTEGER NOT NULL,
              amount REAL NOT NULL,
              is_credit INTEGER NOT NULL,
              merchant TEXT NOT NULL DEFAULT '',
              category TEXT NOT NULL DEFAULT '',
              account TEXT NOT NULL DEFAULT '',
              balance_after REAL,
              ref_no TEXT,
              channel TEXT NOT NULL DEFAULT 'Other',
              source TEXT NOT NULL DEFAULT 'SMS',
              raw TEXT NOT NULL DEFAULT '',
              dedupe_key TEXT,
              inferred_from TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_txn_ts ON txn(ts)")
        // SQLite allows repeated NULLs in a UNIQUE index, so transactions without a bank
        // reference number are simply not covered here; those are de-duplicated in code
        // using a short time window instead.
        db.execSQL("CREATE UNIQUE INDEX idx_txn_dedupe ON txn(dedupe_key)")

        db.execSQL(
            """
            CREATE TABLE merchant_rule(
              pattern TEXT PRIMARY KEY,
              category TEXT NOT NULL
            )
            """.trimIndent()
        )

        createSignalTable(db)
    }

    /**
     * Evidence gathered from non-bank SMS: a Rapido ride OTP, a Zepto order confirmation.
     * These carry no money, but they explain a payment to an otherwise unidentifiable payee.
     */
    private fun createSignalTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE signal(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              ts INTEGER NOT NULL,
              category TEXT NOT NULL,
              label TEXT NOT NULL,
              raw TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_signal_ts ON signal(ts)")
        // One signal per app per minute is plenty; blocks repeated inbox scans from piling up.
        db.execSQL("CREATE UNIQUE INDEX idx_signal_unique ON signal(label, ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE txn ADD COLUMN inferred_from TEXT")
            createSignalTable(db)
        }
        if (oldVersion < 3) {
            // "Investments" became "SIP". Categories are stored as text, so already-imported
            // rows keep the old label unless it is rewritten here.
            db.execSQL("UPDATE txn SET category = 'SIP' WHERE category = 'Investments'")
            db.execSQL("UPDATE merchant_rule SET category = 'SIP' WHERE category = 'Investments'")
        }
    }

    companion object {
        private const val NAME = "balance_tracker.db"
        private const val VERSION = 3

        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(context).also { instance = it }
        }
    }
}
