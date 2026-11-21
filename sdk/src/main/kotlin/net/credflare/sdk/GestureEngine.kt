package net.credflare.sdk

import kotlin.math.abs

data class FaceBox(val x: Double, val y: Double, val w: Double, val h: Double)

data class FaceSample(
    val t: Long,
    val faces: Int,
    val box: FaceBox? = null,
    val eyeOpen: Double? = null,
    val smile: Double? = null,
    val yaw: Double? = null,
    val pitch: Double? = null,
) {
    fun signal(name: String): Double? = when(name) {
        "eye_open" -> eyeOpen
        "smile" -> smile
        "yaw" -> yaw
        "pitch" -> pitch
        else -> throw IllegalArgumentException("Unknown signal $name")
    }
}

enum class PositionStatus(val copyKey: String) {
    NO_FACE(CredflareSpec.CopyKey.LIVENESS_POSITION_NO_FACE),
    MULTIPLE_FACES(CredflareSpec.CopyKey.LIVENESS_POSITION_ONE_FACE),
    TOO_SMALL(CredflareSpec.CopyKey.LIVENESS_POSITION_MOVE_CLOSER),
    TOO_LARGE(CredflareSpec.CopyKey.LIVENESS_POSITION_MOVE_FURTHER),
    OFF_CENTER(CredflareSpec.CopyKey.LIVENESS_POSITION_CENTER),
    OK(CredflareSpec.CopyKey.LIVENESS_POSITION_READY),
}

enum class RunState { RUNNING, PASS, TIMEOUT }

data class RunResult(val state: RunState, val step: Int = 0, val tracked: Boolean = true, val status: PositionStatus? = null, val t: Long? = null)

object GestureEngine {
    fun thresholds(provider: String): Map<String, Double> =
        CredflareSpec.Liveness.THRESHOLDS.getValue("default") + (CredflareSpec.Liveness.THRESHOLDS[provider] ?: emptyMap())

    private fun lookup(thresholds: Map<String, Double>, value: String): Double =
        value.toDoubleOrNull() ?: thresholds[value] ?: throw IllegalArgumentException("Unknown threshold $value")

    fun testStep(step: SpecGestureStep, sample: FaceSample, thresholds: Map<String, Double>): Boolean {
        val s = sample.signal(step.signal) ?: return false
        val v = lookup(thresholds, step.value)
        return when(step.op) {
            "gt" -> s > v
            "lt" -> s < v
            "lt_neg" -> s < -v
            "abs_lt" -> abs(s) < v
            else -> throw IllegalArgumentException("Unknown op ${step.op}")
        }
    }

    fun positionStatus(sample: FaceSample): PositionStatus {
        if(sample.faces == 0) return PositionStatus.NO_FACE
        if(sample.faces > CredflareSpec.Liveness.POSITION_FACES) return PositionStatus.MULTIPLE_FACES
        val box = sample.box ?: return PositionStatus.NO_FACE
        val area = box.w * box.h
        if(area < CredflareSpec.Liveness.POSITION_FACE_AREA_MIN) return PositionStatus.TOO_SMALL
        if(area > CredflareSpec.Liveness.POSITION_FACE_AREA_MAX) return PositionStatus.TOO_LARGE
        val dx = abs(box.x + box.w / 2 - 0.5)
        val dy = abs(box.y + box.h / 2 - 0.5)
        if(dx > CredflareSpec.Liveness.POSITION_CENTER_TOLERANCE || dy > CredflareSpec.Liveness.POSITION_CENTER_TOLERANCE) return PositionStatus.OFF_CENTER
        return PositionStatus.OK
    }
}

interface EngineRun { fun push(sample: FaceSample): RunResult }

class PositionGate: EngineRun {
    private var start: Long? = null
    private var done = false
    override fun push(sample: FaceSample): RunResult {
        check(!done) { "Position gate already finished" }
        val begin = start ?: sample.t.also { start = it }
        val status = GestureEngine.positionStatus(sample)
        if(sample.t - begin >= CredflareSpec.Liveness.GESTURE_TIMEOUT_MS) { done = true; return RunResult(RunState.TIMEOUT, status = status) }
        if(status == PositionStatus.OK) { done = true; return RunResult(RunState.PASS, status = status, t = sample.t) }
        return RunResult(RunState.RUNNING, status = status)
    }
}

class GestureRun(gesture: String, provider: String = "default"): EngineRun {
    private val steps = CredflareSpec.Liveness.GESTURES[gesture] ?: throw IllegalArgumentException("Unknown gesture $gesture")
    private val thresholds = GestureEngine.thresholds(provider)
    private val resetMs = CredflareSpec.Liveness.FACE_LOST_RESET_MS
    private var start: Long? = null
    private var prevT: Long? = null
    private var lostSince: Long? = null
    private var index = 0
    private var count = 0
    private var done = false
    val step get() = index
    val stepCount get() = steps.size

    override fun push(sample: FaceSample): RunResult {
        check(!done) { "Gesture run already finished" }
        val begin = start ?: sample.t.also { start = it }
        if(sample.t - begin >= CredflareSpec.Liveness.GESTURE_TIMEOUT_MS) { done = true; return RunResult(RunState.TIMEOUT, index) }
        val gap = prevT?.let { sample.t - it >= resetMs } ?: false
        prevT = sample.t
        if(sample.faces != 1) {
            count = 0
            val since = lostSince ?: sample.t.also { lostSince = it }
            if(sample.t - since >= resetMs) index = 0
            return RunResult(RunState.RUNNING, index, tracked = false)
        }
        if(gap || lostSince?.let { sample.t - it >= resetMs } == true) { index = 0; count = 0 }
        lostSince = null
        val step = steps[index]
        if(GestureEngine.testStep(step, sample, thresholds)) {
            count += 1
            val hold = step.holdFrames?.let { lookupHold(it) } ?: 1
            if(count >= hold) {
                index += 1
                count = 0
                if(index == steps.size) { done = true; return RunResult(RunState.PASS, index, t = sample.t) }
            }
        } else count = 0
        return RunResult(RunState.RUNNING, index)
    }

    private fun lookupHold(name: String): Int = (name.toDoubleOrNull() ?: thresholds[name] ?: throw IllegalArgumentException("Unknown threshold $name")).toInt()
}
