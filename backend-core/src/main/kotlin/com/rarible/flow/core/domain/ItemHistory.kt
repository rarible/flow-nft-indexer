package com.rarible.flow.core.domain

import com.querydsl.core.annotations.QueryEmbedded
import com.querydsl.core.annotations.QueryEntity
import com.rarible.blockchain.scanner.flow.model.FlowLog
import com.rarible.blockchain.scanner.flow.model.FlowLogRecord
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.springframework.data.mongodb.core.mapping.MongoId
import java.time.Instant

/**
 * NFT Item history (item and order activities)
 * @property id         ID
 * @property date       date of activity
 * @property activity   activity data (see [FlowNftActivity])
 */
@Document("item_history")
@QueryEntity
@CompoundIndexes(
    CompoundIndex(
        name = "activity_siblings",
        def = "{'activity.type': 1, 'activity.contract': 1, 'activity.tokenId': 1, 'log.transactionHash': 1, 'log.eventIndex': 1, }"
    ),
    CompoundIndex(
        name = "log_uniq",
        def = "{'log.transactionHash': 1, 'log.eventIndex': 1,}",
        unique = true
    )
)
data class ItemHistory(
    @Indexed(direction = IndexDirection.DESCENDING)
    @Field(targetType = FieldType.DATE_TIME)
    val date: Instant,
    val activity: BaseActivity,
    @QueryEmbedded
    override val log: FlowLog,
    @MongoId(FieldType.STRING)
    val id: String = "${log.transactionHash}.${log.eventIndex}"
): FlowLogRecord<ItemHistory>() {
    override fun withLog(log: FlowLog): FlowLogRecord<ItemHistory> = copy(log = log)
}

