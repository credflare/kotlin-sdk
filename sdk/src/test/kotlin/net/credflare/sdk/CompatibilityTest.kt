package net.credflare.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityTest {
    private val info = SessionInfo.fromJson(ContractServer.fixture("questionnaire-session.json").getJSONObject("data"))

    @Test
    fun hostsCompiledWithTheFormFlagStillDestructureAndCopy() {
        assertTrue(info.hasForms)
        val component7 = SessionInfo::class.java.methods.single { it.name == "component7" && it.returnType == Boolean::class.javaPrimitiveType }
        assertEquals(true, component7.invoke(info))
        val copy = SessionInfo::class.java.methods.single { it.name == "copy\$default" && it.parameterTypes.size == 11 }
        val onlyRequesterGiven = 0xFF and (1 shl 1).inv()
        val renamed = copy.invoke(null, info, null, "Other", 0L, null, null, null, false, false, onlyRequesterGiven, null) as SessionInfo
        assertEquals("Other", renamed.requesterName)
        assertTrue(renamed.hasForms)
        assertEquals(info.questionnaire.questions, renamed.questionnaire.questions)
        val cleared = copy.invoke(null, info, null, null, 0L, null, null, null, false, false, 0xFF and (1 shl 6).inv(), null) as SessionInfo
        assertEquals(false, cleared.hasForms)
    }

    @Test
    fun hostsCompiledWithTheShortExceptionConstructorStillBuildIt() {
        val defaults = CredflareException::class.java.getConstructor(Int::class.javaPrimitiveType, String::class.java, Throwable::class.java, String::class.java, Int::class.javaPrimitiveType, Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"))
        val error = defaults.newInstance(500, "Request failed.", null, null, 0b1100, null)
        assertEquals("api", error.type)
    }
}
