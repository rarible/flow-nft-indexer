package com.rarible.flow.core.repository

import com.rarible.flow.core.domain.Address
import com.rarible.flow.core.domain.Ownership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow

import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAllAndRemove
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo


class OwnershipRepository(
    private val mongo: ReactiveMongoTemplate
) {
    suspend fun deleteAllByContractAndTokenId(contract: Address, tokenId: Int): Flow<Ownership> {
        return mongo.findAllAndRemove<Ownership>(
            byContractAndTokenId(contract, tokenId)
        ).asFlow()
    }

    suspend fun findAllByContractAndTokenId(contract: Address, tokenId: Int): Flow<Ownership> {
        return mongo.find<Ownership>(
            byContractAndTokenId(contract, tokenId)
        ).asFlow()
    }

    suspend fun saveAll(ownerships: Flow<Ownership>) {
        return ownerships.collect {
            mongo.save(it)
        }
    }

    private fun byContractAndTokenId(
        contract: Address,
        tokenId: Int
    ) = Query(
        Criteria().andOperator(
            Ownership::contract isEqualTo contract,
            Ownership::tokenId isEqualTo tokenId
        )
    )
}