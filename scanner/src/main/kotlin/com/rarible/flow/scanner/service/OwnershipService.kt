package com.rarible.flow.scanner.service

import com.nftco.flow.sdk.FlowAddress
import com.rarible.flow.core.domain.Item
import com.rarible.flow.core.domain.Ownership
import com.rarible.flow.core.domain.OwnershipId
import com.rarible.flow.core.repository.OwnershipRepository
import com.rarible.flow.core.repository.coFindById
import com.rarible.flow.core.repository.coSave
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OwnershipService(
    val ownershipRepository: OwnershipRepository
) {

    suspend fun createOwnership(item: Item, owner: FlowAddress, time: Instant): Ownership {
        return ownershipRepository.coSave(
            Ownership(
                contract = item.contract,
                tokenId = item.tokenId,
                owner = owner,
                date = time,
                creator = owner
            )
        )
    }

    suspend fun transferOwnershipIfExists(item: Item, from: FlowAddress, to: FlowAddress): Ownership? {
        return withOwnership(
            item.ownershipId(from)
        ) { o ->
            ownershipRepository.delete(o)
            ownershipRepository.coSave(o.transfer(to))
        }
    }

    suspend fun <T> withOwnership(ownershipId: OwnershipId, fn: suspend (Ownership) -> T): T? {
        val ownership = ownershipRepository.coFindById(ownershipId)
        return if(ownership == null) {
            null
        } else {
            fn(ownership)
        }
    }
}