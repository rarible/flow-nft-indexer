package com.rarible.flow.api.controller

import com.nftco.flow.sdk.FlowAddress
import com.ninjasquad.springmockk.MockkBean
import com.rarible.flow.api.service.NftItemService
import com.rarible.flow.core.converter.ItemToDtoConverter
import com.rarible.flow.core.domain.Item
import com.rarible.flow.core.domain.ItemId
import com.rarible.flow.core.domain.ItemMeta
import com.rarible.flow.core.domain.TokenId
import com.rarible.flow.core.repository.ItemFilter
import com.rarible.flow.core.repository.ItemMetaRepository
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.core.repository.NftItemContinuation
import com.rarible.flow.form.MetaForm
import com.rarible.flow.log.Log
import com.rarible.protocol.dto.FlowCreatorDto
import com.rarible.protocol.dto.FlowNftItemDto
import com.rarible.protocol.dto.FlowNftItemsDto
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.slot
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@WebFluxTest(
    controllers = [NftApiController::class],
    properties = [
        "application.environment = dev",
        "spring.cloud.service-registry.auto-registration.enabled = false",
        "spring.cloud.discovery.enabled = false",
        "spring.cloud.consul.config.enabled = false",
        "logging.logstash.tcp-socket.enabled = false"
    ]
)
@AutoConfigureWebTestClient(timeout = "60000")
@ActiveProfiles("test")
@Disabled("How to init NftItemService?!!1")
internal class NftApiControllerTest(
    @Autowired val client: WebTestClient
) {
    @MockkBean
    lateinit var itemRepository: ItemRepository

    @MockkBean
    lateinit var itemMetaRepository: ItemMetaRepository

    @Autowired
    lateinit var nftItemService: NftItemService

    @Test
    fun `should return all items and stop`() = runBlocking<Unit> {
        val items = listOf(
            createItem(),
            createItem(43).copy(mintedAt = Instant.now(Clock.systemUTC()).minus(1, ChronoUnit.DAYS))
        )


        coEvery {
            nftItemService.getAllItems(any(), any(), any())
        } returns FlowNftItemsDto(
            total = items.size,
            continuation = "",
            items = items.map(ItemToDtoConverter::convert)
        )

        val cont = NftItemContinuation(Instant.now(Clock.systemUTC()), ItemId("0x01", 42))
        var response = client
            .get()
            .uri("/v0.1/items/?continuation=$cont")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 2

        response = client
            .get()
            .uri("/v0.1/items/?continuation=$cont&size=2")
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 2

    }

    @Test
    fun `should return item by id`() {
        coEvery {
            nftItemService.getItemById(any())
        } returns ItemToDtoConverter.convert(createItem())

        val item = client
            .get()
            .uri("/v0.1/items/{itemId}", mapOf("itemId" to "0x01:42"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemDto>()
            .returnResult().responseBody!!
        item.id shouldBe "0x01:42"
        item.creators shouldBe listOf(FlowCreatorDto(FlowAddress("0x01").formatted, 10000.toBigDecimal()))
        item.owners shouldBe listOf(FlowAddress("0x02").formatted)
    }

    @Test
    fun `should return 404 by id`() {
        coEvery {
            nftItemService.getItemById(any())
        } returns null

        client
            .get()
            .uri("/v0.1/items/{itemId}", mapOf("itemId" to "0x01:43"))
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return items by owner`() {
        val items = listOf(
            createItem(),
            createItem(tokenId = 43).copy(owner = FlowAddress("0x03"))
        )
        val captured = slot<ItemFilter.ByOwner>()
        coEvery {
            nftItemService.byAccount(captured.captured.owner.formatted, any(), any())
        } coAnswers {
            FlowNftItemsDto(
                total = items.filter { it.owner == captured.captured.owner }.size,
                items = items.filter { it.owner == captured.captured.owner }.map(ItemToDtoConverter::convert),
                continuation = ""
            )
        }


        var response = client
            .get()
            .uri("/v0.1/items/byAccount?address={address}", mapOf("address" to "0x02"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 1
        response.items[0].owners shouldBe listOf(items[0].owner!!.formatted)

        response = client
            .get()
            .uri("/v0.1/items/byAccount?address={address}", mapOf("address" to "0x03"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 1
        response.items[0].owners shouldBe listOf(items[1].owner!!.formatted)

        response = client
            .get()
            .uri("/v0.1/items/byAccount?address={address}", mapOf("address" to "0x04"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 0

    }

    @Test
    fun `should return items by creator`() {
        val items = listOf(
            createItem(),
            createItem(tokenId = 43).copy(creator = FlowAddress("0x02"))
        )

        coEvery {
           nftItemService.byCreator(any(), any(), any())
        } coAnswers {
            FlowNftItemsDto(
                total = items.filter { it.owner == FlowAddress(arg(0)) }.size,
                items = items.filter { it.owner == FlowAddress(arg(0)) }.map(ItemToDtoConverter::convert),
                continuation = ""
            )
        }

        var respose = client
            .get()
            .uri("/v0.1/items/byCreator?address={address}", mapOf("address" to "0x01"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        respose.items shouldHaveSize 1
        respose.items[0].owners shouldBe listOf(items[0].owner!!.formatted)

        respose = client
            .get()
            .uri("/v0.1/items/byCreator?address={address}", mapOf("address" to "0x02"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        respose.items shouldHaveSize 1
        respose.items[0].owners shouldBe listOf(items[1].owner!!.formatted)

        respose = client
            .get()
            .uri("/v0.1/items/byCreator?address={address}", mapOf("address" to "0x04"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        respose.items shouldHaveSize 0


    }

    @Test
    fun `should create meta and return link`() {
        every {
            itemRepository.findById(any<ItemId>())
        } returns Mono.just(createItem())

        val itemCapture = slot<Item>()
        every {
            itemRepository.save(capture(itemCapture))
        } answers {
            Mono.just(itemCapture.captured)
        }

        every {
            itemMetaRepository.findById(any<ItemId>())
        } returns Mono.empty()

        val metaCapture = slot<ItemMeta>()
        every {
            itemMetaRepository.save(capture(metaCapture))
        } answers {
            Mono.just(metaCapture.captured)
        }

        client
            .post()
            .uri("/v0.1/items/meta/0x01:1")
            .bodyValue(
                MetaForm(
                    "title",
                    "description",
                    URI.create("https://keyassets.timeincuk.net/inspirewp/live/wp-content/uploads/sites/34/2021/03/pouring-wine-zachariah-hagy-8_tZ-eu32LA-unsplash-1-920x609.jpg")
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody<String>().isEqualTo("/v0.1/items/meta/0x01:1")
    }

    @Test
    fun `should return items by collection`() {
        val items = listOf(
            createItem(),
            createItem(tokenId = 43).copy(collection = "different collection")
        )
        val captured = slot<ItemFilter.ByCollection>()
        coEvery {
            itemRepository.search(capture(captured), null, any())
        } coAnswers {
            items.filter { it.collection == captured.captured.collectionId }.asFlow()
        }

        var response = client
            .get()
            .uri("/v0.1/items/byCollection?collection={collection}", mapOf("collection" to "collection"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 1
        response.items[0].collection shouldBe "collection"

        response = client
            .get()
            .uri("/v0.1/items/byCollection?collection={collection}", mapOf("collection" to "different collection"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 1
        response.items[0].collection shouldBe "different collection"

        response = client
            .get()
            .uri("/v0.1/items/byCollection?collection={collection}", mapOf("collection" to "unsupported collection"))
            .exchange()
            .expectStatus().isOk
            .expectBody<FlowNftItemsDto>()
            .returnResult().responseBody!!
        response.items shouldHaveSize 0


    }


    private fun createItem(tokenId: TokenId = 42) = Item(
        "0x01",
        tokenId,
        FlowAddress("0x01"),
        emptyList(),
        FlowAddress("0x02"),
        Instant.now(Clock.systemUTC()),
        collection = "collection",
        updatedAt = Instant.now()
    )

    companion object {
        val log by Log()
    }
}
