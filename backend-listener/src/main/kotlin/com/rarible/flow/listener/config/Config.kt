package com.rarible.flow.listener.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.rarible.core.daemon.sequential.ConsumerEventHandler
import com.rarible.core.daemon.sequential.ConsumerWorker
import com.rarible.core.kafka.RaribleKafkaConsumer
import com.rarible.core.kafka.RaribleKafkaProducer
import com.rarible.core.kafka.json.JsonDeserializer
import com.rarible.core.kafka.json.JsonSerializer
import com.rarible.flow.core.config.CoreConfig
import com.rarible.flow.core.repository.*
import com.rarible.flow.events.EventMessage
import com.rarible.flow.json.commonMapper
import com.rarible.flow.listener.handler.EventHandler
import com.rarible.flow.listener.handler.ProtocolEventPublisher
import com.rarible.protocol.dto.FlowNftItemEventDto
import com.rarible.protocol.dto.FlowNftItemEventTopicProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(CoreConfig::class)
@EnableConfigurationProperties(ListenerProperties::class)
class Config(
    private val listenerProperties: ListenerProperties
) {

    @Bean
    fun eventConsumer(): RaribleKafkaConsumer<EventMessage> {
        return RaribleKafkaConsumer(
            clientId = "${listenerProperties.environment}.flow.nft-scanner.nft-indexer-item-events-consumer",
            valueDeserializerClass = JsonDeserializer::class.java,
            valueClass = EventMessage::class.java,
            consumerGroup = "flow-listener",
            defaultTopic = EventMessage.getTopic(listenerProperties.environment),
            bootstrapServers = listenerProperties.kafkaReplicaSet
        )
    }

    @Bean
    fun eventMessageHandler(
        itemRepository: ItemRepository,
        ownershipRepository: OwnershipRepository,
        orderRepository: OrderRepository,
        itemReactiveRepository: ItemReactiveRepository,
        orderReactiveRepository: OrderReactiveRepository,
        protocolEventPublisher: ProtocolEventPublisher
    ): ConsumerEventHandler<EventMessage> {
        return EventHandler(
            itemRepository,
            itemReactiveRepository,
            ownershipRepository,
            orderRepository,
            orderReactiveRepository,
            protocolEventPublisher
        )
    }

    @Bean
    fun eventConsumerWorker(
        eventConsumer: RaribleKafkaConsumer<EventMessage>,
        eventMessageHandler: ConsumerEventHandler<EventMessage>
    ): ConsumerWorker<EventMessage> {
        return ConsumerWorker(
            eventConsumer,
            eventMessageHandler,
            "flow-event-message-consumer-worker"
        )
    }

    @Bean
    fun objectMapper(): ObjectMapper = commonMapper()

    @Bean
    fun gatewayEventsProducer(): RaribleKafkaProducer<FlowNftItemEventDto> {
        return RaribleKafkaProducer(
            clientId = "${listenerProperties.environment}.flow.nft-events-importer",
            valueSerializerClass = JsonSerializer::class.java,
            defaultTopic = FlowNftItemEventTopicProvider.getTopic(listenerProperties.environment),
            bootstrapServers = listenerProperties.kafkaReplicaSet
        )
    }

    @Bean
    fun protocolEventPublisher(
        gatewayEventsProducer: RaribleKafkaProducer<FlowNftItemEventDto>
    ) = ProtocolEventPublisher(gatewayEventsProducer)

}

