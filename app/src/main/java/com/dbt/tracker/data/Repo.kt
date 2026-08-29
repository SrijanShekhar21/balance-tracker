package com.dbt.tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class Repo(context: Context) {

    private val db = Db.get(context)

    // ---------------------------------------------------------------- writes

    /**
     * Inserts a transaction unless it is already recorded.
     *
     * Two guards run. A UNIQUE index over the bank reference number catches the same message
     * being read twice, and [isLikelyDuplicate] catches the same payment being described twice
     * by different senders, which the reference number cannot.
     *
     * @return true if a new row was written.
     */
    fun insert(txn: Txn): Boolean {
        val key = txn.refNo?.takeIf { it.isNotBlank() }
            ?.let { "${if (txn.isCredit) "C" else "D"}:$it:${"%.2f".format(txn.amount)}" }

        // Runs even when a reference number is present. One UPI payment is announced by both
        // the bank and the wallet app, and the two messages carry different reference numbers
        // and different payee names -- so the reference index alone cannot catch the copy, and
        // every such payment would otherwise be counted twice.
        if (txn.source == Source.SMS && isLikelyDuplicate(txn)) return false

        val values = ContentValues().apply {
            put("ts", txn.ts)
            put("amount", txn.amount)
            put("is_credit", if (txn.isCredit) 1 else 0)
            put("merchant", txn.merchant)
            put("category", txn.category)
            put("account", txn.account)
            if (txn.balanceAfter != null) put("balance_after", txn.balanceAfter) else putNull("balance_after")
            put("ref_no", txn.refNo)
            put("channel", txn.channel)
            put("source", txn.source)
            put("raw", txn.raw)
            put("dedupe_key", key)
            put("inferred_from", txn.inferredFrom)
        }
        // insertWithOnConflict + CONFLICT_IGNORE returns -1 rather than throwing when the
        // UNIQUE dedupe index rejects the row.
        val id = db.writableDatabase.insertWithOnConflict(
            "txn", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        return id != -1L
    }

    /**
     * Treats the same amount moving the same way within three minutes as one transaction.
     *
     * Merchant and reference number are deliberately not compared. Two descriptions of a single
     * payment rarely agree on either, so matching on them is what allowed duplicates through.
     * The cost of this looseness is that two genuinely separate payments of an identical amount
     * within three minutes collapse into one; the benefit is that the balance stops drifting
     * downwards, which is the far more common and more damaging error.
     *
     * Manually entered cash is exempt, so it can be recorded alongside a similar card payment.
     */
    private fun isLikelyDuplicate(txn: Txn): Boolean {
        db.readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM txn
            WHERE amount = ? AND is_credit = ? AND source = ? AND ABS(ts - ?) <= 180000
            """.trimIndent(),
            arrayOf(
                txn.amount.toString(),
                (if (txn.isCredit) 1 else 0).toString(),
                Source.SMS,
                txn.ts.toString()
            )
        ).use { c -> return c.moveToFirst() && c.getInt(0) > 0 }
    }

    /**
     * Wipes recorded transactions so they can be re-read from the inbox.
     *
     * Learned merchant rules survive, since they represent decisions the user made rather than
     * data derived from messages. Needed after a parsing fix, because corrections only affect
     * what is read next -- rows already stored keep whatever the old logic concluded.
     */
    fun clearTransactions() {
        db.writableDatabase.execSQL("DELETE FROM txn")
        db.writableDatabase.execSQL("DELETE FROM signal")
    }

    fun delete(id: Long) {
        db.writableDatabase.delete("txn", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Recategorises one transaction. When [remember] is set, every past and future
     * transaction from the same merchant follows, so a correction only has to be made once.
     */
    fun setCategory(id: Long, category: String, remember: Boolean) {
        val w = db.writableDatabase
        w.execSQL("UPDATE txn SET category = ? WHERE id = ?", arrayOf(category, id.toString()))
        if (!remember) return

        val merchant = one("SELECT merchant FROM txn WHERE id = ?", arrayOf(id.toString())) {
            it.getString(0)
        } ?: return
        if (merchant.isBlank()) return

        w.execSQL(
            "INSERT OR REPLACE INTO merchant_rule(pattern, category) VALUES(?, ?)",
            arrayOf(merchant.lowercase().trim(), category)
        )
        w.execSQL(
            "UPDATE txn SET category = ? WHERE LOWER(TRIM(merchant)) = ?",
            arrayOf(category, merchant.lowercase().trim())
        )
    }

    /** Applies one category to many transactions at once, for the triage screen. */
    fun setCategoryBulk(ids: List<Long>, category: String) {
        if (ids.isEmpty()) return
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            ids.forEach {
                w.execSQL(
                    "UPDATE txn SET category = ?, inferred_from = NULL WHERE id = ?",
                    arrayOf(category, it.toString())
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /**
     * Spends the classifier could not place: no recognised merchant, and no signal to explain
     * them. These are what the triage screen asks the user about.
     */
    fun needsTriage(sinceTs: Long): List<Txn> = query(
        """
        SELECT * FROM txn
        WHERE is_credit = 0 AND ts >= ? AND category IN (?, ?)
        ORDER BY ts DESC, id DESC
        """.trimIndent(),
        arrayOf(sinceTs.toString(), Categories.OTHER, Categories.TRANSFER)
    )

    // ---------------------------------------------------------------- signals

    fun insertSignal(signal: Signal) {
        val values = ContentValues().apply {
            put("ts", signal.ts)
            put("category", signal.category)
            put("label", signal.label)
            put("raw", signal.raw)
        }
        db.writableDatabase.insertWithOnConflict(
            "signal", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    /**
     * Finds an activity that plausibly explains a payment made at [ts].
     *
     * The window is deliberately lopsided. A ride is paid for when it ends, so its OTP arrives
     * well before the debit; an order is paid for as it is placed, so its confirmation lands
     * alongside. Reaching two hours back but only fifteen minutes forward matches both without
     * letting an unrelated later activity claim the payment.
     *
     * @return the nearest signal, or null when nothing explains the payment
     */
    fun findSignal(ts: Long): Signal? = one(
        """
        SELECT ts, category, label FROM signal
        WHERE ts BETWEEN ? AND ?
        ORDER BY ABS(ts - ?) ASC LIMIT 1
        """.trimIndent(),
        arrayOf(
            (ts - 120 * 60_000L).toString(),
            (ts + 15 * 60_000L).toString(),
            ts.toString()
        )
    ) { Signal(it.getLong(0), it.getString(1), it.getString(2)) }

    /** Category for a new transaction: a learned rule wins over the keyword classifier. */
    fun categoryFor(merchant: String, raw: String, channel: String, isCredit: Boolean): String {
        val rule = one(
            "SELECT category FROM merchant_rule WHERE pattern = ?",
            arrayOf(merchant.lowercase().trim())
        ) { it.getString(0) }
        return rule ?: Categories.classify(merchant, raw, channel, isCredit)
    }

    // ---------------------------------------------------------------- reads

    fun between(fromTs: Long, toTs: Long): List<Txn> = query(
        "SELECT * FROM txn WHERE ts >= ? AND ts < ? ORDER BY ts DESC, id DESC",
        arrayOf(fromTs.toString(), toTs.toString())
    )

    fun recent(limit: Int = 300): List<Txn> = query(
        "SELECT * FROM txn ORDER BY ts DESC, id DESC LIMIT ?",
        arrayOf(limit.toString())
    )

    fun count(): Int = one("SELECT COUNT(*) FROM txn", emptyArray()) { it.getInt(0) } ?: 0

    fun firstTs(): Long? = one("SELECT MIN(ts) FROM txn", emptyArray()) {
        if (it.isNull(0)) null else it.getLong(0)
    }

    /** Earliest sighting of a merchant, used to flag first-time high-value spends. */
    fun merchantFirstSeen(merchant: String): Long? = one(
        "SELECT MIN(ts) FROM txn WHERE LOWER(TRIM(merchant)) = ?",
        arrayOf(merchant.lowercase().trim())
    ) { if (it.isNull(0)) null else it.getLong(0) }

    // ---------------------------------------------------------------- balance

    /**
     * Current balance, preferring ground truth over arithmetic.
     *
     * SBI stamps "Avl Bal" onto many of its alerts. The most recent such figure is taken as
     * an anchor and only the transactions recorded after it are applied on top, so the number
     * self-corrects on every balance-bearing SMS instead of drifting.
     *
     * Falls back to the opening balance the user entered during setup. Returns null when
     * neither exists, which the UI renders as "unknown" rather than a misleading zero.
     *
     * @return balance paired with whether it had to be derived rather than read from the bank.
     */
    fun currentBalance(prefs: Prefs): Pair<Double, Boolean>? {
        val account = primaryAccount(prefs)

        val anchor = one(
            """
            SELECT ts, id, balance_after FROM txn
            WHERE balance_after IS NOT NULL AND channel <> ?
              AND (? = '' OR account = ?)
            ORDER BY ts DESC, id DESC LIMIT 1
            """.trimIndent(),
            arrayOf(Channel.CREDIT_CARD, account, account)
        ) { Triple(it.getLong(0), it.getLong(1), it.getDouble(2)) }

        if (anchor != null) {
            val (ts, id, bal) = anchor
            val delta = one(
                """
                SELECT SUM(CASE WHEN is_credit = 1 THEN amount ELSE -amount END)
                FROM txn
                WHERE (ts > ? OR (ts = ? AND id > ?))
                  AND channel <> ?
                  AND (? = '' OR account = ? OR account = '')
                """.trimIndent(),
                arrayOf(
                    ts.toString(), ts.toString(), id.toString(),
                    Channel.CREDIT_CARD, account, account
                )
            ) { if (it.isNull(0)) 0.0 else it.getDouble(0) } ?: 0.0
            // Estimated only when transactions have landed since the bank last told us.
            return (bal + delta) to (delta != 0.0)
        }

        val opening = prefs.openingBalance ?: return null
        val delta = one(
            """
            SELECT SUM(CASE WHEN is_credit = 1 THEN amount ELSE -amount END)
            FROM txn WHERE ts >= ?
            """.trimIndent(),
            arrayOf(prefs.openingBalanceTs.toString())
        ) { if (it.isNull(0)) 0.0 else it.getDouble(0) } ?: 0.0
        return (opening + delta) to true
    }

    /**
     * The account the balance should follow: the user's choice, or failing that whichever
     * account appears in the most transactions. Returns blank when no account is identifiable,
     * in which case the balance queries fall back to considering everything.
     */
    fun primaryAccount(prefs: Prefs): String {
        prefs.primaryAccount.takeIf { it.isNotBlank() }?.let { return it }
        return one(
            """
            SELECT account FROM txn
            WHERE account <> '' AND channel <> ?
            GROUP BY account ORDER BY COUNT(*) DESC LIMIT 1
            """.trimIndent(),
            arrayOf(Channel.CREDIT_CARD)
        ) { it.getString(0) } ?: ""
    }

    /** Every account the app has seen, so the user can confirm it is tracking the right one. */
    fun accountsSeen(): List<AccountStat> {
        val out = ArrayList<AccountStat>()
        db.readableDatabase.rawQuery(
            """
            SELECT account, channel, COUNT(*),
                   SUM(CASE WHEN balance_after IS NOT NULL THEN 1 ELSE 0 END)
            FROM txn WHERE account <> ''
            GROUP BY account, channel ORDER BY COUNT(*) DESC
            """.trimIndent(),
            emptyArray()
        ).use { c ->
            while (c.moveToNext()) {
                out.add(AccountStat(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)))
            }
        }
        return out
    }

    /** Everything behind the balance figure, so a wrong number can be explained rather than guessed at. */
    fun balanceDiagnostics(prefs: Prefs): BalanceDiag {
        val account = primaryAccount(prefs)
        val anchor = one(
            """
            SELECT ts, balance_after, account FROM txn
            WHERE balance_after IS NOT NULL AND channel <> ?
              AND (? = '' OR account = ?)
            ORDER BY ts DESC, id DESC LIMIT 1
            """.trimIndent(),
            arrayOf(Channel.CREDIT_CARD, account, account)
        ) { Triple(it.getLong(0), it.getDouble(1), it.getString(2) ?: "") }

        val since = anchor?.first ?: 0L
        val counted = one(
            """
            SELECT COUNT(*), SUM(CASE WHEN is_credit = 1 THEN amount ELSE -amount END)
            FROM txn WHERE ts > ? AND channel <> ? AND (? = '' OR account = ? OR account = '')
            """.trimIndent(),
            arrayOf(since.toString(), Channel.CREDIT_CARD, account, account)
        ) { it.getInt(0) to (if (it.isNull(1)) 0.0 else it.getDouble(1)) } ?: (0 to 0.0)

        return BalanceDiag(
            primaryAccount = account,
            accountExplicitlyChosen = prefs.primaryAccount.isNotBlank(),
            anchorTs = anchor?.first,
            anchorBalance = anchor?.second,
            anchorAccount = anchor?.third,
            txnsSinceAnchor = counted.first,
            netSinceAnchor = counted.second,
            accounts = accountsSeen()
        )
    }

    // ---------------------------------------------------------------- plumbing

    private fun query(sql: String, args: Array<String>): List<Txn> {
        val out = ArrayList<Txn>()
        db.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(c.toTxn())
        }
        return out
    }

    private fun <T> one(sql: String, args: Array<String>, map: (Cursor) -> T?): T? {
        db.readableDatabase.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) map(c) else null
        }
    }

    private fun Cursor.toTxn() = Txn(
        id = getLong(getColumnIndexOrThrow("id")),
        ts = getLong(getColumnIndexOrThrow("ts")),
        amount = getDouble(getColumnIndexOrThrow("amount")),
        isCredit = getInt(getColumnIndexOrThrow("is_credit")) == 1,
        merchant = getString(getColumnIndexOrThrow("merchant")) ?: "",
        category = getString(getColumnIndexOrThrow("category")) ?: Categories.OTHER,
        account = getString(getColumnIndexOrThrow("account")) ?: "",
        balanceAfter = getColumnIndexOrThrow("balance_after").let { if (isNull(it)) null else getDouble(it) },
        refNo = getString(getColumnIndexOrThrow("ref_no")),
        channel = getString(getColumnIndexOrThrow("channel")) ?: Channel.OTHER,
        source = getString(getColumnIndexOrThrow("source")) ?: Source.SMS,
        raw = getString(getColumnIndexOrThrow("raw")) ?: "",
        inferredFrom = getString(getColumnIndexOrThrow("inferred_from"))
    )
}
