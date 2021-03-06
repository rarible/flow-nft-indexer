package com.rarible.flow.core.converter

import com.rarible.flow.core.domain.ItemMeta
import com.rarible.protocol.dto.FlowAudioContentDto
import com.rarible.protocol.dto.FlowHtmlContentDto
import com.rarible.protocol.dto.FlowImageContentDto
import com.rarible.protocol.dto.FlowMetaAttributeDto
import com.rarible.protocol.dto.FlowMetaContentItemDto
import com.rarible.protocol.dto.FlowMetaDto
import com.rarible.protocol.dto.FlowModel3dContentDto
import com.rarible.protocol.dto.FlowUnknownContentDto
import com.rarible.protocol.dto.FlowVideoContentDto
import org.springframework.core.convert.converter.Converter
import java.util.*

object ItemMetaToDtoConverter : Converter<ItemMeta, FlowMetaDto> {

    override fun convert(source: ItemMeta): FlowMetaDto {
        return FlowMetaDto(
            name = source.name,
            description = source.description,
            createdAt = source.createdAt,
            tags = source.tags,
            genres = source.genres,
            language = source.language,
            rights = source.rights,
            rightsUrl = source.rightsUrl,
            externalUri = source.externalUri,
            originalMetaUri = source.originalMetaUri,
            attributes = source.attributes.map {
                FlowMetaAttributeDto(
                    key = it.key, value = it.value, format = it.format, type = it.type
                )
            },
            contents = source.contentUrls,
            content = source.content?.map(::convert),
            raw = source.raw?.let { Base64.getEncoder().encodeToString(it) })
    }

    private fun convert(source: ItemMeta.Content) = when (source.type) {
        ItemMeta.Content.Type.IMAGE -> FlowImageContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
            width = source.width,
            height = source.height,
        )
        ItemMeta.Content.Type.VIDEO -> FlowVideoContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
            width = source.width,
            height = source.height,
        )
        ItemMeta.Content.Type.AUDIO -> FlowAudioContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
        )
        ItemMeta.Content.Type.MODEL_3D -> FlowModel3dContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
        )
        ItemMeta.Content.Type.HTML -> FlowHtmlContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
        )
        ItemMeta.Content.Type.UNKNOWN -> FlowUnknownContentDto(
            fileName = source.fileName,
            url = source.url,
            representation = convert(source.representation),
            mimeType = source.mimeType,
            size = source.size,
        )
    }

    private fun convert(source: ItemMeta.Content.Representation): FlowMetaContentItemDto.Representation =
        source.name.let { FlowMetaContentItemDto.Representation.valueOf(it) }
}
