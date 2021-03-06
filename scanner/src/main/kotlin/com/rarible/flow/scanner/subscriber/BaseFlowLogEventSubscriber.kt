package com.rarible.flow.scanner.subscriber

import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.FlowEvent
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainBlock
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainLog
import com.rarible.blockchain.scanner.flow.model.FlowDescriptor
import com.rarible.blockchain.scanner.flow.model.FlowLog
import com.rarible.blockchain.scanner.flow.model.FlowLogRecord
import com.rarible.blockchain.scanner.flow.subscriber.FlowLogEventSubscriber
import com.rarible.blockchain.scanner.framework.model.Log
import com.rarible.flow.core.domain.FlowLogEvent
import com.rarible.flow.core.domain.FlowLogType
import com.rarible.flow.core.repository.FlowLogEventRepository
import com.rarible.flow.events.EventMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.time.Instant

abstract class BaseFlowLogEventSubscriber: FlowLogEventSubscriber {

    @Value("\${blockchain.scanner.flow.chainId}")
    protected lateinit var chainId: FlowChainId

    protected val collection = "flow_log_event"

    protected val logger by com.rarible.flow.log.Log()

    @Autowired
    private lateinit var flogEventRepository: FlowLogEventRepository

    abstract val descriptors: Map<FlowChainId, FlowDescriptor>

    override fun getDescriptor(): FlowDescriptor = when(chainId) {
        FlowChainId.EMULATOR -> descriptors[chainId] ?: FlowDescriptor("", emptySet(), "")
        else -> descriptors[chainId]!!
    }

    override fun getEventRecords(block: FlowBlockchainBlock, log: FlowBlockchainLog): Flow<FlowLogRecord<*>> = flow {
        val descriptor = getDescriptor()
        emitAll(
            if (descriptor.events.contains(log.event.id) && isNewEvent(block, log.event)) {
                flowOf(
                    FlowLogEvent(
                        log = FlowLog(
                            transactionHash = log.event.transactionId.base16Value,
                            status = Log.Status.CONFIRMED,
                            eventIndex = log.event.eventIndex,
                            eventType = log.event.type,
                            timestamp = Instant.ofEpochMilli(block.timestamp),
                            blockHeight = block.number,
                            blockHash = block.hash
                        ),
                        event = com.nftco.flow.sdk.Flow.unmarshall(EventMessage::class, log.event.event),
                        type = eventType(log),
                    )
                )
            } else emptyFlow()
        )
    }


    protected open suspend fun isNewEvent(block: FlowBlockchainBlock, event: FlowEvent): Boolean {
        return !flogEventRepository.existsById("${event.transactionId.base16Value}.${event.eventIndex}").awaitSingle()
    }

    abstract suspend fun eventType(log: FlowBlockchainLog): FlowLogType
}
