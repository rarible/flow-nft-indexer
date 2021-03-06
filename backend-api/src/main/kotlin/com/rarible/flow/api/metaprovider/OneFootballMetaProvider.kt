package com.rarible.flow.api.metaprovider

import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.cadence.*
import com.rarible.flow.Contracts
import com.rarible.flow.api.service.ScriptExecutor
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.ItemRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
class OneFootballMetaScript(
    val scriptExecutor: ScriptExecutor,
    @Value("classpath:script/one_football_meta.cdc")
    val script: Resource
) {
    suspend fun call(owner: FlowAddress, tokenId: TokenId): MetaBody? {
        return scriptExecutor.executeFile(
            script,
            {
                arg { address(owner.formatted) }
                arg { uint64(tokenId) }
            },
            { response ->
                response as OptionalField
                response.value?.let {
                    Flow.unmarshall(OneFootballMeta::class, it)
                }
            }
        )
    }
}

@Component
class OneFootballMetaProvider(
    val itemRepository: ItemRepository,
    val metaScript: OneFootballMetaScript
): ItemMetaProvider {

    override fun isSupported(itemId: ItemId): Boolean = Contracts.ONE_FOOTBALL.supports(itemId)

    override suspend fun getMeta(item: Item): ItemMeta? {
        return metaScript.call(item.owner ?: item.creator, item.tokenId)
            ?.toItemMeta(item.id)

    }
}

@JsonCadenceConversion(OneFootballMetaConverter::class)
data class OneFootballMeta(
    val id: Long,
    val templateID: Long,
    val seriesName: String,
    val name: String,
    val description: String,
    val preview: String,
    val media: String,
    val data: Map<String, String>,
): MetaBody {
    override fun toItemMeta(itemId: ItemId): ItemMeta {
        return ItemMeta(
            itemId = itemId,
            name = name,
            description = description,
            attributes = data.map { (k, v) ->
                ItemMetaAttribute(k, v)
            },
            contentUrls = listOf(
                media,
                preview
            ),
            content = listOf(
                ItemMeta.Content(
                    media,
                    ItemMeta.Content.Representation.ORIGINAL,
                    ItemMeta.Content.Type.IMAGE,
                ),
                ItemMeta.Content(
                    preview,
                    ItemMeta.Content.Representation.PREVIEW,
                    ItemMeta.Content.Type.IMAGE,
                )
            )
        )
    }
}

class OneFootballMetaConverter: JsonCadenceConverter<OneFootballMeta> {
    override fun unmarshall(value: Field<*>, namespace: CadenceNamespace): OneFootballMeta {
        return com.nftco.flow.sdk.cadence.unmarshall(value) {
            OneFootballMeta(
                id = long("id"),
                templateID = long("templateID"),
                seriesName = string("seriesName"),
                name = string("name"),
                description = string("description"),
                preview = string("preview"),
                media = string("media"),
                data = dictionaryMap("data") { k, v ->
                    string(k) to string(v)
                }
            )
        }
    }
}

