package com.dbt.tracker.statement

import android.content.Context
import android.net.Uri
import com.dbt.tracker.data.Prefs
import com.dbt.tracker.data.Repo
import com.dbt.tracker.data.Source
import com.dbt.tracker.data.Txn
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Turns a downloaded bank statement into stored transactions.
 *
 * The statement is treated as the truth. Every row carries its own closing balance, so the
 * balance is read rather than inferred, and re-importing an overlapping period corrects whatever
 * was there before instead of adding to it.
 */
object StatementImporter {

    data class Outcome(
        val ok: Boolean,
        val imported: Int = 0,
        val replaced: Int = 0,
        val fromTs: Long = 0,
        val toTs: Long = 0,
        val closingBalance: Double? = null,
        val account: String = "",
        val warnings: List<String> = emptyList(),
        val error: String? = null,
        /** The file is encrypted and no usable password was supplied. */
        val needsPassword: Boolean = false,
        /** The statement had no balance column, so a starting figure must be supplied once. */
        val needsBalance: Boolean = false
    )

    private const val MAX_BYTES = 25 * 1024 * 1024

    fun import(context: Context, uri: Uri, password: String? = null): Outcome {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
                ?: return Outcome(false, error = "Could not open that file.")
        } catch (e: Exception) {
            return Outcome(false, error = "Could not read that file: ${e.message}")
        }

        if (bytes.isEmpty()) return Outcome(false, error = "That file is empty.")

        val parsed = try {
            decode(bytes, password)
        } catch (e: NeedsPassword) {
            return Outcome(false, needsPassword = true, error = "That file is password protected.")
        } catch (e: OoxmlDecryptor.WrongPassword) {
            return Outcome(false, needsPassword = true, error = e.message)
        } catch (e: OoxmlDecryptor.Unsupported) {
            return Outcome(false, error = e.message)
        } catch (e: XlsxReader.NotXlsx) {
            return Outcome(false, error = e.message)
        } catch (e: Exception) {
            return Outcome(false, error = "Could not make sense of that file: ${e.message}")
        }

        if (parsed.isEmpty) {
            return Outcome(
                false,
                warnings = parsed.warnings,
                error = "No transactions found in that file."
            )
        }

        return store(context, parsed)
    }

    // ------------------------------------------------------------------ decode

    private class NeedsPassword : Exception()

    /** Chooses a reader from the file's leading bytes rather than trusting its extension. */
    private fun decode(bytes: ByteArray, password: String?): StatementParser.Result {
        if (startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)) {
            return StatementParser.parseGrid(XlsxReader.read(bytes.inputStream()))
        }
        if (OoxmlDecryptor.isEncryptedOfficeFile(bytes)) {
            if (password.isNullOrBlank()) throw NeedsPassword()
            val plain = OoxmlDecryptor.decrypt(bytes, password)
            return StatementParser.parseGrid(XlsxReader.read(plain.inputStream()))
        }
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) {
            throw XlsxReader.NotXlsx(
                "PDF statements are not supported. Download the CSV or Excel version from " +
                    "net banking instead."
            )
        }
        return StatementParser.parseText(asText(bytes))
    }

    private fun startsWith(bytes: ByteArray, vararg sig: Int): Boolean {
        if (bytes.size < sig.size) return false
        return sig.indices.all { (bytes[it].toInt() and 0xFF) == sig[it] }
    }

    /** Statements are usually UTF-8 but occasionally Windows-encoded; both must survive. */
    private fun asText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        // U+FFFD means bytes that are not valid UTF-8, so fall back to a single-byte charset.
        return if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw Exception("file is too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ store

    private fun store(context: Context, parsed: StatementParser.Result): Outcome {
        val repo = Repo(context)
        val prefs = Prefs(context)

        val txns = parsed.rows.mapIndexed { i, row ->
            val decoded = Narration.decode(row.description, row.ref)
            val isCredit = row.credit != null
            val amount = row.credit ?: row.debit ?: 0.0

            // The handle is searched alongside the payee name, so "rapido.qr@ybl" identifies a
            // ride even when the name field holds nothing but a stranger's initials.
            val haystack = "${row.description} ${decoded.vpa}"

            Txn(
                // Statements date a transaction but do not time it. Spacing rows a second apart
                // preserves the order the bank listed them in, which matters for the running
                // balance: the last row of a day must remain last.
                ts = row.date + i * 1000L,
                amount = amount,
                isCredit = isCredit,
                merchant = decoded.merchant,
                category = repo.categoryFor(decoded.merchant, haystack, decoded.channel, isCredit),
                account = parsed.accountHint,
                balanceAfter = row.balance,
                refNo = decoded.ref.ifBlank { row.ref }.takeIf { it.isNotBlank() },
                channel = decoded.channel,
                source = Source.STATEMENT,
                raw = row.description
            )
        }.filter { it.amount > 0 }

        if (txns.isEmpty()) {
            return Outcome(false, warnings = parsed.warnings, error = "Every row had a zero amount.")
        }

        val from = txns.minOf { it.ts }
        val to = txns.maxOf { it.ts }
        val (replaced, imported) = repo.replaceRange(from, to, txns)

        // Remember which account this is, so the balance follows it rather than guessing.
        if (parsed.accountHint.isNotBlank() && prefs.primaryAccount.isBlank()) {
            prefs.primaryAccount = parsed.accountHint
        }

        return Outcome(
            ok = true,
            needsBalance = txns.none { it.balanceAfter != null },
            imported = imported,
            replaced = replaced,
            fromTs = from,
            toTs = to,
            closingBalance = txns.lastOrNull { it.balanceAfter != null }?.balanceAfter,
            account = parsed.accountHint,
            warnings = parsed.warnings
        )
    }
}
