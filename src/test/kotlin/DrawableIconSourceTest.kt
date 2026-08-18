package io.github.siddhardh7.iconlens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

class DrawableIconSourceTest : BasePlatformTestCase() {

    fun testDiscoversAndDedupesAcrossDensityBuckets() {
        myFixture.tempDirFixture.createFile("res/drawable/ic_calendar.xml", "<vector/>")
        myFixture.tempDirFixture.createFile("res/drawable-hdpi/ic_calendar.png", "")
        myFixture.tempDirFixture.createFile("res/drawable/ic_close.png", "")
        myFixture.tempDirFixture.createFile("res/values/strings.xml", "<resources/>")
        myFixture.tempDirFixture.createFile("res/drawable/ic_ninepatch.9.png", "")

        val resources = runBlocking { DrawableIconSource(project).discover() }

        assertEquals(2, resources.size)
        val byName = resources.associateBy { it.name }
        assertEquals(IconResourceType.VECTOR_DRAWABLE, byName.getValue("ic_calendar").type)
        assertEquals(IconResourceType.PNG, byName.getValue("ic_close").type)
    }

    fun testIgnoresNonDrawableDirectories() {
        myFixture.tempDirFixture.createFile("res/layout/activity_main.xml", "<LinearLayout/>")
        myFixture.tempDirFixture.createFile("res/mipmap/ic_launcher.png", "")

        val resources = runBlocking { DrawableIconSource(project).discover() }

        assertEquals(0, resources.size)
    }

    fun testDiscoversLargeResourceCollectionWithoutPathologicalSlowdown() {
        repeat(500) { i ->
            myFixture.tempDirFixture.createFile("res/drawable/ic_scale_$i.png", "")
        }

        val elapsedMillis = measureTimeMillis {
            val resources = runBlocking { DrawableIconSource(project).discover() }
            assertEquals(500, resources.size)
        }

        assertTrue(
            "discover() of 500 real resources took ${elapsedMillis}ms, expected under 10000ms",
            elapsedMillis < 10_000,
        )
    }
}
