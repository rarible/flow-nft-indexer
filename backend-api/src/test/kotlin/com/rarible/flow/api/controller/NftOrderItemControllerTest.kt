package com.rarible.flow.api.controller

import com.nftco.flow.sdk.FlowAddress
import com.rarible.core.test.ext.MongoTest
import com.rarible.flow.api.config.Config
import com.rarible.flow.core.config.CoreConfig
import com.rarible.flow.core.domain.FlowAssetFungible
import com.rarible.flow.core.domain.FlowAssetNFT
import com.rarible.flow.core.domain.Item
import com.rarible.flow.core.domain.ItemId
import com.rarible.flow.core.domain.Order
import com.rarible.flow.core.domain.OrderData
import com.rarible.flow.core.domain.OrderType
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.randomAddress
import com.rarible.flow.randomLong
import com.rarible.protocol.dto.FlowNftItemDto
import com.rarible.protocol.dto.FlowNftItemsDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

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
@MongoTest
@ActiveProfiles("test")
@Import(Config::class, CoreConfig::class)
@Disabled("TODO unused controller?")
class NftOrderItemControllerTest {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @BeforeEach
    internal fun setUp() {
        itemRepository.deleteAll().block()
        orderRepository.deleteAll().block()
    }

    @Test
    internal fun `continuation test`() {
        val nftContract = randomAddress()
        val nftOwner = FlowAddress(randomAddress())
        var tokenId = 42L
        val item = Item(
            contract = nftContract,
            tokenId = tokenId,
            creator = nftOwner,
            royalties = listOf(),
            owner = nftOwner,
            mintedAt = Instant.now(Clock.systemUTC()),
            collection = "collection",
            updatedAt = Instant.now()
        )

        itemRepository.saveAll(
            listOf(
                item,
                item.copy(tokenId = ++tokenId, updatedAt = Instant.now(Clock.systemUTC()).plusMillis(1000L)),
                item.copy(tokenId = ++tokenId, updatedAt = Instant.now(Clock.systemUTC()).plusMillis(2000L))
            )
        ).then().block()

        client.get()
            .uri("/v0.1/order/items/all")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .consumeWith { response ->
                val itemsDto = response.responseBody
                Assertions.assertNotNull(itemsDto)
                itemsDto as FlowNftItemsDto
                Assertions.assertNotNull(itemsDto.items)
                Assertions.assertTrue(itemsDto.items.isNotEmpty())
                Assertions.assertTrue(itemsDto.items.size == 3)

                val sorted = itemsDto.items.sortedByDescending(FlowNftItemDto::lastUpdatedAt).sortedByDescending(FlowNftItemDto::id)
                    .toList()
                Assertions.assertArrayEquals(sorted.toTypedArray(), itemsDto.items.toTypedArray())
            }

        client.get().uri("/v0.1/order/items/all?size=1")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .consumeWith { response ->
                val itemsDto = response.responseBody
                Assertions.assertNotNull(itemsDto)
                itemsDto as FlowNftItemsDto
                Assertions.assertNotNull(itemsDto.items)
                Assertions.assertTrue(itemsDto.items.isNotEmpty())
                Assertions.assertNotNull(itemsDto.total)
                Assertions.assertEquals(1, itemsDto.total)
                Assertions.assertNotNull(itemsDto.continuation)
                val lastItem = itemsDto.items.last()

                Assertions.assertEquals(44L, lastItem.tokenId.toLong())

                val cont = "${lastItem.lastUpdatedAt.toEpochMilli()}_${lastItem.id}"

                Assertions.assertEquals(cont, itemsDto.continuation)

                client.get().uri("/v0.1/order/items/all?size=1&continuation={continuation}", mapOf("continuation" to itemsDto.continuation))
                    .exchange()
                    .expectStatus().isOk
                    .expectBody<FlowNftItemsDto>()
                    .consumeWith { nextResponse ->
                        val nextItemsDto = nextResponse.responseBody
                        Assertions.assertNotNull(nextItemsDto)
                        nextItemsDto as FlowNftItemsDto
                        Assertions.assertNotNull(nextItemsDto.total)
                        Assertions.assertNotNull(nextItemsDto.continuation)

                        Assertions.assertEquals(1, nextItemsDto.total)

                        val nextLastItem = nextItemsDto.items.last()

                        Assertions.assertEquals(43L, nextLastItem.tokenId.toLong())
                        Assertions.assertTrue(lastItem.lastUpdatedAt.isAfter(nextLastItem.lastUpdatedAt))
                        Assertions.assertTrue(lastItem.tokenId > nextLastItem.tokenId)
                    }

                client.get().uri("/v0.1/order/items/all?size=2&continuation={continuation}", mapOf("continuation" to itemsDto.continuation))
                    .exchange()
                    .expectStatus().isOk
                    .expectBody<FlowNftItemsDto>()
                    .consumeWith { nextResponse ->
                        val nextItemsDto = nextResponse.responseBody
                        Assertions.assertNotNull(nextItemsDto)
                        nextItemsDto as FlowNftItemsDto
                        Assertions.assertNotNull(nextItemsDto.total)
                        Assertions.assertNotNull(nextItemsDto.continuation)

                        Assertions.assertEquals(2, nextItemsDto.total)

                        val nextLastItem = nextItemsDto.items.last()

                        Assertions.assertEquals(42L, nextLastItem.tokenId.toLong())
                        Assertions.assertTrue(lastItem.lastUpdatedAt.isAfter(nextLastItem.lastUpdatedAt))
                        Assertions.assertTrue(lastItem.tokenId - nextLastItem.tokenId == 2.toBigInteger())
                    }
            }


    }

