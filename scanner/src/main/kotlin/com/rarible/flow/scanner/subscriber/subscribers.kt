package com.rarible.flow.scanner.subscriber

import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.cadence.AddressField
import com.nftco.flow.sdk.cadence.OptionalField
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainBlock
import com.rarible.blockchain.scanner.flow.client.FlowBlockchainLog
import com.rarible.blockchain.scanner.flow.model.FlowBlock
import com.rarible.blockchain.scanner.flow.model.FlowDescriptor
import com.rarible.blockchain.scanner.flow.model.FlowLog
import com.rarible.blockchain.scanner.framework.model.Log
import com.rarible.flow.core.domain.BalanceHistory
import com.rarible.flow.core.domain.BalanceId
import java.math.BigDecimal
import java.time.Instant


internal fun flowDescriptor(
    address: String,
    contract: String,
    events: Iterable<String>,
    startFrom: Long? = null,
    dbCollection: String,
) = FlowDescriptor(
    id = "${contract}Descriptor",
    events = events.map { "A.$address.$contract.$it" }.toSet(),
    collection = dbCollection,
    startFrom = startFrom
)

internal fun balanceHistory(
    balanceId: BalanceId,
    change: BigDecimal,
    block: FlowBlockchainBlock,
    logRecord: FlowBlockchainLog
): BalanceHistory {
    val time = Instant.ofEpochMilli(block.timestamp)
    return BalanceHistory(
        balanceId,
        change,
        time,
        log = FlowLog(
            transactionHash = logRecord.event.transactionId.base16Value,
            status = Log.Status.CONFIRMED,
            eventIndex = logRecord.event.eventIndex,
            eventType = logRecord.event.type,
            timestamp = time,
            blockHeight = block.number,
            blockHash = block.hash
        )
    )
}

internal fun balanceHistory(
    address: OptionalField,
    amount: BigDecimal,
    token: String,
    block: FlowBlockchainBlock,
    logRecord: FlowBlockchainLog
): BalanceHistory {
    val flowAddress = FlowAddress((address.value as AddressField).value!!)
    return balanceHistory(BalanceId(flowAddress, token), amount, block, logRecord)
}