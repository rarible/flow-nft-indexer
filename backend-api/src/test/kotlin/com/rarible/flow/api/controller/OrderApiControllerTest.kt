package com.rarible.flow.api.controller

import com.nftco.flow.sdk.FlowAddress
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.randomAddress
import com.rarible.protocol.dto.FlowOrderDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@SpringBootTest(
    properties = [
        "application.environment = dev",
        "spring.cloud.service-registry.auto-registration.enabled = false",
        "spring.cloud.discovery.enabled = false",
        "spring.cloud.consul.config.enabled = false",
        "logging.logstash.tcp-socket.enabled = false",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient(timeout = "60000")
@ActiveProfiles("test")
class OrderApiControllerTest {


    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var repo: OrderRepository

    @Autowired
    lateinit var client: WebTestClient

    @BeforeEach
    internal fun setUp() {
        repo.deleteAll().block()
    }

    @Test
    fun `should throw exception`() {
        client.get()
            .uri("/v0.1/orders/1")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return order by id`() {
        val itemId = ItemId("0x1a2b3c4d", 25L)
        val order = Order(
            id = 1L,
            itemId = itemId,
            maker = FlowAddress(randomAddress()),
            make = FlowAssetNFT(
                contract = itemId.contract,
                value = BigDecimal.valueOf(100L),
                tokenId = itemId.tokenId
            ),
            amount = BigDecimal.valueOf(100L),
            amountUsd = BigDecimal.valueOf(100L),
            data = OrderData(
                payouts = listOf(Payout(FlowAddress(randomAddress()), BigDecimal.valueOf(1L))),
                originalFees = listOf(Payout(FlowAddress(randomAddress()), BigDecimal.valueOf(1L)))
            ),
            collection = "collection"
        )

        repo.save(order).block()

        val resp = client.get()
            .uri("/v0.1/orders/${order.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody(FlowOrderDto::class.java)
            .returnResult().responseBody!!

        Assertions.assertNotNull(resp)

    }
}
