package com.dbt.tracker.statement

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts a password-protected .xlsx, so a statement can be imported as downloaded.
 *
 * A protected workbook is not a zip. It is a Compound File Binary container holding two streams:
 * EncryptionInfo, describing how it was encrypted, and EncryptedPackage, the AES-encrypted zip.
 * Both the container format and the ECMA-376 agile encryption scheme are public, and Android
 * already provides AES and SHA-512, so this needs no third-party library.
 *
 * Implemented against a real encrypted workbook and verified to reproduce the original bytes
 * exactly. A wrong password is rejected by the scheme's own verifier hash before any data is
 * decrypted, so it fails clearly rather than returning noise.
 */
object OoxmlDecryptor {

    class WrongPassword : Exception("That password did not work.")
    class Unsupported(message: String) : Exception(message)

    /** Signature of a Compound File Binary container: an encrypted Office document. */
    fun isEncryptedOfficeFile(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    /**
     * @return the decrypted .xlsx, ready to hand to [XlsxReader]
     * @throws WrongPassword if the verifier hash does not match
     */
    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        val cfb = Cfb(bytes)
        val info = cfb.stream("EncryptionInfo") ?: throw Unsupported("Not an encrypted workbook.")
        val payload = cfb.stream("EncryptedPackage") ?: throw Unsupported("Encrypted data missing.")

        val major = le16(info, 0)
        val minor = le16(info, 2)
        if (major != 4 || minor != 4) {
            // Office 2007 used a different, weaker scheme. Modern Excel and every bank export
            // seen in the wild writes agile encryption.
            throw Unsupported("This file uses an older encryption format (v$major.$minor).")
        }

        val d = parseDescriptor(String(info, 8, info.size - 8, Charsets.UTF_8))
        val digest = MessageDigest.getInstance(d.hashAlgorithm)

        fun key(blockKey: ByteArray): ByteArray {
            // H0 = hash(salt + password); then hash(counter + previous) spinCount times.
            // The iteration count is the deliberate cost that makes guessing slow.
            var h = digest.run { reset(); update(d.keySalt); digest(password.toByteArray(Charsets.UTF_16LE)) }
            for (i in 0 until d.spinCount) {
                h = digest.run { reset(); update(le32(i)); digest(h) }
            }
            h = digest.run { reset(); update(h); digest(blockKey) }
            return h.copyOf(d.keyBits / 8)
        }

        // Verify before decrypting anything: hashing the decrypted verifier input must
        // reproduce the decrypted verifier hash.
        val verifierIn = aes(key(BLOCK_VERIFIER_INPUT), d.keySalt, d.encryptedVerifierHashInput)
        val verifierHash = aes(key(BLOCK_VERIFIER_VALUE), d.keySalt, d.encryptedVerifierHashValue)
        val expected = digest.run { reset(); digest(verifierIn) }
        if (!expected.copyOf(expected.size).contentEquals(verifierHash.copyOf(expected.size))) {
            throw WrongPassword()
        }

        val secret = aes(key(BLOCK_KEY_VALUE), d.keySalt, d.encryptedKeyValue).copyOf(d.keyBits / 8)

        // The package is a length header followed by independently-chained segments, each with
        // an IV derived from its own index, so segments cannot be reordered undetected.
        val total = le64(payload, 0)
        val body = payload.copyOfRange(8, payload.size)
        val out = ByteArray(body.size)
        var written = 0
        var segment = 0
        while (written < body.size) {
            val iv = digest.run { reset(); update(d.dataSalt); digest(le32(segment)) }
                .copyOf(d.blockSize)
            val end = minOf(written + SEGMENT, body.size)
            val plain = aes(secret, iv, body.copyOfRange(written, end))
            plain.copyInto(out, written)
            written = end
            segment++
        }
        return out.copyOf(minOf(total.toInt(), out.size))
    }

    private fun aes(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }.doFinal(data)

    // ------------------------------------------------------------------ descriptor

