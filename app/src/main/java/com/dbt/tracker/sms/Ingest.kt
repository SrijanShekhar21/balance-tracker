package com.dbt.tracker.sms

import android.content.Context
import android.provider.Telephony
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo

/**
 * The single path by which an SMS becomes a stored transaction. Both the live broadcast
 * receiver and the historical inbox scan funnel through here so they cannot disagree about
 * parsing, categorisation or de-duplication.
 */
object Ingest {

    /** @return true when this message produced a genuinely new transaction row. */
    fun handle(context: Context, sender: String, body: String, receivedAt: Long): Boolean {
        val repo = Repo(context)

        // Checked before the bank-sender filter, because these come from Rapido, Zepto and the
        // like rather than from a bank. They record what you were doing, not money moving.
        SignalDetector.detect(sender, body, receivedAt)?.let {
            repo.insertSignal(it)
            return false
        }

        val prefs = Prefs(context)
        if (!prefs.includeAllSenders && !SmsParser.isSbi(sender)) return false
        if (!SmsParser.senderLooksFinancial(sender)) return false

        val parsed = SmsParser.parse(body, receivedAt) ?: return false

        var category = repo.categoryFor(parsed.merchant, body, parsed.channel, parsed.isCredit)
        var merchant = parsed.merchant
        var inferredFrom: String? = null

        // Only reached when the payee itself said nothing useful, which is exactly the case for
        // a Rapido rider's personal QR code. A recognised merchant is never overridden.
        if (!parsed.isCredit && (category == Categories.OTHER || category == Categories.TRANSFER)) {
            repo.findSignal(parsed.ts)?.let { signal ->
                category = signal.category
                // Reporting on fifteen different rider names is useless; what matters is that
                // the money went to Rapido. The original payee stays visible in the raw SMS.
                merchant = signal.label
                inferredFrom = signal.label
            }
        }

        return repo.insert(
            SmsParser.toTxn(parsed, category, body)
                .copy(merchant = merchant, inferredFrom = inferredFrom)
        )
    }

    /**
     * Replays the SMS inbox so the app is useful the moment it is installed rather than only
     * going forward. Re-running is safe: existing rows are rejected by the de-duplication in
     * [Repo.insert].
     *
     * @return number of new transactions recorded
     */
    fun backfill(context: Context, days: Int): Int {
        val since = System.currentTimeMillis() - days * 86_400_000L
        var added = 0

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return 0

        cursor.use { c ->
            val iAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndex(Telephony.Sms.BODY)
            val iDate = c.getColumnIndex(Telephony.Sms.DATE)
            if (iAddr < 0 || iBody < 0 || iDate < 0) return 0

            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                val date = c.getLong(iDate)
                // Oldest first, so a ride OTP is always recorded before the payment it explains,
                // and so the balance anchor ends up on the most recent message.
                if (handle(context, sender, body, date)) added++
            }
        }
        return added
    }
}
