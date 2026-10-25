package hu.vagyonnaplo.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Password-derived AES-256-GCM encrypted document; no plaintext data stored on disk.
data class Person(val id: String, val name: String)
data class Account(val id: String, val personId: String, val name: String, val icon: String, val color: Int, val active: Boolean = true)
data class Entry(val accountId: String, val value: Long)
data class Snapshot(val id: String, val personId: String, val timestamp: Long, val note: String, val entries: List<Entry>)
data class Ledger(val people: List<Person> = emptyList(), val accounts: List<Account> = emptyList(), val snapshots: List<Snapshot> = emptyList())

object Vault {
    private const val ITERATIONS = 240_000
    private val random = SecureRandom()
    private fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)
    private fun file(ctx: Context) = File(ctx.filesDir, "vault.dat")
    fun exists(ctx: Context) = file(ctx).exists()
    fun encryptedBytes(ctx: Context): ByteArray = file(ctx).readBytes()
    fun restore(ctx: Context, data: ByteArray, password: String): Ledger {
        val ledger = decrypt(data, password) // validate before overwriting existing vault
        val tmp = File(ctx.filesDir, "vault.tmp")
        tmp.writeBytes(data)
        check(tmp.renameTo(file(ctx)) || run { tmp.copyTo(file(ctx), overwrite = true); tmp.delete(); true })
        return ledger
    }
    fun save(ctx: Context, ledger: Ledger, password: String) {
        val data = encrypt(serialize(ledger).toByteArray(Charsets.UTF_8), password)
        val tmp = File(ctx.filesDir, "vault.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file(ctx))) { tmp.copyTo(file(ctx), overwrite = true); tmp.delete() }
    }
    fun load(ctx: Context, password: String): Ledger = decrypt(file(ctx).readBytes(), password)
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = bytes(16); val iv = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val payload = cipher.doFinal(data)
        return byteArrayOf(86, 78, 1) + salt + iv + payload
    }
    private fun decrypt(data: ByteArray, password: String): Ledger {
        require(data.size > 32 && data[0] == 86.toByte() && data[1] == 78.toByte() && data[2] == 1.toByte()) { "Ismeretlen mentésformátum" }
        val salt = data.copyOfRange(3, 19); val iv = data.copyOfRange(19, 31)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return parse(String(cipher.doFinal(data.copyOfRange(31, data.size)), Charsets.UTF_8))
    }
    private fun serialize(l: Ledger): String = JSONObject().apply {
        put("people", JSONArray().apply { l.people.forEach { put(JSONObject().put("id",it.id).put("name",it.name)) } })
        put("accounts", JSONArray().apply { l.accounts.forEach { put(JSONObject().put("id",it.id).put("person",it.personId).put("name",it.name).put("icon",it.icon).put("color",it.color).put("active",it.active)) } })
        put("snapshots", JSONArray().apply { l.snapshots.forEach { s -> put(JSONObject().put("id",s.id).put("person",s.personId).put("time",s.timestamp).put("note",s.note).put("entries",JSONArray().apply { s.entries.forEach { put(JSONObject().put("account",it.accountId).put("value",it.value)) } })) } })
    }.toString()
    private fun parse(raw: String): Ledger {
        val root = JSONObject(raw)
        fun <T> JSONArray.mapObjects(fn: (JSONObject)->T): List<T> = (0 until length()).map { fn(getJSONObject(it)) }
        return Ledger(
            root.getJSONArray("people").mapObjects { Person(it.getString("id"),it.getString("name")) },
            root.getJSONArray("accounts").mapObjects { Account(it.getString("id"),it.getString("person"),it.getString("name"),it.optString("icon","wallet"),it.optInt("color",0),it.optBoolean("active",true)) },
            root.getJSONArray("snapshots").mapObjects { s -> Snapshot(s.getString("id"),s.getString("person"),s.getLong("time"),s.optString("note",""),s.getJSONArray("entries").mapObjects { Entry(it.getString("account"),it.getLong("value")) }) }
        )
    }
}
