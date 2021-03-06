package com.rarible.flow.scanner

import com.nftco.flow.sdk.FlowAddress
import com.rarible.blockchain.scanner.flow.model.FlowLog
import com.rarible.blockchain.scanner.framework.model.Log
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.BalanceRepository
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.core.repository.coFindById
import com.rarible.flow.core.repository.coSave
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
@IntegrationTest
internal class FungibleLogListenerTest: BaseIntegrationTest() {

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var fungibleLogListener: FungibleLogListener

    @Autowired
    lateinit var balanceRepository: BalanceRepository

    @BeforeEach
    fun beforeEach() {
        orderRepository.deleteAll().block()
    }

    @Test
    fun `should deactivate orders with insufficient balance`() = runBlocking<Unit> {
        // given
        val maker = FlowAddress("0x4895ce5fb8a40f47")
        val order = Order(
            id = 24327471,
            itemId = ItemId.parse("A.ebf4ae01d1284af8.RaribleNFT:1183"),
            maker = maker,
            make = FlowAssetFungible("A.7e60df042a9c0868.FlowToken", BigDecimal.TEN),
            take = FlowAssetNFT("A.ebf4ae01d1284af8.RaribleNFT", BigDecimal.ONE, 1183),
            type = OrderType.BID,
            amount = BigDecimal.TEN,
            fill = BigDecimal.ZERO,
            cancelled = false,
            createdAt = LocalDateTime.parse("2021-12-24T05:52:21.526"),
            lastUpdatedAt = LocalDateTime.parse("2021-12-24T05:52:21.526"),
            collection = "A.ebf4ae01d1284af8.RaribleNFT",
            makeStock = BigDecimal.TEN,
            status = OrderStatus.ACTIVE,
            takePriceUsd = BigDecimal("93.75710754898183000000000")
        )
        orderRepository.coSave(order)

        val initialBalance = balanceRepository.coSave(
            Balance(maker, "A.7e60df042a9c0868.FlowToken", BigDecimal("10.5"))
        )

        // when
        fungibleLogListener.processBalance(
            BalanceHistory(initialBalance.id, BigDecimal.ONE.negate(), Instant.now().plusMillis(10000), FlowLog(
                transactionHash = "tx_hash",
                status = Log.Status.CONFIRMED,
                eventIndex = 1,
                eventType = "event_type",
                Instant.now(),
                blockHeight = 1000,
                "block_hash"
            ))
        )

        // then
        balanceRepository.coFindById(initialBalance.id)?.balance shouldBe BigDecimal("9.5")
        orderRepository.coFindById(order.id)!!.let { bid ->
            bid.status shouldBe OrderStatus.INACTIVE
            bid.makeStock shouldBe BigDecimal("9.5")
        }
    }

    @Test
    fun `should activate orders with sufficient balance`() = runBlocking<Unit> {
        // given
        val maker = FlowAddress("0x4895ce5fb8a40f47")
        val order = Order(
            id = 24327471,
            itemId = ItemId.parse("A.ebf4ae01d1284af8.RaribleNFT:1183"),
            maker = maker,
            make = FlowAssetFungible("A.7e60df042a9c0868.FlowToken", BigDecimal.TEN),
            take = FlowAssetNFT("A.ebf4ae01d1284af8.RaribleNFT", BigDecimal.ONE, 1183),
            type = OrderType.BID,
            amount = BigDecimal.TEN,
            fill = BigDecimal.ZERO,
            cancelled = false,
            createdAt = LocalDateTime.parse("2021-12-24T05:52:21.526"),
            lastUpdatedAt = LocalDateTime.parse("2021-12-24T05:52:21.526"),
            collection = "A.ebf4ae01d1284af8.RaribleNFT",
            makeStock = BigDecimal.TEN,
            status = OrderStatus.INACTIVE,
            takePriceUsd = BigDecimal("93.75710754898183000000000")
        )
        orderRepository.coSave(order)

        val initialBalance = balanceRepository.coSave(
            Balance(maker, "A.7e60df042a9c0868.FlowToken", BigDecimal("9.5"))
        )

        // when
        fungibleLogListener.processBalance(
            BalanceHistory(initialBalance.id, BigDecimal.ONE, Instant.now().plusMillis(10000), FlowLog(
                transactionHash = "tx_hash",
                status = Log.Status.CONFIRMED,
                eventIndex = 1,
                eventType = "event_type",
                Instant.now(),
                blockHeight = 1000,
                "block_hash"
            ))
        )

        // then
        balanceRepository.coFindById(initialBalance.id)?.balance shouldBe BigDecimal("10.5")
        orderRepository.coFindById(order.id)!!.let { bid ->
            bid.status shouldBe OrderStatus.ACTIVE
            bid.makeStock shouldBe bid.make.value
        }
    }

}
