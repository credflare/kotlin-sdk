package net.credflare.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GestureEngineTest {
    private fun traces(): List<File> {
        val directory = File(requireNotNull(javaClass.classLoader!!.getResource("liveness-traces")) { "Trace resources are missing" }.toURI())
        return directory.listFiles { file -> file.name.endsWith(".json") }!!.sortedBy { it.name }
    }

    private fun sample(value: JSONObject): FaceSample {
        fun number(name: String) = if(value.isNull(name)) null else value.getDouble(name)
        val box = value.optJSONObject("box")?.let { FaceBox(it.getDouble("x"), it.getDouble("y"), it.getDouble("w"), it.getDouble("h")) }
        return FaceSample(value.getLong("t"), value.getInt("faces"), box, number("eye_open"), number("smile"), number("yaw"), number("pitch"))
    }

    private fun replay(trace: JSONObject): RunResult? {
        val run: EngineRun = if(trace.getString("gesture") == "position") PositionGate() else GestureRun(trace.getString("gesture"), trace.optString("provider", "default"))
        val samples = trace.getJSONArray("samples")
        var last: RunResult? = null
        for(index in 0 until samples.length()) {
            last = run.push(sample(samples.getJSONObject(index)))
            if(last.state != RunState.RUNNING) break
        }
        return last
    }

    @Test
    fun replaysEverySpecTrace() {
        val files = traces()
        assertTrue("Expected the sdk-spec liveness traces", files.size >= 30)
        for(file in files) {
            val trace = JSONObject(file.readText())
            val last = replay(trace)
            val outcome = when(last?.state) { RunState.PASS -> "pass"; RunState.TIMEOUT -> "timeout"; else -> "fail" }
            assertEquals(file.name, trace.getString("expect"), outcome)
            if(trace.has("expect_status")) assertEquals(file.name, trace.getString("expect_status"), last?.status?.name?.lowercase())
        }
    }

    @Test
    fun providerThresholdsOverrideDefaults() {
        assertEquals(0.7, GestureEngine.thresholds("mlkit").getValue("smile"), 0.0)
        assertEquals(0.6, GestureEngine.thresholds("unknown").getValue("smile"), 0.0)
        assertEquals(20.0, GestureEngine.thresholds("mlkit").getValue("turn"), 0.0)
    }

    @Test
    fun turnLeftNeedsPositiveYawThenNeutral() {
        val run = GestureRun("turn_left", "mlkit")
        fun face(t: Long, yaw: Double) = FaceSample(t, 1, FaceBox(0.3, 0.2, 0.4, 0.5), 0.9, 0.1, yaw, 0.0)
        assertEquals(RunState.RUNNING, run.push(face(0, -30.0)).state)
        assertEquals(0, run.step)
        assertEquals(RunState.RUNNING, run.push(face(66, 25.0)).state)
        assertEquals(RunState.PASS, run.push(face(132, 2.0)).state)
    }
}
