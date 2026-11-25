package de.breadcrumbz.app.data.share

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verschlüsselung für verschickte Reisen – nur mit Bordmitteln von Android.
 *
 * - Aus dem Passwort wird mit PBKDF2 (HMAC-SHA256, 210 000 Runden, zufälliges
 *   Salz) ein 256-Bit-Schlüssel abgeleitet.
 * - Die Daten werden in Stücken von 64 KB mit AES-256-GCM verschlüsselt. So
 *   passen auch große Reisen mit vielen Fotos, ohne alles in den Speicher zu
 *   laden. Jedes Stück ist gegen Veränderung geschützt; ein abgeschnittenes
 *   oder verändertes File wird erkannt.
 *
 * Aufbau der Datei:
 * `BCZENC01` | Runden (4 Byte) | Salz (16) | Nonce-Anfang (8) | Stücke …
 * Jedes Stück: Länge (4 Byte, oberstes Bit = letztes Stück) | Geheimtext + Tag.
 */
object ShareCrypto {
    val MAGIC: ByteArray = "BCZENC01".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val PREFIX_SIZE = 8
    private const val TAG_BITS = 128
    internal const val CHUNK_SIZE = 64 * 1024
    private const val FINAL_FLAG = 0x80000000.toInt()
    private val HEADER_SIZE = MAGIC.size + 4 + SALT_SIZE + PREFIX_SIZE

    fun isEncrypted(start: ByteArray): Boolean =
        start.size >= MAGIC.size && start.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun aad(header: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 5)
            .put(header)
            .putInt(counter)
            .put(if (last) 1.toByte() else 0.toByte())
            .array()

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    /** Schreibt verschlüsselt in [target]. [close] schreibt das letzte Stück. */
    class EncryptingOutputStream(
        private val target: OutputStream,
        password: String,
    ) : OutputStream() {
        private val header: ByteArray
        private val key: SecretKeySpec
        private val prefix = ByteArray(PREFIX_SIZE)
        private val buffer = ByteArray(CHUNK_SIZE)
        private var filled = 0
        private var counter = 0
        private var closed = false

        init {
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
            random.nextBytes(prefix)
            header = ByteBuffer.allocate(HEADER_SIZE)
                .put(MAGIC)
                .putInt(ITERATIONS)
                .put(salt)
                .put(prefix)
                .array()
            key = deriveKey(password, salt, ITERATIONS)
            target.write(header)
        }

        private fun emit(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            val sealed = cipher.doFinal(buffer, 0, filled)
            val length = if (last) sealed.size or FINAL_FLAG else sealed.size
            target.write(ByteBuffer.allocate(4).putInt(length).array())
            target.write(sealed)
            counter++
            filled = 0
        }

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("Bereits geschlossen")
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // Ein volles Stück erst rausschreiben, wenn noch mehr kommt –
                // so trägt immer das wirklich letzte Stück die Ende-Markierung.
                if (filled == CHUNK_SIZE) emit(last = false)
                val take = minOf(remaining, CHUNK_SIZE - filled)
                System.arraycopy(b, offset, buffer, filled, take)
                filled += take
                offset += take
                remaining -= take
            }
        }

        override fun flush() = target.flush()

        override fun close() {
            if (closed) return
            closed = true
            emit(last = true)
            target.flush()
            target.close()
        }
    }

    /** Falsches Passwort (oder die Datei wurde verändert). */
    class WrongPasswordException : IOException("Falsches Passwort")

    /** Liest entschlüsselt aus [source]. */
    class DecryptingInputStream(
        source: InputStream,
        password: String,
    ) : InputStream() {
        private val input = DataInputStream(source)
        private val header = ByteArray(HEADER_SIZE)
        private val key: SecretKeySpec
        private val prefix: ByteArray
        private var current = ByteArray(0)
        private var position = 0
        private var counter = 0
        private var finished = false

        init {
            input.readFully(header)
            if (!isEncrypted(header)) throw IOException("Keine verschlüsselte Reise")
            val buffer = ByteBuffer.wrap(header, MAGIC.size, HEADER_SIZE - MAGIC.size)
            val iterations = buffer.int
            if (iterations !in 10_000..5_000_000) throw IOException("Ungültige Datei")
            val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
            prefix = ByteArray(PREFIX_SIZE).also { buffer.get(it) }
            key = deriveKey(password, salt, iterations)
            // Das erste Stück sofort prüfen: so ist ein falsches Passwort gleich klar.
            nextChunk()
        }

        private fun nextChunk(): Boolean {
            if (finished) return false
            val raw = try {
                input.readInt()
            } catch (_: EOFException) {
                throw IOException("Die Datei ist unvollständig")
            }
            val last = raw and FINAL_FLAG != 0
            val length = raw and FINAL_FLAG.inv()
            if (length < TAG_BITS / 8 || length > CHUNK_SIZE + TAG_BITS / 8) {
                throw if (counter == 0) WrongPasswordException() else IOException("Ungültige Datei")
            }
            val sealed = ByteArray(length)
            try {
                input.readFully(sealed)
            } catch (_: EOFException) {
                throw IOException("Die Datei ist unvollständig")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            current = try {
                cipher.doFinal(sealed)
            } catch (_: AEADBadTagException) {
                throw if (counter == 0) WrongPasswordException() else IOException("Die Datei ist beschädigt")
            }
            position = 0
            counter++
            finished = last
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position >= current.size) {
                if (!nextChunk()) return -1
            }
            val take = minOf(len, current.size - position)
            System.arraycopy(current, position, b, off, take)
            position += take
            return take
        }

        override fun close() = input.close()
    }

    /** Nur für Tests: verschlüsselt einen kleinen Byte-Block komplett im Speicher. */
    internal fun encryptAll(data: ByteArray, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptingOutputStream(out, password).use { it.write(data) }
        return out.toByteArray()
    }
}
