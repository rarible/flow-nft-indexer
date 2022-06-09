package com.rarible.flow.api.metaprovider

import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.cadence.CadenceNamespace
import com.nftco.flow.sdk.cadence.Field
import com.nftco.flow.sdk.cadence.JsonCadenceBuilder
import com.nftco.flow.sdk.cadence.JsonCadenceConversion
import com.nftco.flow.sdk.cadence.JsonCadenceConverter
import com.nftco.flow.sdk.cadence.OptionalField
import com.nftco.flow.sdk.cadence.StructField
import com.rarible.flow.Contracts
import com.rarible.flow.api.service.ScriptExecutor
import com.rarible.flow.core.domain.ItemId
import com.rarible.flow.core.domain.ItemMeta
import com.rarible.flow.core.domain.ItemMetaAttribute
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.core.repository.coFindById
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
class MatrixWorldFlowFestMetaScript(
    @Value("classpath:script/matrix_flow_fest_meta.cdc")
    private val scriptFile: Resource,
    private val scriptExecutor: ScriptExecutor
) {
    suspend fun call(owner: FlowAddress, tokenId: TokenId): MatrixWorldFlowFestNftMeta? {
        return scriptExecutor.executeFile(
            scriptFile,
            {
                arg { address(owner.formatted) }
                arg { uint64(tokenId) }
            },
            { json ->
                if(json.value == null) null
                else Flow.unmarshall(MatrixWorldFlowFestNftMeta::class, json.value as StructField)
            }
        )
    }
}

@Component
class MatrixWorldFlowFestMetaProvider(
    private val matrixWorldFlowFestMetaScript: MatrixWorldFlowFestMetaScript,
): ItemMetaProvider {

    override suspend fun getMeta(item: Item): ItemMeta? {
        return matrixWorldFlowFestMetaScript
            .call(item.owner!!, item.tokenId)
            ?.toItemMeta(item.id)
    }

    override fun isSupported(itemId: ItemId): Boolean = Contracts.MATRIX_WORLD_FLOW_FEST.supports(itemId)

}

@JsonCadenceConversion(MatrixWorldFlowFestNftMetaConverter::class)
data class MatrixWorldFlowFestNftMeta(
    val name: String,
    val description: String,
    val animationUrl: String,
    val type: String
): MetaBody {

    override fun toItemMeta(itemId: ItemId): ItemMeta {
        return ItemMeta(
            itemId = itemId,
            name = name,
            description = description,
            attributes = listOf(
                ItemMetaAttribute(key = "type", value = type)
            ),
            contentUrls = listOf(
                animationUrl
            ),
            content = listOf(
                ItemMeta.Content(
                    meta.animationUrl,
                    ItemMeta.Content.Representation.ORIGINAL,
                    ItemMeta.Content.Type.VIDEO,
                )
            )
        ).apply {
            raw = toString().toByteArray(Charsets.UTF_8)
        }
    }
}

class MatrixWorldFlowFestNftMetaConverter: JsonCadenceConverter<MatrixWorldFlowFestNftMeta> {
    override fun unmarshall(value: Field<*>, namespace: CadenceNamespace): MatrixWorldFlowFestNftMeta {
        return unmarshall(value) {
            MatrixWorldFlowFestNftMeta(
                name = string(compositeValue.getRequiredField("name")),
                description = string(compositeValue.getRequiredField("description")),
                animationUrl = string(compositeValue.getRequiredField("animationUrl")),
                type = string(compositeValue.getRequiredField("type"))
            )
        }
    }
}
