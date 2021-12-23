package com.rarible.flow.scanner.service


import com.nftco.flow.sdk.FlowAddress
import com.rarible.flow.core.domain.Balance
import com.rarible.flow.core.domain.OrderStatus
import com.rarible.flow.core.repository.OrderFilter
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.scanner.Data
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class BidServiceTest: FunSpec({
    val element = Data.createOrder()

    val repository = mockk<OrderRepository>("orderRepository") {
        every {
            search(any(), isNull(), any(), OrderFilter.Sort.LATEST_FIRST)
        } returns Flux.fromIterable(
            listOf(element)
        )

        every {
            search(any(), eq(OrderFilter.Sort.LATEST_FIRST.nextPage(element)), any(), OrderFilter.Sort.LATEST_FIRST)
        } returns Flux.empty()

        every {
            save(any())
        } answers { Mono.just(arg(0)) }
    }

    test("should deactivate bids by balance") {
        val order = BidService(repository).deactivateBidsByBalance(
            Balance(FlowAddress("0x01"), "A.1234.FlowToken", 11.3.toBigDecimal())
        ).toList().first()

        order.makeStock shouldBe 11.3.toBigDecimal()
        order.status shouldBe OrderStatus.INACTIVE

        coVerify {
            repository.search(any(), null, 1000)
            repository.search(any(), OrderFilter.Sort.LATEST_FIRST.nextPage(element), 1000)
            repository.save(withArg {
                it.makeStock shouldBe 11.3.toBigDecimal()
                it.status shouldBe OrderStatus.INACTIVE
            })
        }
    }

    test("should activate bids by balance") {
        val order = BidService(repository).activateBidsByBalance(
            Balance(FlowAddress("0x01"), "A.1234.FlowToken", 11.3.toBigDecimal())
        ).toList().first()

        order.makeStock shouldBe order.make.value
        order.status shouldBe OrderStatus.ACTIVE

        coVerify {
            repository.search(any(), null, 1000)
            repository.search(any(), OrderFilter.Sort.LATEST_FIRST.nextPage(element), 1000)
            repository.save(withArg {
                it.makeStock shouldBe it.make.value
                it.status shouldBe OrderStatus.ACTIVE
            })
        }
    }

})