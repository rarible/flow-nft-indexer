package com.rarible.flow.listener.handler

import com.rarible.core.daemon.sequential.ConsumerEventHandler
import com.rarible.flow.core.domain.TokenId
import com.rarible.flow.events.BlockInfo
import com.rarible.flow.events.EventId
import com.rarible.flow.events.EventMessage
import com.rarible.flow.listener.handler.listeners.SmartContractEventHandler
import com.rarible.flow.log.Log
import org.onflow.sdk.FlowAddress


class EventHandler(
    private val smartContractEventHandlers: Map<String, SmartContractEventHandler<*>>
) : ConsumerEventHandler<EventMessage> {

    override suspend fun handle(event: EventMessage) {
        val contract = event.eventId.contractAddress
        val tokenId = tokenId(event)

        if(tokenId == null) {
            log.warn("Event [${event.eventId}] has no tokenId")
        } else {
            log.info("Handling [${event.eventId.contractEvent()}] at [$contract.$tokenId] with fields [${event.fields}]")
            smartContractEventHandlers.getOrDefault(
                event.eventId.contractEvent(),
                NoOpHandler(event.eventId)
            ).handle(contract, tokenId, event.fields, event.blockInfo)
        }
    }

    private fun tokenId(event: EventMessage): TokenId? {
        return when(val id = event.fields["id"]) {
            is String -> id.toLong()
            is Number -> id.toLong()
            else -> null
        }
    }


    companion object {
        val log by Log()
        class NoOpHandler(val eventId: EventId): SmartContractEventHandler<Unit> {
            override suspend fun handle(
                contract: FlowAddress,
                tokenId: TokenId,
                fields: Map<String, Any?>,
                blockInfo: BlockInfo
            ): Unit {
                log.info("Skipping unknown or untracked event [$eventId] for [$contract.$tokenId] with fields [${fields}]")
            }
        }
    }
}
