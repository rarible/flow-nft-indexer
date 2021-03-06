package com.rarible.flow.core.domain

import com.nftco.flow.sdk.FlowAddress
import org.springframework.data.annotation.AccessType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable
import java.time.Clock
import java.time.Instant

data class OwnershipId(
    val contract: String,
    val tokenId: TokenId,
    val owner: FlowAddress
) : Serializable {

    override fun toString(): String {
        return "${contract}:$tokenId:${owner.formatted}"
    }

    companion object {
        fun parse(str: String): OwnershipId {
            val parts = str.split(':')
            if (parts.size == 3) {
                return OwnershipId(
                    contract = parts[0],
                    tokenId = parts[1].toLong(),
                    owner = FlowAddress(parts[2])
                )
            } else {
                throw IllegalArgumentException("Failed to parse OwnershipId from [$str]")
            }
        }
    }
}

@Document
@CompoundIndexes(
    CompoundIndex(
        name = "date_tokenId",
        def = "{'date': -1, 'tokenId': -1}"
    ),
    CompoundIndex(
        name = "date_owner_contract_tokenId",
        def = "{'date': -1, 'owner': -1, 'contract': -1, 'tokenId': -1}"
    )
)
data class Ownership(
    val contract: String,
    val tokenId: TokenId,
    val owner: FlowAddress,
    val creator: FlowAddress,
    val date: Instant = Instant.now(Clock.systemUTC())
) : Serializable {

    constructor(
        id: OwnershipId, creator: FlowAddress, date: Instant = Instant.now(Clock.systemUTC())
    ): this(id.contract, id.tokenId, id.owner, creator, date)

    @get:Id
    @get:AccessType(AccessType.Type.PROPERTY)
    var id: OwnershipId
        get() = OwnershipId(owner = owner, contract = contract, tokenId = tokenId)
        set(_) {}

    fun transfer(to: FlowAddress): Ownership = this.copy(owner = to)
}
