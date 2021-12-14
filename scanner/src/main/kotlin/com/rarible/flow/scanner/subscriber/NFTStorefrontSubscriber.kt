package com.rarible.flow.scanner.subscriber

import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.FlowEvent
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainBlock
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainLog
import com.rarible.blockchain.scanner.flow.model.FlowDescriptor
import com.rarible.core.apm.withSpan
import com.rarible.flow.core.domain.FlowLogType
import com.rarible.flow.core.repository.ItemCollectionRepository
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.events.EventId
import com.rarible.flow.scanner.TxManager
import com.rarible.flow.scanner.cadence.ListingAvailable
import com.rarible.flow.scanner.cadence.ListingCompleted
import com.rarible.flow.scanner.model.parse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class NFTStorefrontSubscriber(
    private val collectionRepository: ItemCollectionRepository,
    private val txManager: TxManager,
    private val orderRepository: OrderRepository
): BaseFlowLogEventSubscriber() {

    private val events = setOf("ListingAvailable", "ListingCompleted")

    private val contractName = "NFTStorefront"

    private lateinit var nftEvents: Set<String>

    override val descriptors: Map<FlowChainId, FlowDescriptor> = mapOf(
        FlowChainId.MAINNET to flowDescriptor(
            address = "4eb8a10cb9f87357",
            events = events,
            contract = contractName,
            startFrom = 19799019L
        ),
        FlowChainId.TESTNET to flowDescriptor(
            address = "94b06cfca1d8a476",
            events = events,
            contract = contractName
        )

    )

    override suspend fun eventType(log: FlowBlockchainLog): FlowLogType = when(EventId.of(log.event.type).eventName) {
        "ListingAvailable" -> FlowLogType.LISTING_AVAILABLE
        "ListingCompleted" -> FlowLogType.LISTING_COMPLETED
        else -> throw IllegalStateException("Unsupported event type [${log.event.type}]")
    }

    override suspend fun isNewEvent(block: FlowBlockchainBlock, event: FlowEvent): Boolean {
        if (nftEvents.isEmpty()) {
            nftEvents = collectionRepository.findAll().asFlow().toList().flatMap {
                listOf("${it.id}.Withdraw", "${it.id}.Deposit")
            }.toSet()
        }
        return withSpan("checkOrderIsNewEvent", "event") { super.isNewEvent(block, event) && when(EventId.of(event.type).eventName) {
            "ListingAvailable" -> {
                val e = event.event.parse<ListingAvailable>()
                val nftCollection = EventId.of(e.nftType).collection()
                collectionRepository.existsById(nftCollection).awaitSingle()
            }
            "ListingCompleted" -> {
                val e = event.event.parse<ListingCompleted>()
                return@withSpan if (e.purchased) {
                    txManager.onTransaction(
                        blockHeight = block.number,
                        transactionId = event.transactionId
                    ) {
                        it.events.map { EventId.of(it.type) }.any {
                            nftEvents.contains(it.toString())
                        }
                    }
                } else orderRepository.existsById(e.listingResourceID).awaitSingle()

            }
            else -> false
        } }
    }

    @PostConstruct
    private fun postConstruct() = runBlocking {
        nftEvents =
            collectionRepository.findAll().asFlow().toList().flatMap {
                listOf("${it.id}.Withdraw", "${it.id}.Deposit")
            }.toSet()
    }
}