package com.nbaltas.nickos.talk

import com.nbaltas.nickos.node.talkBannerShown
import com.nbaltas.nickos.ui.master.talkTargetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the banner counts as shown, and who push to talk reaches. */
class TalkShownTest {
    @Test fun bannerCountsOnceAddedEvenBeforeAttachment() {
        assertTrue("added, parent assigned, not yet attached", talkBannerShown(added = true, attached = false, hasParent = true))
        assertTrue(talkBannerShown(added = true, attached = true, hasParent = true))
    }

    @Test fun bannerDoesNotCountWhenMissingOrRemoved() {
        assertFalse("never added", talkBannerShown(added = false, attached = false, hasParent = false))
        assertFalse("added but removed again", talkBannerShown(added = true, attached = false, hasParent = false))
    }

    @Test fun targetsAreOnlineTabletsOnStageWhenACameraIsOn() {
        val ids = talkTargetIds(listOf("a", "b", "c"), online = setOf("a", "b"), onStage = setOf("a", "c"), anyCamOn = true)
        assertEquals(listOf("a"), ids)
    }

    @Test fun targetsAreAllOnlineTabletsWhenNoCameraIsOn() {
        val ids = talkTargetIds(listOf("a", "b", "c"), online = setOf("a", "b"), onStage = emptySet(), anyCamOn = false)
        assertEquals(listOf("a", "b"), ids)
    }

    @Test fun nobodyOnlineMeansNoTargets() {
        assertEquals(emptyList<String>(), talkTargetIds(listOf("a"), online = emptySet(), onStage = emptySet(), anyCamOn = false))
    }
}
