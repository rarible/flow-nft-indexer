package com.rarible.flow.scanner.subscriber

import com.nftco.flow.sdk.FlowChainId
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainLog
import com.rarible.blockchain.scanner.flow.model.FlowDescriptor
import com.rarible.flow.Contracts
import com.rarible.flow.core.domain.FlowLogType
import com.rarible.flow.events.EventId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.springframework.stereotype.Component

@ExperimentalCoroutinesApi
@Component
class MatrixWorldSubscriber : BaseFlowLogEventSubscriber() {
    val events = setOf("Minted", "Withdraw", "Deposit")

    override val descriptors: Map<FlowChainId, FlowDescriptor>
        get() = mapOf(
            FlowChainId.MAINNET to flowDescriptor(
                contract = Contracts.MATRIX_WORLD_VOUCHER.contractName,
                address = Contracts.MATRIX_WORLD_VOUCHER.deployments[FlowChainId.MAINNET]!!.base16Value,
                events = events,
                dbCollection = collection,
                startFrom = 19683032L,
            ),
            FlowChainId.TESTNET to flowDescriptor(
                contract = Contracts.MATRIX_WORLD_VOUCHER.contractName,
                address = Contracts.MATRIX_WORLD_VOUCHER.deployments[FlowChainId.TESTNET]!!.base16Value,
                events = events,
                dbCollection = collection,
            ),
            FlowChainId.EMULATOR to flowDescriptor(
                contract = Contracts.MATRIX_WORLD_VOUCHER.contractName,
                address = Contracts.MATRIX_WORLD_VOUCHER.deployments[FlowChainId.EMULATOR]!!.base16Value,
                events = events,
                dbCollection = collection,
            ),
        )

    override suspend fun eventType(log: FlowBlockchainLog): FlowLogType = when(EventId.of(log.event.type).eventName) {
        "Withdraw" -> FlowLogType.WITHDRAW
        "Deposit" -> FlowLogType.DEPOSIT
        "Minted" -> FlowLogType.MINT
        else ->  throw IllegalStateException("Unsupported event type: ${log.event.type}")
    }
}