    @Test
    fun `should return 1 item by owner`() {
        val nftContract = randomAddress()
        val nftOwner = FlowAddress(randomAddress())
        val tokenId = 42L
        val nftId = ItemId(nftContract, tokenId)
        val item = Item(
            contract = nftContract,
            tokenId = tokenId,
            creator = nftOwner,
            royalties = listOf(),
            owner = nftOwner,
            mintedAt = Instant.now(Clock.systemUTC()),
            collection = "collection",
            updatedAt = Instant.now()
        )

        itemRepository.saveAll(
            listOf(item, item.copy(tokenId = 500L), item.copy(tokenId = 600L))
        ).then().block()

        val order = Order(
            id = 1L,
            itemId = nftId,
            maker = nftOwner,
            make = FlowAssetNFT(
                contract = nftContract,
                value = 1.toBigDecimal(),
                tokenId = tokenId
            ),
            take = FlowAssetFungible(
                "FLOW",
                BigDecimal.TEN
            ),
            makeStock = BigDecimal.TEN,
            amount = 1000.toBigDecimal(),
            data = OrderData(
                payouts = listOf(), originalFees = listOf()
            ),
            collection = item.collection,
            createdAt = LocalDateTime.now(ZoneOffset.UTC),
            type = OrderType.LIST
        )

        orderRepository.saveAll(
            listOf(
                order,
                order.copy(
                    id = 2L,
                    taker = FlowAddress(randomAddress()),
                ),
                order.copy(
                    id = 3L,
                    maker = FlowAddress(randomAddress())
                )
            )
        ).collectList().block()

        val response = client.get()
            .uri("/v0.1/order/items/byOwner?owner=${nftOwner.formatted}")
            .exchange()
            .expectStatus().isOk
            .expectBody(FlowNftItemsDto::class.java)
            .returnResult().responseBody!!

        Assertions.assertNotNull(response)
        Assertions.assertTrue(response.items.isNotEmpty())
        Assertions.assertTrue(response.items.size == 3)
    }


    @Test
    fun `should return all items on sale`() {
        val nftContract = randomAddress()
        val nftOwner = FlowAddress(randomAddress())
        val tokenId = 42L
        val nftId = ItemId(nftContract, tokenId)
        val item = Item(
            contract = nftContract,
            tokenId = tokenId,
            creator = nftOwner,
            royalties = listOf(),
            owner = nftOwner,
            mintedAt = Instant.now(Clock.systemUTC()),
            collection = "collection",
            updatedAt = Instant.now()
        )

        itemRepository.saveAll(
            listOf(item, item.copy(tokenId = 500L), item.copy(tokenId = 600L))
        ).then().block()

        val order = Order(
            id = 1L,
            itemId = nftId,
            maker = nftOwner,
            make = FlowAssetNFT(
                contract = nftContract,
                value = 1.toBigDecimal(),
                tokenId = tokenId
            ),
            amount = 1000.toBigDecimal(),
            data = OrderData(
                payouts = listOf(), originalFees = listOf()
            ),
            collection = item.collection,
            take = FlowAssetFungible(
                "FLOW",
                BigDecimal.TEN
            ),
            makeStock = BigDecimal.TEN,
            createdAt = LocalDateTime.now(ZoneOffset.UTC),
            type = OrderType.LIST
        )

        orderRepository.saveAll(
            listOf(
                order,
                order.copy(
                    id = 2L,
                    taker = FlowAddress(randomAddress()),
                ),
                order.copy(
                    id = 3L,
                    maker = FlowAddress(randomAddress()),
                    itemId = ItemId(contract = nftContract, tokenId = 500L)
                )
            )
        ).collectList().block()

        val response = client.get()
            .uri("/v0.1/order/items/all")
            .exchange()
            .expectStatus().isOk
            .expectBody(FlowNftItemsDto::class.java)
            .returnResult().responseBody!!

        Assertions.assertNotNull(response)
        Assertions.assertTrue(response.items.isNotEmpty())
        Assertions.assertTrue(response.items.size == 3)

    }


