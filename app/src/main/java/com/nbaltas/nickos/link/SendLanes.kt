package com.nbaltas.nickos.link

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * One ordered lane per tablet for commands. Commands to one tablet keep their order (volume 40 then 60 arrive as 40,
 * 60), but a tablet whose link is stuck holds up only its own lane, never a command to another tablet.
 */
class SendLanes(private val scope: CoroutineScope, private val base: CoroutineDispatcher = Dispatchers.IO) {
    private val lanes = ConcurrentHashMap<String, CoroutineDispatcher>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun launch(id: String, block: () -> Unit) {
        val lane = lanes.getOrPut(id) { base.limitedParallelism(1) }
        scope.launch(lane) { block() }
    }
}
