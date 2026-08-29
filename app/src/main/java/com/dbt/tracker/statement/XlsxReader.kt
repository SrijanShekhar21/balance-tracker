package com.dbt.tracker.statement

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a real .xlsx into a grid, with no third-party library.
 *
 * An .xlsx is a zip of XML, and Android already ships both a zip reader and an XML pull parser,
 * so the alternative -- Apache POI -- would add roughly fifteen megabytes and a set of desktop
 * Java dependencies that misbehave on Android, to do something the platform can already do.
 *
 * Only unencrypted workbooks can be read. A password-protected file is AES-encrypted inside a
 * different container entirely, and is reported as such rather than failing obscurely.
 */
object XlsxReader {

    class NotXlsx(message: String) : Exception(message)

    /** @return every cell as text, row by row, with blanks preserved so columns stay aligned. */
    fun read(input: InputStream): List<List<String>> {
        var sharedXml: ByteArray? = null
        var sheetXml: ByteArray? = null
        var sawEntries = false

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                sawEntries = true
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> sharedXml = zip.readBytes()
                    // The first worksheet is the statement; workbooks from banks have only one.
                    sheetXml == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheetXml = zip.readBytes()
                    name == "EncryptedPackage" || name == "EncryptionInfo" ->
                        throw NotXlsx("This spreadsheet is password protected.")
                }
                zip.closeEntry()
            }
        }

        if (!sawEntries) throw NotXlsx("Not a spreadsheet file.")
        val sheet = sheetXml ?: throw NotXlsx("No worksheet found inside the spreadsheet.")

        val shared = sharedXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheet, shared)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = newParser(bytes)
        // A shared string is one <si>, but its text can be split across several <t> runs
        // when part of the cell is styled differently, so runs are concatenated.
        val current = StringBuilder()
        var inItem = false
        var inText = false

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inItem && inText) current.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inText = false
                    "si" -> { out.add(current.toString()); inItem = false }
                }
            }
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val p = newParser(bytes)

        var row = mutableListOf<String>()
        var colIndex = -1
        var cellType: String? = null
        var value = StringBuilder()
        var inValue = false

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> { row = mutableListOf(); colIndex = -1 }
                    "c" -> {
                        cellType = p.getAttributeValue(null, "t")
                        // Cells are omitted entirely when empty, so the column letter in r="C5"
                        // is the only way to keep later columns aligned with the header.
                        val ref = p.getAttributeValue(null, "r")
                        val target = ref?.let { columnOf(it) } ?: (colIndex + 1)
                        while (colIndex < target - 1) { row.add(""); colIndex++ }
                        colIndex = target
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        row.add(
                            if (cellType == "s") shared.getOrElse(raw.toIntOrNull() ?: -1) { "" }
                            else raw
                        )
                    }
                    "row" -> rows.add(row)
                }
            }
        }
        return rows
    }

    private fun newParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    /** "BC12" -> 54. Converts the letter part of a cell reference to a zero-based column. */
    private fun columnOf(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }
}