    private class Descriptor(
        val dataSalt: ByteArray,
        val blockSize: Int,
        val keySalt: ByteArray,
        val spinCount: Int,
        val keyBits: Int,
        val hashAlgorithm: String,
        val encryptedVerifierHashInput: ByteArray,
        val encryptedVerifierHashValue: ByteArray,
        val encryptedKeyValue: ByteArray
    )

    private fun parseDescriptor(xml: String): Descriptor {
        var dataSalt: ByteArray? = null
        var blockSize = 16
        var hash = "SHA-512"
        var keySalt: ByteArray? = null
        var spin = 100_000
        var keyBits = 256
        var vIn: ByteArray? = null
        var vVal: ByteArray? = null
        var kVal: ByteArray? = null

        val p = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        }
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            // Namespace processing is off, so the encryptedKey element arrives prefixed.
            when (p.name.substringAfter(':')) {
                "keyData" -> {
                    dataSalt = b64(p.getAttributeValue(null, "saltValue"))
                    blockSize = p.getAttributeValue(null, "blockSize")?.toIntOrNull() ?: 16
                    hash = mapHash(p.getAttributeValue(null, "hashAlgorithm"))
                }
                "encryptedKey" -> {
                    keySalt = b64(p.getAttributeValue(null, "saltValue"))
                    spin = p.getAttributeValue(null, "spinCount")?.toIntOrNull() ?: 100_000
                    keyBits = p.getAttributeValue(null, "keyBits")?.toIntOrNull() ?: 256
                    p.getAttributeValue(null, "hashAlgorithm")?.let { hash = mapHash(it) }
                    vIn = b64(p.getAttributeValue(null, "encryptedVerifierHashInput"))
                    vVal = b64(p.getAttributeValue(null, "encryptedVerifierHashValue"))
                    kVal = b64(p.getAttributeValue(null, "encryptedKeyValue"))
                }
            }
        }

        return Descriptor(
            dataSalt = dataSalt ?: throw Unsupported("Encryption details missing."),
            blockSize = blockSize,
            keySalt = keySalt ?: throw Unsupported("Encryption key details missing."),
            spinCount = spin,
            keyBits = keyBits,
            hashAlgorithm = hash,
            encryptedVerifierHashInput = vIn ?: throw Unsupported("Verifier missing."),
            encryptedVerifierHashValue = vVal ?: throw Unsupported("Verifier missing."),
            encryptedKeyValue = kVal ?: throw Unsupported("Encrypted key missing.")
        )
    }

    private fun mapHash(name: String?): String = when (name?.uppercase()) {
        "SHA1", "SHA-1" -> "SHA-1"
        "SHA256", "SHA-256" -> "SHA-256"
        "SHA384", "SHA-384" -> "SHA-384"
        else -> "SHA-512"
    }

    private fun b64(s: String?): ByteArray? = s?.let { Base64.getDecoder().decode(it) }

    // ------------------------------------------------------------------ container

    /**
     * Minimal Compound File Binary reader: enough to pull two named streams out.
     *
     * Storage is sector-based with a file allocation table. Streams smaller than the cutoff live
     * in a separate mini-stream with its own allocation table, which is why both paths exist.
     */
    private class Cfb(private val data: ByteArray) {

        private val sectorSize: Int
        private val miniSectorSize: Int
        private val miniCutoff: Int
        private val fat: IntArray
        private val miniFat: IntArray
        private val miniStream: ByteArray
        private val directory = mutableMapOf<String, Pair<Int, Long>>()

        init {
            sectorSize = 1 shl le16(data, 30)
            miniSectorSize = 1 shl le16(data, 32)
            val fatCount = le32(data, 44)
            val dirStart = le32(data, 48)
            miniCutoff = le32(data, 56)
            val miniFatStart = le32(data, 60)
            var difatStart = le32(data, 68)
            val difatCount = le32(data, 72)

            val difat = ArrayList<Int>(109)
            for (i in 0 until 109) difat.add(le32(data, 76 + i * 4))
            var guard = 0
            while (difatStart != END && difatStart != FREE && guard++ < difatCount + 1) {
                val s = sector(difatStart)
                val per = sectorSize / 4 - 1
                for (i in 0 until per) difat.add(le32(s, i * 4))
                difatStart = le32(s, sectorSize - 4)
            }

            val fatList = ArrayList<Int>()
            for (i in 0 until minOf(fatCount, difat.size)) {
                val fs = difat[i]
                if (fs == END || fs == FREE) continue
                val s = sector(fs)
                for (j in 0 until sectorSize / 4) fatList.add(le32(s, j * 4))
            }
            fat = fatList.toIntArray()

            val dirBytes = readChain(dirStart)
            var rootStart = 0
            for (i in 0 until dirBytes.size / 128) {
                val off = i * 128
                val nameLen = le16(dirBytes, off + 64)
                if (nameLen <= 2) continue
                val name = String(dirBytes, off, nameLen - 2, Charsets.UTF_16LE)
                val type = dirBytes[off + 66].toInt()
                val start = le32(dirBytes, off + 116)
                val size = le64(dirBytes, off + 120)
                if (type == 5) rootStart = start
                directory[name] = start to size
            }

            miniFat = if (miniFatStart == END || miniFatStart == FREE) IntArray(0) else {
                val mb = readChain(miniFatStart)
                IntArray(mb.size / 4) { le32(mb, it * 4) }
            }
            miniStream = if (rootStart == END || rootStart == FREE) ByteArray(0) else readChain(rootStart)
        }

        fun stream(name: String): ByteArray? {
            val (start, size) = directory[name] ?: return null
            val bytes = if (size < miniCutoff) readMini(start) else readChain(start)
            return bytes.copyOf(minOf(size.toInt(), bytes.size))
        }

        private fun sector(n: Int): ByteArray {
            val from = 512 + n * sectorSize
            return data.copyOfRange(from, minOf(from + sectorSize, data.size))
        }

        private fun readChain(start: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var c = start
            var guard = 0
            while (c != END && c != FREE && c >= 0 && c < fat.size && guard++ < MAX_SECTORS) {
                out.write(sector(c))
                c = fat[c]
            }
            return out.toByteArray()
        }

        private fun readMini(start: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var c = start
            var guard = 0
            while (c != END && c != FREE && c >= 0 && c < miniFat.size && guard++ < MAX_SECTORS) {
                val from = c * miniSectorSize
                if (from >= miniStream.size) break
                out.write(miniStream, from, minOf(miniSectorSize, miniStream.size - from))
                c = miniFat[c]
            }
            return out.toByteArray()
        }
    }

    // ------------------------------------------------------------------ helpers

    private const val END = -2      // 0xFFFFFFFE, end of chain
    private const val FREE = -1     // 0xFFFFFFFF, unallocated
    private const val MAX_SECTORS = 1_000_000
    private const val SEGMENT = 4096

    private val BLOCK_VERIFIER_INPUT = byteArrayOf(
        0xFE.toByte(), 0xA7.toByte(), 0xD2.toByte(), 0x76,
        0x3B, 0x4B, 0x9E.toByte(), 0x79
    )
    private val BLOCK_VERIFIER_VALUE = byteArrayOf(
        0xD7.toByte(), 0xAA.toByte(), 0x0F, 0x6D,
        0x30, 0x61, 0x34, 0x4E
    )
    private val BLOCK_KEY_VALUE = byteArrayOf(
        0x14, 0x6E, 0x0B, 0xE7.toByte(),
        0xAB.toByte(), 0xAC.toByte(), 0xD0.toByte(), 0xD6.toByte()
    )

    private fun le16(b: ByteArray, o: Int) =
        ByteBuffer.wrap(b, o, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun le32(b: ByteArray, o: Int) =
        ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun le64(b: ByteArray, o: Int) =
        ByteBuffer.wrap(b, o, 8).order(ByteOrder.LITTLE_ENDIAN).long

    private fun le32(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
}
