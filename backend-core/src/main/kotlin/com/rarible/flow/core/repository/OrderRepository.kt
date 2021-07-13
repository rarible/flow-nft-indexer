package com.rarible.flow.core.repository

import com.rarible.flow.core.domain.Address
import com.rarible.flow.core.domain.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.*
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo


class OrderRepository(
    private val mongo: ReactiveMongoTemplate
) {
    fun findAll(): Flow<Order> {
        return mongo.findAll<Order>().asFlow()
    }

    suspend fun findById(id: String): Order? {
        return mongo.findById<Order>(id).awaitFirstOrNull()
    }

    fun findByItemId(id: String): Flow<Order> {
        return mongo.find<Order>(
            Query.query(
                Order::itemId isEqualTo id
            )
        ).asFlow()
    }

    fun findAllByAccount(account: String): Flow<Order> {
        return mongo.find<Order>(
            Query.query(
                Order::taker isEqualTo Address(account)
            )
        ).asFlow()
    }

    suspend fun delete(id: String): Order? {
        return mongo.findAndRemove<Order>(
            Query.query(
                Order::id isEqualTo id
            )
        ).awaitFirstOrNull()
    }

    suspend fun save(item: Order): Order? {
        return mongo.save(item).awaitFirst();
    }
}
