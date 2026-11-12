package dev.nihal.edgereach

import dev.nihal.edgereach.data.Config
import dev.nihal.edgereach.data.Direction
import dev.nihal.edgereach.data.Length
import dev.nihal.edgereach.data.LongMode
import dev.nihal.edgereach.data.SettingsRepo
import dev.nihal.edgereach.data.directionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsMigrationTest {

    /** Settings exactly as version 1.6 saved them (every field, customised values). */
    private val v16 = """
        {"enabled":true,
         "left":{"enabled":true,"position":0.55,"height":0.21,"widthDp":20,"color":-1,"opacity":0.0,
                 "actions":{"STRAIGHT_SHORT":"BACK","STRAIGHT_LONG":"NONE","UP_SHORT":"HOME","UP_LONG":"RECENTS",
                            "DOWN_SHORT":"LAUNCH_APP:com.spotify.music/com.spotify.music.MainActivity","DOWN_LONG":"FLASHLIGHT"}},
         "right":{"enabled":true,"position":0.62,"height":0.51,"widthDp":16,"color":null,"opacity":0.3,
                  "actions":{"STRAIGHT_SHORT":"BACK","STRAIGHT_LONG":"PREVIOUS_APP","UP_SHORT":"RECENTS","UP_LONG":"NONE",
                             "DOWN_SHORT":"NOTIFICATIONS","DOWN_LONG":"QUICK_SETTINGS"}},
         "longMode":"HOLD","shortDistDp":44,"longDistDp":180,"holdMs":550,"straightAngle":40,
         "vibrate":false,"showIndicator":true,"passThrough":false,"hideLandscape":true,"hideKeyboard":false,
         "showOnLockscreen":true,"excluded":["com.android.chrome","com.whatsapp"],
         "keepAlive":true,"autoRepair":false,"blockSystemBack":false}
    """.trimIndent()

    @Test
    fun keepsEverySettingFrom16() {
        val c = SettingsRepo.decode(v16)
        assertNotNull(c)
        c!!
        assertEquals(LongMode.HOLD, c.longMode)
        assertEquals(44, c.shortDistDp)
        assertEquals(180, c.longDistDp)
        assertEquals(550, c.holdMs)
        assertEquals(false, c.vibrate)
        assertEquals(false, c.passThrough)
        assertEquals(true, c.hideLandscape)
        assertEquals(false, c.hideKeyboard)
        assertEquals(true, c.showOnLockscreen)
        assertEquals(setOf("com.android.chrome", "com.whatsapp"), c.excluded)
        assertEquals(false, c.autoRepair)
        assertEquals(0.55f, c.left.position, 0.0001f)
        assertEquals(0.21f, c.left.height, 0.0001f)
        assertEquals(20, c.left.widthDp)
        assertEquals(-1, c.left.color)
        assertEquals(0f, c.left.opacity, 0.0001f)
        assertEquals(0.51f, c.right.height, 0.0001f)
        assertNull(c.right.color)
        assertEquals("LAUNCH_APP:com.spotify.music/com.spotify.music.MainActivity", c.left.action(Direction.DOWN, Length.SHORT))
        assertEquals("FLASHLIGHT", c.left.action(Direction.DOWN, Length.LONG))
        assertEquals("RECENTS", c.right.action(Direction.UP, Length.SHORT))
    }

    @Test
    fun oldStraightZoneBecomesBothDiagonalAngles() {
        val c = SettingsRepo.decode(v16)!!
        assertEquals(40, c.upAngle)
        assertEquals(40, c.downAngle)
    }

    @Test
    fun newAnglesSurviveARoundTrip() {
        val c = SettingsRepo.decode(v16)!!.copy(upAngle = 25, downAngle = 55)
        val again = SettingsRepo.decode(SettingsRepo.encode(c))!!
        assertEquals(c, again)
        assertEquals(25, again.upAngle)
        assertEquals(55, again.downAngle)
    }

    @Test
    fun badValueInOneFieldDoesNotLoseTheRest() {
        val broken = v16.replace("\"longMode\":\"HOLD\"", "\"longMode\":\"SOMETHING_NEW\"")
        val c = SettingsRepo.decode(broken)!!
        assertEquals(Config().longMode, c.longMode)
        assertEquals(44, c.shortDistDp)
        assertEquals(setOf("com.android.chrome", "com.whatsapp"), c.excluded)
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() {
        val newer = v16.replace("\"enabled\":true,", "\"enabled\":true,\"someFutureOption\":{\"x\":1},")
        assertEquals(44, SettingsRepo.decode(newer)!!.shortDistDp)
    }

    @Test
    fun garbageIsReportedNotSilentlyReplaced() {
        assertNull(SettingsRepo.decode("not json"))
    }

    @Test
    fun directionUsesSeparateUpAndDownAngles() {
        // Up boundary 25 degrees, down boundary 55 degrees.
        assertEquals(Direction.STRAIGHT, directionFor(0f, 25, 55))
        assertEquals(Direction.STRAIGHT, directionFor(-40f, 25, 55)) // slanted back swipe stays straight
        assertEquals(Direction.DOWN, directionFor(-60f, 25, 55))
        assertEquals(Direction.STRAIGHT, directionFor(20f, 25, 55))
        assertEquals(Direction.UP, directionFor(30f, 25, 55))
    }
}
