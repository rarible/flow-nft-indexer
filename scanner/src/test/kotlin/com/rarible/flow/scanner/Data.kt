package com.rarible.flow.scanner

import com.nftco.flow.sdk.FlowAddress
import com.rarible.flow.core.domain.*
import org.apache.activemq.artemis.utils.RandomUtil.randomLong
import org.apache.activemq.artemis.utils.RandomUtil.randomPositiveLong
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime


object Data {
    fun createItem(): Item {
        return Item(
            "ABC",
            123L,
            FlowAddress("0x01"),
            emptyList(),
            owner = FlowAddress("0x02"),
            mintedAt = Instant.now(),
            collection = "ABC",
            updatedAt = Instant.now()
        )
    }

    fun createOrder(tokenId: Long = randomLong()): Order {
        val itemId = ItemId("0x1a2b3c4d", tokenId)
        val order = Order(
            id = randomPositiveLong(),
            itemId = itemId,
            maker = FlowAddress("0x01"),
            make = FlowAssetNFT(
                contract = itemId.contract,
                value = BigDecimal.valueOf(100L),
                tokenId = itemId.tokenId
            ),
            amount = BigDecimal.valueOf(100L),
//            amountUsd = BigDecimal.valueOf(100L),
            data = OrderData(
                payouts = listOf(Payout(FlowAddress("0x01"), BigDecimal.valueOf(1L))),
                originalFees = listOf(Payout(FlowAddress("0x02"), BigDecimal.valueOf(1L)))
            ),
            collection = "collection",
            take = FlowAssetFungible(
                "FLOW",
                BigDecimal.TEN
            ),
            makeStock = BigDecimal.TEN,
            lastUpdatedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            type = OrderType.LIST
        )
        return order
    }
}
