package com.rarible.flow.api.controller

import com.rarible.flow.core.domain.Item
import com.rarible.flow.core.domain.ItemMeta
import com.rarible.flow.core.repository.ItemMetaRepository
import com.rarible.flow.core.repository.ItemReactiveRepository
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.form.MetaForm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@CrossOrigin
@RestController
@RequestMapping(value = [
    "/v0.1/items"
])
class NftApiController(
    private val itemRepository: ItemRepository,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val itemReactiveRepository: ItemReactiveRepository,
    private val itemMetaRepository: ItemMetaRepository
) {

    @GetMapping("/")
    suspend fun findAll(): Flow<Item> {
        return itemRepository.findAll()
    }

    @GetMapping("/byAccount")
    suspend fun findByAccount(address: String): Flow<Item> {
        return itemRepository.findAllByAccount(address)
    }

    @GetMapping("/byCreator")
    suspend fun findByCreator(address: String): Flow<Item> {
        return itemRepository.findAllByCreator(address)
    }

    @GetMapping("/listed")
    suspend fun findListed(): Flow<Item> {
        return itemRepository.findAllListed()
    }

    @PostMapping("/meta/{itemId}")
    suspend fun createMeta(
        @PathVariable("itemId") itemId: String,
        @RequestBody form: MetaForm
    ): String? {
        val existing = itemMetaRepository.findByItemId(itemId)
        if(existing == null) {
            itemMetaRepository.save(
                ItemMeta(itemId, form.title, form.description, form.uri)
            )
        } else {
            itemMetaRepository.save(
                existing.copy(
                    title = form.title,
                    description = form.description,
                    uri = form.uri
                )
            )
        }

        return "/v0.1/items/meta/$itemId"
    }

    @GetMapping("/meta/{itemId}")
    suspend fun getMeta(
        @PathVariable("itemId") itemId: String,
    ): ItemMeta? {
        return itemMetaRepository.findByItemId(itemId)
    }

    @GetMapping("/getNftItemById/{itemId}")
    suspend fun getNftItemById(@PathVariable itemId: String): Item? =
        itemReactiveRepository.findById(itemId).awaitSingleOrNull()
}
