package com.rarible.flow.listener.handler

import com.rarible.core.kafka.KafkaMessage
import com.rarible.core.kafka.KafkaSendResult
import com.rarible.core.kafka.RaribleKafkaProducer
import com.rarible.flow.core.converter.ItemToDtoConverter
import com.rarible.flow.core.domain.Item
import com.rarible.protocol.dto.*
import java.util.*


class ProtocolEventPublisher(
    val gatewayKafkaProducer: RaribleKafkaProducer<FlowNftItemEventDto>
) {

    suspend fun onItemUpdate(item: Item): KafkaSendResult {
        return gatewayKafkaProducer.send(
            KafkaMessage(
                item.id,
                FlowNftItemUpdateEventDto(
                    eventId = "${item.id}.${UUID.randomUUID()}",
                    itemId = item.id,
                    ItemToDtoConverter.convert(item)

                )
            )
        )
    }

    suspend fun onItemDelete(item: Item): KafkaSendResult {
        return gatewayKafkaProducer.send(
            KafkaMessage(
                item.id,
                FlowNftItemDeleteEventDto(
                    eventId = "${item.id}.${UUID.randomUUID()}",
                    itemId = item.id,
                    FlowNftDeletedItemDto(
                        item.id,
                        item.contract,
                        item.tokenId.toInt()
                    )
                )
            )
        )
    }
}