    @Test
    internal fun `should return all items by collection`() {
        val itemId1 = ItemId(contract = randomAddress(), tokenId = randomLong())
        val itemId2 = ItemId(contract = randomAddress(), tokenId = randomLong())
        val itemId3 = ItemId(contract = randomAddress(), tokenId = randomLong())

        val item = Item(
            contract = itemId1.contract,
            tokenId = itemId1.tokenId,
            creator = FlowAddress(randomAddress()),
            royalties = listOf(),
            owner = FlowAddress(randomAddress()),
            mintedAt = Instant.now(Clock.systemUTC()),
            collection = "CollectionNFT",
            updatedAt = Instant.now(),
        )

        itemRepository.saveAll(
            listOf(
                item,
                item.copy(contract = itemId2.contract, tokenId = itemId2.tokenId),
                item.copy(contract = itemId3.contract, tokenId = itemId3.tokenId),
                item.copy(contract = randomAddress(), tokenId = randomLong(), collection = "DIFF")
            )
        ).then().block()


        val order = Order(
            id = 1L,
            itemId = item.id,
            maker = FlowAddress(randomAddress()),
            make = FlowAssetNFT(
                contract = randomAddress(),
                BigDecimal.valueOf(1L),
                tokenId = randomLong()
            ),
            collection = item.collection,
            amount = BigDecimal.valueOf(100L),
            createdAt = LocalDateTime.now(ZoneOffset.UTC),
            data = OrderData(payouts = listOf(), originalFees = listOf()),
            take = FlowAssetFungible(
                "FLOW",
                BigDecimal.TEN
            ),
            makeStock = BigDecimal.TEN,
            type = OrderType.LIST
        )

        orderRepository.saveAll(
            listOf(
                order,
                order.copy(id = 2L, itemId = itemId2),
                order.copy(id = 3L, itemId = itemId3),
                order.copy(
                    id = 4L,
                    itemId = ItemId(contract = randomAddress(), tokenId = randomLong()),
                    collection = "DIFF"
                ),
            )
        ).then().block()

        client.get()
            .uri("/v0.1/order/items/byCollection?collection={collection}", mapOf("collection" to item.collection))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .consumeWith {
                Assertions.assertNotNull(it.responseBody)
                val items = it.responseBody!!.items
                Assertions.assertTrue(items.isNotEmpty())
                Assertions.assertTrue(items.size == 3)
            }
    }

    @Test
    internal fun showDeletedFlagTest() {
        val nftContract = randomAddress()
        var tokenId = randomLong()
        val item = Item(
            contract = nftContract,
            tokenId = tokenId,
            creator = FlowAddress(randomAddress()),
            royalties = listOf(),
            owner = FlowAddress(randomAddress()),
            mintedAt = Instant.now(Clock.systemUTC()),
            collection = "CollectionNFT",
            updatedAt = Instant.now()
        )

        itemRepository.saveAll(
            listOf(
                item,
                item.copy(tokenId = ++tokenId),
                item.copy(tokenId = ++tokenId, owner = null),
            )
        ).then().block()

        client.get().uri("/v0.1/order/items/all?showDeleted=false")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .consumeWith {
                Assertions.assertNotNull(it.responseBody)
                val items = it.responseBody!!.items
                Assertions.assertTrue(items.isNotEmpty(), "Items list is empty!")
                Assertions.assertEquals(2, items.size, "Items more or less than 2!")
                Assertions.assertTrue(items.all { !it.deleted })
            }

        client.get().uri("/v0.1/order/items/all?showDeleted=true")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .consumeWith {
                Assertions.assertNotNull(it.responseBody)
                val items = it.responseBody!!.items
                Assertions.assertTrue(items.isNotEmpty(), "Items list is empty!")
                Assertions.assertEquals(3, items.size, "Items more or less than 3!")
                val deleted = items.filter { it.deleted }
                val notDeleted = items.filterNot { it.deleted }
                Assertions.assertEquals(1, deleted.size)
                Assertions.assertEquals(2, notDeleted.size)
            }
    }

}
