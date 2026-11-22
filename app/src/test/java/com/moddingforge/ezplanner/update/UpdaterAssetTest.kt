package com.moddingforge.ezplanner.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Both apps are attached to the same GitHub release; each may only pick its own APK. */
class UpdaterAssetTest {
    private val release = listOf(
        "YourTube-0.3.0.apk", "YourTube-0.3.0.apk.sha256",
        "YourTubeMusic-0.3.0.apk", "YourTubeMusic-0.3.0.apk.sha256",
    )

    @Test fun youTubePicksItsOwnApk() {
        assertEquals("YourTube-0.3.0.apk", Updater.pickApk(release, "YourTube-", "x86_64"))
    }

    @Test fun musicPicksItsOwnApk() {
        assertEquals("YourTubeMusic-0.3.0.apk", Updater.pickApk(release, "YourTubeMusic-", "x86_64"))
    }

    @Test fun olderReleaseWithoutMusicApkIsNoUpdateForMusic() {
        assertNull(Updater.pickApk(listOf("YourTube-0.2.3.apk"), "YourTubeMusic-", "arm64-v8a"))
    }

    @Test fun debugBuildsAndAbiSplits() {
        val names = listOf("YourTube-1.0.0-debug.apk", "YourTube-1.0.0-x86_64.apk", "YourTube-1.0.0-arm64-v8a.apk")
        assertEquals("YourTube-1.0.0-arm64-v8a.apk", Updater.pickApk(names, "YourTube-", "arm64-v8a"))
        assertNull(Updater.pickApk(listOf("YourTube-1.0.0-debug.apk"), "YourTube-", "x86_64"))
    }
}
