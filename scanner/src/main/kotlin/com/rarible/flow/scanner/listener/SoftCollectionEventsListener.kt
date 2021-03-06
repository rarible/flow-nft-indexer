package com.rarible.flow.scanner.listener

import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.cadence.*
import com.rarible.blockchain.scanner.flow.model.FlowLog
import com.rarible.blockchain.scanner.flow.model.FlowLogRecord
import com.rarible.blockchain.scanner.flow.subscriber.FlowLogEventListener
import com.rarible.blockchain.scanner.subscriber.ProcessedBlockEvent
import com.rarible.flow.Contracts
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.ItemCollectionRepository
import com.rarible.flow.core.repository.coFindById
import com.rarible.flow.core.repository.coSave
import com.rarible.flow.log.Log
import com.rarible.flow.scanner.model.parse
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class SoftCollectionEventsListener(
    private val itemCollectionRepository: ItemCollectionRepository,
    @Value("\${blockchain.scanner.flow.chainId}")
    private val chainId: FlowChainId
) : FlowLogEventListener {

    private val parser = JsonCadenceParser()

    private val logger by Log()

    private val idDelimiter = '.'

    override suspend fun onBlockLogsProcessed(blockEvent: ProcessedBlockEvent<FlowLog, FlowLogRecord<*>>) {
        blockEvent.records.filterIsInstance<FlowLogEvent>()
            .filter {
                it.type in setOf(FlowLogType.COLLECTION_MINT,
                    FlowLogType.COLLECTION_WITHDRAW,
                    FlowLogType.COLLECTION_DEPOSIT,
                    FlowLogType.COLLECTION_CHANGE,
                    FlowLogType.COLLECTION_BURN)
            }.forEach { event ->
                when (event.type) {
                    FlowLogType.COLLECTION_MINT -> createSoftCollection(event)
                    FlowLogType.COLLECTION_CHANGE -> updateSoftCollection(event)
                    FlowLogType.COLLECTION_DEPOSIT -> depositSoftCollection(event)
                    FlowLogType.COLLECTION_BURN -> burnSoftCollection(event)
                    else -> {}
                }
            }
    }

    private fun FlowLogEvent.collectionId() =
        "${ItemId(Contracts.SOFT_COLLECTION.fqn(chainId), parser.long(event.fields["id"]!!), idDelimiter)}"

    private suspend fun FlowLogEvent.getCollection() =
        itemCollectionRepository.coFindById(collectionId())
            ?: throw IllegalStateException("Collection with id [${collectionId()}] not found")

    private suspend fun burnSoftCollection(event: FlowLogEvent) {
        val entity = event.getCollection()
        itemCollectionRepository.coSave(entity.copy(burned = true))
    }

    private suspend fun depositSoftCollection(event: FlowLogEvent) {
        val to = event.event.fields["to"]?.let { parser.optional(it, JsonCadenceParser::address) }
        if (to != null) {
            val entity = event.getCollection()
            itemCollectionRepository.coSave(entity.copy(owner = FlowAddress(to)))
        }
    }

    private suspend fun createSoftCollection(event: FlowLogEvent) {
        try {
            val id by event.event.fields
            val parentId by event.event.fields
            val meta by event.event.fields
            val creator by event.event.fields
            val royalties by event.event.fields

            val creatorAddress = FlowAddress(parser.address(creator))
            val collectionMeta = meta.parse<CollectionMeta>()
            val collectionChainId = parser.long(id)
            val itemCollection = ItemCollection(
                id = "${ItemId(Contracts.SOFT_COLLECTION.fqn(chainId), collectionChainId, idDelimiter)}",
                owner = creatorAddress,
                name = collectionMeta.name,
                symbol = collectionMeta.symbol,
                createdDate = event.log.timestamp,
                features = setOf("BURN", "SECONDARY_SALE_FEES"),
                chainId = collectionChainId,
                chainParentId = parser.optional(parentId, JsonCadenceParser::long),
                royalties = parseRoyalties(royalties),
                isSoft = true,
                description = collectionMeta.description,
                icon = collectionMeta.icon,
                url = collectionMeta.url
            )
            itemCollectionRepository.save(itemCollection).awaitSingleOrNull()
        } catch (e: Exception) {
            logger.error("Unable to create soft collection! ${e.message}", e)
            throw Throwable(e.message, e)
        }
    }

    fun parseRoyalties(royalties: Field<*>): List<Part> {
        return when (royalties) {
            is ArrayField ->
                parser.arrayValues(royalties) { arrValue ->
                    val r = this.unmarshall(
                        arrValue,
                        SoftCollectionRoyalty::class,
                        Contracts.SOFT_COLLECTION.deployments[chainId]!!
                    )
                    Part(
                        address = FlowAddress(r.address),
                        fee = r.fee.toDouble()
                    )
                }
            is OptionalField -> royalties.value?.let(::parseRoyalties) ?: emptyList()
            else -> {
                logger.error("Trying to parseRoyalties from unknown field type {} ({})", royalties.type, royalties.value)
                emptyList()
            }
        }
    }

    suspend fun updateSoftCollection(event: FlowLogEvent) {
        val id by event.event.fields
        val meta by event.event.fields
        val royalties = event.event.fields["royalties"]?.let(::parseRoyalties)

        val collectionMeta = meta.parse<CollectionMeta>()
        val collectionId = "${ItemId(Contracts.SOFT_COLLECTION.fqn(chainId), parser.long(id), idDelimiter)}"
        val entity = itemCollectionRepository.coFindById(collectionId) ?: throw IllegalStateException("Collection with id [$collectionId] not found")

        itemCollectionRepository.coSave(entity.copy(
            name = collectionMeta.name,
            symbol = collectionMeta.symbol,
            icon = collectionMeta.icon,
            description = collectionMeta.description,
            url = collectionMeta.url,
            royalties = royalties ?: entity.royalties,
        ))
    }

    override suspend fun onPendingLogsDropped(logs: List<FlowLogRecord<*>>) {
        /** do nothing*/
    }
}

@JsonCadenceConversion(CollectionMetaConversion::class)
data class CollectionMeta(
    val name: String,
    val symbol: String,
    val icon: String?,
    val description: String?,
    val url: String?
)

class CollectionMetaConversion: JsonCadenceConverter<CollectionMeta> {
    override fun unmarshall(value: Field<*>, namespace: CadenceNamespace): CollectionMeta = unmarshall(value) {
        CollectionMeta(
            name = string("name"),
            symbol = string("symbol"),
            description = optional("description", JsonCadenceParser::string),
            icon = optional("icon", JsonCadenceParser::string),
            url = optional("url", JsonCadenceParser::string),
        )
    }
}

@JsonCadenceConversion(SoftCollectionRoyaltyConverter::class)
data class SoftCollectionRoyalty(
    val address: String,
    val fee: BigDecimal,
)

class SoftCollectionRoyaltyConverter : JsonCadenceConverter<SoftCollectionRoyalty> {
    override fun unmarshall(value: Field<*>, namespace: CadenceNamespace): SoftCollectionRoyalty = unmarshall(value) {
        SoftCollectionRoyalty(address("address"), bigDecimal("fee"))
    }

    override fun marshall(value: SoftCollectionRoyalty, namespace: CadenceNamespace): Field<*> = marshall {
        struct {
            compositeOfPairs(namespace.withNamespace("SoftCollection.Royalty")) {
                listOf(
                    "address" to address(value.address),
                    "fee" to ufix64(value.fee),
                )
            }
        }
    }
}
