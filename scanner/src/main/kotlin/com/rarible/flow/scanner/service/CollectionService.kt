package com.rarible.flow.scanner.service

import com.nftco.flow.sdk.FlowChainId
import com.rarible.core.task.Task
import com.rarible.core.task.TaskStatus
import com.rarible.flow.Contracts
import com.rarible.flow.core.domain.FlowLogEvent
import com.rarible.flow.core.domain.Item
import com.rarible.flow.core.domain.ItemHistory
import com.rarible.flow.core.domain.NFTActivity
import com.rarible.flow.core.domain.Ownership
import com.rarible.flow.events.EventId
import com.rarible.flow.events.EventMessage
import com.rarible.flow.log.Log
import com.rarible.flow.scanner.subscriber.flowDescriptorName
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mapping.div
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.remove
import org.springframework.stereotype.Service

@Service
class CollectionService(
    private val mongo: ReactiveMongoTemplate
) {

    suspend fun purgeCollectionHistory(contract: Contracts, chainId: FlowChainId, startBlock: Long) {
        purgeItemHistory(contract, chainId)
        purgeLogEvents(contract, chainId)
        restartDescriptor(contract, startBlock)
        purgeItems(contract, chainId)
        purgeOwnerships(contract, chainId)
    }

    suspend fun purgeOwnerships(contract: Contracts, chainId: FlowChainId) {
        val removed = mongo.remove<Ownership>(
            Query(
                Ownership::contract isEqualTo contract.fqn(chainId)
            )
        ).awaitFirstOrNull()

        if (removed == null || !removed.wasAcknowledged()) {
            logger.warn("Failed to delete ownerships for contract {}", contract)
        } else {
            logger.info("Deleted ownerships: {}", removed.deletedCount)
        }
    }

    suspend fun purgeItems(contract: Contracts, chainId: FlowChainId) {
        val removed = mongo.remove<Item>(
            Query(
                Item::contract isEqualTo contract.fqn(chainId)
            )
        ).awaitFirstOrNull()

        if (removed == null || !removed.wasAcknowledged()) {
            logger.warn("Failed to delete items for contract {}", contract)
        } else {
            logger.info("Deleted items: {}", removed.deletedCount)
        }
    }

    suspend fun purgeItemHistory(contract: Contracts, chainId: FlowChainId) {
        val query = Query(
            Criteria(
                "${ItemHistory::activity.name}.${NFTActivity::contract.name}"
            ).isEqualTo(contract.fqn(chainId))
        )
        val removed = mongo.remove<ItemHistory>(query).awaitFirstOrNull()
        if (removed == null || !removed.wasAcknowledged()) {
            logger.warn("Failed to delete item_history for contract {}", contract)
        } else {
            logger.info("Deleted item_history: {}", removed.deletedCount)
        }
    }

    suspend fun purgeLogEvents(contract: Contracts, chainId: FlowChainId) {
        val removed = mongo.remove<FlowLogEvent>(
            Query(
                Criteria("event.eventId.contractName").isEqualTo(contract.contractName)
            )
        ).awaitFirstOrNull()

        if (removed == null || !removed.wasAcknowledged()) {
            logger.warn("Failed to delete flow_log_event for contract {}", contract)
        } else {
            logger.info("Deleted flow_log_event: {}", removed.deletedCount)
        }
    }

    suspend fun restartDescriptor(contract: Contracts, startBlock: Long) {
        mongo.updateFirst(
            Query(
                Task::param isEqualTo contract.flowDescriptorName()
            ),
            Update.update(
                Task::state.name, startBlock
            ).set(
                Task::lastStatus.name, TaskStatus.NONE
            ).set(
                Task::running.name, false
            ).set(
                Task::version.name, 0
            ).set(
                Task::lastError.name, null
            )
            ,
            Task::class.java
        ).awaitFirstOrNull()
    }

    companion object {
        val logger by Log()
    }
}