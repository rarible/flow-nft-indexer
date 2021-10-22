package com.rarible.flow.scanner.subscriber

import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.cadence.AddressField
import com.nftco.flow.sdk.cadence.NumberField
import com.nftco.flow.sdk.cadence.OptionalField
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainBlock
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainLog
import com.rarible.blockchain.scanner.flow.model.FlowDescriptor
import com.rarible.flow.core.domain.*
import com.rarible.flow.events.EventMessage
import org.springframework.stereotype.Component

@Component
class EvolutionSubscriber : BaseItemHistoryFlowLogSubscriber() {

    private val events = "Withdraw,Deposit,CollectibleMinted,CollectibleDestroyed".split(",")

    override val descriptors: Map<FlowChainId, FlowDescriptor>
        get() = mapOf(
            FlowChainId.MAINNET to flowDescriptor(
                address = "f4264ac8f3256818",
                contract = "Evolution",
                events = events,
                startFrom = 13001301L,
            ),
            FlowChainId.TESTNET to flowDescriptor(
                address = "01658d9b94068f3c",
                contract = "Evolution",
                events = events,
            ),
            FlowChainId.EMULATOR to flowDescriptor(
                address = "f8d6e0586b0a20c7",
                contract = "Evolution",
                events = events,
            )
        )

    override fun activity(block: FlowBlockchainBlock, log: FlowBlockchainLog, msg: EventMessage): FlowActivity {
        val id: NumberField by msg.fields
        val tokenId = id.toLong()!!
        val contract = msg.eventId.collection()
        val timestamp = msg.timestamp
        return when (msg.eventId.eventName) {
            "Withdraw" -> {
                val from: OptionalField by msg.fields
                WithdrawnActivity(
                    contract = contract,
                    tokenId = tokenId,
                    from = if (from.value == null) null else (from.value as AddressField).value,
                    timestamp = timestamp,
                )
            }
            "Deposit" -> {
                val to: OptionalField by msg.fields
                DepositActivity(
                    contract = contract,
                    tokenId = tokenId,
                    to = if (to.value == null) null else (to.value as AddressField).value,
                    timestamp = timestamp,
                )
            }
            "CollectibleMinted" -> {
                MintActivity(
                    owner = msg.eventId.contractAddress.formatted,
                    contract = contract,
                    tokenId = tokenId,
                    timestamp = timestamp,
                    value = 1L, royalties = emptyList(), metadata = emptyMap(),
                )
            }
            "CollectibleDestroyed" -> {
                BurnActivity(
                    contract = contract,
                    tokenId = tokenId,
                    timestamp = timestamp,
                )
            }
            else -> {
                throw IllegalStateException("Unsupported eventId: ${msg.eventId}")
            }
        }
    }
}