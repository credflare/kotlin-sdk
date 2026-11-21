package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID

internal class ContractServer: AutoCloseable {
    private val process: Process
    val root: String

    init {
        val repo = File("..").canonicalFile
        process = ProcessBuilder("node", File(repo, "scripts/contract-server.mjs").path, File(repo, "example/src/androidTest/assets").path)
            .apply { environment()["PORT"] = "0" }.redirectErrorStream(true).start()
        val port = process.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: error("The contract server did not start")
        root = "http://127.0.0.1:$port"
    }

    fun session(token: String, sessionId: UUID = UUID.randomUUID()) = VerificationSession(SessionDescriptor(sessionId, token, "$root/api/v1", "2030-01-01T00:00:00Z"))
    fun stats() = JSONObject(URL("$root/stats").readText())
    fun attempts(keys: Collection<UUID>): List<JSONObject> {
        val wanted = keys.map { it.toString() }.toSet()
        val all: JSONArray = stats().getJSONArray("attempts")
        return (0 until all.length()).map { all.getJSONObject(it) }.filter { it.getString("key") in wanted }
    }
    override fun close() { process.destroy() }

    companion object {
        fun fixture(name: String): JSONObject = JSONObject(File("../example/src/androidTest/assets/$name").readText())
    }
}
