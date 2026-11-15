package vn.nero.remote.shared
import java.security.MessageDigest
import java.security.SecureRandom
object PairingCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    fun generate(length: Int = 14): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
    fun normalize(value: String): String = value.uppercase().filter { it in ALPHABET }
    fun isValid(value: String): Boolean = normalize(value).length >= 12
    fun channelName(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(value).toByteArray(Charsets.UTF_8))
        return "realtime:nero-" + digest.joinToString("") { "%02x".format(it) }
    }
}
