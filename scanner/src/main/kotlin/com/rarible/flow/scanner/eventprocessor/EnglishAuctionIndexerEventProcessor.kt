package com.rarible.flow.scanner.eventprocessor

import com.rarible.core.apm.withSpan
import com.rarible.flow.core.domain.*
import com.rarible.flow.scanner.model.IndexerEvent
import com.rarible.flow.scanner.service.EnglishAuctionService
import org.springframework.stereotype.Component

@Component
class EnglishAuctionIndexerEventProcessor(
    private val englishAuctionService: EnglishAuctionService
) : IndexerEventsProcessor {

    private val supportedTypes = setOf(
        FlowActivityType.LOT_AVAILABLE,
        FlowActivityType.LOT_COMPLETED,
        FlowActivityType.LOT_END_TIME_CHANGED,
        FlowActivityType.LOT_CLEANED,
        FlowActivityType.OPEN_BID,
        FlowActivityType.CLOSE_BID
    )

    override fun isSupported(event: IndexerEvent): Boolean = event.activityType() in supportedTypes

    override suspend fun process(event: IndexerEvent) {
        when(event.activityType()) {
            FlowActivityType.LOT_AVAILABLE -> openLot(event)
            FlowActivityType.LOT_COMPLETED -> completeLot(event)
            FlowActivityType.LOT_END_TIME_CHANGED -> changeLotEndTime(event)
            FlowActivityType.LOT_CLEANED -> cleanLot(event)
            FlowActivityType.OPEN_BID -> openBid(event)
            FlowActivityType.CLOSE_BID -> closeBid(event)
            else -> throw IllegalStateException("Unsupported activity type [${event.activityType()}]")
        }
    }

    private fun closeBid(event: IndexerEvent) {

    }

    private suspend fun openBid(event: IndexerEvent) {
        val activity = event.history.activity as AuctionActivityBidOpened
        withSpan("openBid") {
            englishAuctionService.openBid(activity)
        }
    }

    private suspend fun cleanLot(event: IndexerEvent) {
        val activity = event.history.activity as AuctionActivityLotCleaned
        withSpan("cleanLot") {
            englishAuctionService.finalizeLot(activity)
        }
    }

    private suspend fun changeLotEndTime(event: IndexerEvent) {
        val activity = event.history.activity as AuctionActivityLotEndTimeChanged
        withSpan("changeLotEndTime") {
            englishAuctionService.changeLotEndTime(activity)
        }
    }

    private suspend fun completeLot(event: IndexerEvent) {
        val activity = event.history.activity as AuctionActivityLotHammered
        withSpan("completeLot") {
            englishAuctionService.hammerLot(activity)
        }
    }

    private suspend fun openLot(event: IndexerEvent) {
        val activity = event.history.activity as AuctionActivityLot
        withSpan("openLot", type = "event") {
            englishAuctionService.openLot(activity, event.item)
        }
    }
}
