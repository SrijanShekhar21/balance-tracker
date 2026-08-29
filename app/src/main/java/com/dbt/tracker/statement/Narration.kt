package com.dbt.tracker.statement

import com.dbt.tracker.data.Channel
import java.util.Locale

/**
 * Pulls a payee out of a statement description.
 *
 * SBI encodes UPI payments as slash-separated fields:
 *
 *   TO TRANSFER-UPI/DR/522839472019/RAPIDO/YESB/rapido.qr@ybl/Payment from PhonePe
 *   BY TRANSFER-UPI/CR/411000111222/RAHUL KU/SBIN/rahul@oksbi/UPI
 *
 * which is strictly more than an SMS carries: the payee name *and* their UPI handle. The handle
 * matters, because a name like "SURESH KUMAR" identifies nothing while the handle it came from
 * often still names the platform behind the payment.
 */
object Narration {

    data class Decoded(
        val merchant: String,
        /** Payee UPI handle when present. Fed to the classifier alongside the name. */
        val vpa: String,
        val channel: String,
        val ref: String
    )

    private val NOISE = Regex(
        """(?i)^(to|by)\s+transfer\s*[-:]?\s*|^(to|by)\s+|^transfer\s*[-:]?\s*|^upi\s*[-/]\s*"""
    )

    fun decode(description: String, fallbackRef: String): Decoded {
        val raw = description.replace(Regex("""\s+"""), " ").trim()
        val lower = raw.lowercase(Locale.ROOT)

        if (lower.contains("upi/") || lower.startsWith("upi")) decodeUpi(raw)?.let { return it }

        val channel = channelOf(lower)
        return Decoded(
            merchant = cleanName(NOISE.replace(raw, "").substringBefore("--").trim())
                .ifBlank { defaultName(channel) },
            vpa = "",
            channel = channel,
            ref = fallbackRef
        )
    }

    /**
     * Field positions are consistent across SBI's UPI narrations, but truncation is common, so
     * each part is taken defensively and the handle is located by its "@" rather than by index.
     */
    private fun decodeUpi(raw: String): Decoded? {
        val parts = raw.split("/").map { it.trim() }
        if (parts.size < 4) return null

        val vpa = parts.firstOrNull { it.contains("@") && it.length in 4..60 }.orEmpty()

        // parts[0] is the "TO TRANSFER-UPI" prefix, [1] the direction, [2] the reference,
        // [3] the payee name.
        val name = parts.getOrNull(3).orEmpty()
        val ref = parts.getOrNull(2).orEmpty().filter { it.isDigit() }

        val merchant = cleanName(name).ifBlank {
            // Fall back to the handle's local part: "rapido.qr@ybl" -> "rapido.qr".
            cleanName(vpa.substringBefore("@"))
        }.ifBlank { "UPI payment" }

        return Decoded(merchant, vpa, Channel.UPI, ref)
    }

    private fun channelOf(lower: String): String = when {
        lower.contains("atm") || lower.contains("cash wdl") || lower.contains("nfs") -> Channel.ATM
        lower.contains("upi") -> Channel.UPI
        lower.contains("pos") || lower.contains("card") || lower.contains("ecom") -> Channel.CARD
        lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") ||
            lower.contains("inb") -> Channel.BANK
        lower.contains("chq") || lower.contains("cheque") -> Channel.OTHER
        else -> Channel.OTHER
    }

    private fun defaultName(channel: String) = when (channel) {
        Channel.ATM -> "ATM withdrawal"
        Channel.BANK -> "Bank transfer"
        else -> "Unknown"
    }

    /** Trims the codes and padding banks leave in payee fields, then softens shouted names. */
    private fun cleanName(input: String): String {
        var m = input.trim().trim('-', '.', ',', ':', ';', '*', '_')
        if (m.contains("@")) m = m.substringBefore("@")

        m = m.replace(Regex("""\d{8,}"""), " ")
            .replace(Regex("""[*_|\\]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (m.length < 2) return ""
        if (m.length > 38) m = m.take(38).trim()

        if (m == m.uppercase(Locale.ROOT) && m.any { it.isLetter() }) {
            m = m.lowercase(Locale.ROOT).split(" ").joinToString(" ") { w ->
                if (w.isEmpty()) w else w.replaceFirstChar { it.uppercase() }
            }
        }
        return m
    }
}
