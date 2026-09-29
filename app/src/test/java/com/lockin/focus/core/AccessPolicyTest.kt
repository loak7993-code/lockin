package com.lockin.focus.core

import com.lockin.focus.core.model.FocusSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    private val self = "com.lockin.focus"
    private val launcher = "com.android.launcher3"

    private val settings = FocusSettings(
        blocked = setOf("com.zhiliaoapp.musically", "com.android.dialer", "com.google.android.youtube"),
        allowed = setOf("com.ankiweb.anki"),
    )

    private fun decide(pkg: String, launchers: Set<String> = setOf(launcher)) =
        AccessPolicy.evaluate(pkg, settings, self, launchers)

    @Test
    fun `a blocked app is blocked`() {
        assertTrue(decide("com.zhiliaoapp.musically") is AccessPolicy.Decision.Block)
        assertTrue(decide("com.google.android.youtube") is AccessPolicy.Decision.Block)
    }

    @Test
    fun `anything not on the list is allowed`() {
        assertEquals(AccessPolicy.Decision.Allow, decide("com.example.notes"))
    }

    @Test
    fun `LockIn never blocks its own overlay screens`() {
        assertEquals(AccessPolicy.Decision.Allow, decide(self))
    }

    @Test
    fun `the home app is never blocked, whatever the OEM calls it`() {
        assertEquals(AccessPolicy.Decision.Allow, decide("com.miui.home", setOf("com.miui.home")))
        assertEquals(AccessPolicy.Decision.Allow, decide("com.miui.home", setOf(launcher)))
    }

    @Test
    fun `a launcher the user put on the blocklist still wins`() {
        val withLauncherBlocked = settings.copy(blocked = settings.blocked + "com.miui.home")
        val decision = AccessPolicy.evaluate(
            pkg = "com.miui.home",
            settings = withLauncherBlocked,
            selfPackage = self,
            launcherPackages = setOf("com.miui.home"),
        )
        assertEquals(AccessPolicy.Decision.Allow, decision)
    }

    @Test
    fun `an explicit always-allowed entry beats the blocklist`() {
        assertEquals(AccessPolicy.Decision.Allow, decide("com.ankiweb.anki"))
    }

    @Test
    fun `the phone keeps working even if it is blocklisted by mistake`() {
        for (dialer in listOf("com.android.dialer", "com.google.android.dialer", "com.android.incallui")) {
            assertEquals(
                "$dialer must never be blocked",
                AccessPolicy.Decision.Allow,
                decide(dialer),
            )
        }
    }

    @Test
    fun `system UI, settings and package installers stay reachable`() {
        for (system in listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.deskclock",
            "com.android.mms",
        )) {
            assertEquals(AccessPolicy.Decision.Allow, decide(system))
        }
    }

    @Test
    fun `a blank package name is never blocked`() {
        assertEquals(AccessPolicy.Decision.Allow, decide(""))
    }

    @Test
    fun `the block reason names the blocklist`() {
        val decision = decide("com.zhiliaoapp.musically")
        assertEquals(
            AccessPolicy.Reason.ON_BLOCKLIST,
            (decision as AccessPolicy.Decision.Block).reason,
        )
    }
}
