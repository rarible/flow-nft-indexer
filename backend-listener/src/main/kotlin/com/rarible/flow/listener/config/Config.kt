package com.rarible.flow.listener.config

import com.rarible.core.daemon.sequential.ConsumerEventHandler
import com.rarible.core.daemon.sequential.ConsumerWorker
import com.rarible.core.kafka.RaribleKafkaConsumer
import com.rarible.core.kafka.json.JsonDeserializer
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.core.repository.OwnershipRepo
import com.rarible.flow.events.EventMessage
import com.rarible.flow.events.NftEvent
import com.rarible.flow.listener.handler.EventHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ListenerProperties::class)
class Config(
    private val listenerProperties: ListenerProperties,
    //private val meterRegistry: MeterRegistry
) {

    @Bean
    fun eventConsumer(): RaribleKafkaConsumer<NftEvent> {
        return RaribleKafkaConsumer(
            clientId = "${listenerProperties.environment}.flow.nft-scanner.nft-indexer-item-events-consumer",
            valueDeserializerClass = JsonDeserializer::class.java,
            valueClass = NftEvent::class.java,
            consumerGroup = "flow-listener",
            defaultTopic = EventMessage.getTopic(listenerProperties.environment),
            bootstrapServers = listenerProperties.kafkaReplicaSet
        )
    }

    @Bean
    fun eventMessageHandler(
        itemRepository: ItemRepository,
        ownershipRepo: OwnershipRepo
    ): ConsumerEventHandler<NftEvent> {
        return EventHandler(itemRepository, ownershipRepo)
    }

    @Bean
    fun eventConsumerWorker(
        eventConsumer: RaribleKafkaConsumer<NftEvent>,
        eventMessageHandler: ConsumerEventHandler<NftEvent>
    ): ConsumerWorker<NftEvent> {
        return ConsumerWorker(
            eventConsumer,
            eventMessageHandler,
            "flow-event-message-consumer-worker"
        )
    }

}
