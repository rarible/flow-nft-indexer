package com.rarible.flow.core.domain

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.springframework.data.mongodb.core.mapping.MongoId

/**
 * Represents NFT Items' meta information
 * @property itemId         - ID of an NFT item (address:tokenId)
 * @property name           - item's card title
 * @property description    - description
 * @property attributes     - item attributes
 * @property contentUrls    - list of content URL's
 *
 */
@Document
data class ItemMeta(
    @MongoId(FieldType.STRING)
    val itemId: ItemId,
    val name: String,
    val description: String,
    val attributes: List<ItemMetaAttribute>,
    val contentUrls: List<String>,
) {
    @Field(targetType = FieldType.BINARY)
    var raw: ByteArray? = null

    @Field(targetType = FieldType.STRING)
    var base64: String? = null

    companion object {
        fun empty(itemId: ItemId): ItemMeta = ItemMeta(
            itemId = itemId,
            name = "Untitled",
            description = "",
            attributes = emptyList(),
            contentUrls = emptyList()
        )
    }
}

data class ItemMetaAttribute(
    val key: String,
    val value: String?,
    val type: String? = null,
    val format: String? = null
)
