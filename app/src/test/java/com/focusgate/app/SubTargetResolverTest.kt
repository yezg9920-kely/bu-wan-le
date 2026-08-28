package com.focusgate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubTargetResolverTest {
    @Test
    fun recognizesKnownWechatFinderHomeVariants() {
        assertEquals(
            "finder",
            Constants.resolveSubTarget(
                "com.tencent.mm",
                "com.tencent.mm.plugin.finder.ui.FinderHomeUI"
            )
        )
        assertEquals(
            "finder",
            Constants.resolveSubTarget(
                "com.tencent.mm",
                "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI"
            )
        )
    }

    @Test
    fun recognizesFutureFinderHomeSuffixButNotUnrelatedWechatPage() {
        assertEquals(
            "finder",
            Constants.resolveSubTarget(
                "com.tencent.mm",
                "com.tencent.mm.plugin.finder.feed.ui.FinderHomeNewUI"
            )
        )
        assertNull(
            Constants.resolveSubTarget(
                "com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI"
            )
        )
        assertNull(
            Constants.resolveSubTarget(
                "com.example.container",
                "com.tencent.mm.plugin.finder.ui.FinderHomeUI"
            )
        )
    }
}
