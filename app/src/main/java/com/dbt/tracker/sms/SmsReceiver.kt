package com.dbt.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo
import com.dbt.tracker.report.Notifications
import kotlin.concurrent.thread

/** Records transactions the moment the bank texts about them. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Long transaction alerts arrive split across several PDUs; joining them first
        // keeps the parser from seeing half a message.
        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val receivedAt = System.currentTimeMillis()

        val appContext = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                val stored = Ingest.handle(appContext, sender, body, receivedAt)
                if (stored && Prefs(appContext).liveAlerts) {
                    Repo(appContext).recent(1).firstOrNull()?.let {
                        Notifications.showLiveTxn(appContext, it)
                    }
                }
            } catch (_: Exception) {
                // A malformed message must never crash the receiver; it is simply skipped.
            } finally {
                pending.finish()
            }
        }
    }
}
