package com.rarible.flow.scanner.eventlisteners

import com.fasterxml.jackson.databind.ObjectMapper
import com.nftco.flow.sdk.FlowAddress
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.ItemRepository
import com.rarible.flow.core.repository.OrderRepository
import com.rarible.flow.core.repository.OwnershipRepository
import com.rarible.flow.core.kafka.ProtocolEventPublisher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
class ItemEventListeners(
    private val itemRepository: ItemRepository,
    private val ownershipRepository: OwnershipRepository,
    private val orderRepository: OrderRepository,
    private val protocolEventPublisher: ProtocolEventPublisher,
) {

    @EventListener(MintActivity::class)
    fun mintItemEvent(activity: MintActivity) = runBlocking {
        val id = ItemId(contract = activity.contract, tokenId = activity.tokenId)
        val owner = FlowAddress(activity.owner)
        val ownershipId = OwnershipId(contract = activity.contract, tokenId = activity.tokenId, owner = owner)
        val itemExists = itemRepository.existsById(id).awaitSingle()
        val ownershipExists = ownershipRepository.existsById(ownershipId).awaitSingle()
        val mapper = ObjectMapper()
        if (!itemExists) {
            val item = itemRepository.insert(
                Item(
                    contract = id.contract,
                    tokenId = id.tokenId,
                    creator = owner,
                    royalties = activity.royalties,
                    owner = owner,
                    mintedAt = activity.timestamp,
                    meta = mapper.writeValueAsString(activity.metadata),
                    collection = activity.contract,
                    updatedAt = activity.timestamp
                )
            ).awaitSingle()
            protocolEventPublisher.onItemUpdate(item)
        } else {
            val item = itemRepository.findById(id).awaitSingle()
            if (item.mintedAt != activity.timestamp) {
                itemRepository.save(item.copy(mintedAt = activity.timestamp))
            }
        }

        if (!ownershipExists) {
            val ownership = ownershipRepository.insert(
                Ownership(
                    contract = activity.contract,
                    tokenId = activity.tokenId,
                    owner = owner,
                    creator = owner,
                    date = activity.timestamp
                )
            ).awaitSingle()
            protocolEventPublisher.onUpdate(ownership)
        }
    }

    @EventListener(BurnActivity::class)
    fun burnItemEvent(activity: BurnActivity) = runBlocking {
        val itemId = ItemId(contract = activity.contract, tokenId = activity.tokenId)
        val exists = itemRepository.existsById(itemId).awaitSingle()
        if (exists) {
            val item = itemRepository.findById(itemId).awaitSingle()
            itemRepository.save(item.copy(owner = null, updatedAt = activity.timestamp)).awaitSingle()
            protocolEventPublisher.onItemDelete(itemId)
            val ownerships =
                ownershipRepository.deleteAllByContractAndTokenId(itemId.contract, itemId.tokenId).collectList()
                    .awaitSingle()
            ownerships.forEach {
                protocolEventPublisher.onDelete(it)
            }
        }
    }

    @EventListener(WithdrawnActivity::class)
    fun itemWithdrawn(activity: WithdrawnActivity) = runBlocking {
        val itemId = ItemId(contract = activity.contract, tokenId = activity.tokenId)
        val from = FlowAddress(activity.from ?: "0x00")
        val item = itemRepository.findById(itemId).awaitSingleOrNull()
        if (item != null && item.updatedAt <= activity.timestamp) {
            protocolEventPublisher.onItemUpdate(
                itemRepository.save(
                    item.copy(
                        owner = from,
                        updatedAt = activity.timestamp
                    )
                ).awaitSingle()
            )
        }

        val ownershipId = OwnershipId(contract = activity.contract, tokenId = activity.tokenId, owner = from)
        val ownership = ownershipRepository.findById(ownershipId).awaitSingleOrNull()
        if (ownership != null) {
            ownershipRepository.delete(ownership).awaitSingleOrNull()
            protocolEventPublisher.onDelete(ownership)
        }

        if (activity.from != null) {
            val orders = orderRepository
                .findAllByMakeAndMakerAndStatusAndLastUpdatedAtIsBefore(
                    FlowAssetNFT(activity.contract, 1.toBigDecimal(), activity.tokenId),
                    FlowAddress(activity.from!!),
                    OrderStatus.ACTIVE,
                    LocalDateTime.ofInstant(activity.timestamp, ZoneOffset.UTC),
                )
                .map {
                    it.copy(status = OrderStatus.INACTIVE)
                }
                .asFlow().toList()
            orderRepository.saveAll(orders).subscribe()
        }
    }

    @EventListener(DepositActivity::class)
    fun itemDeposit(activity: DepositActivity) = runBlocking {
        val itemId = ItemId(contract = activity.contract, tokenId = activity.tokenId)
        val newOwner = FlowAddress(activity.to ?: "0x00")
        val item = itemRepository.findById(itemId).awaitSingleOrNull()
        if (item != null && item.updatedAt <= activity.timestamp) {
            protocolEventPublisher.onItemUpdate(
                itemRepository.save(item.copy(owner = newOwner, updatedAt = activity.timestamp)).awaitSingle()
            )
        }

        val ownershipId = OwnershipId(contract = activity.contract, tokenId = activity.tokenId, owner = newOwner)
        val existsOwnership = ownershipRepository.existsById(ownershipId).awaitSingle()
        if (!existsOwnership) {
            protocolEventPublisher.onUpdate(
                ownershipRepository.insert(
                    Ownership(
                        contract = itemId.contract,
                        tokenId = itemId.tokenId,
                        owner = newOwner,
                        date = activity.timestamp,
                        creator = newOwner
                    )
                ).awaitSingle()
            )
        }

        if (activity.to != null) {
            val orders = orderRepository
                .findAllByMakeAndMakerAndStatusAndLastUpdatedAtIsBefore(
                    FlowAssetNFT(activity.contract, 1.toBigDecimal(), activity.tokenId),
                    FlowAddress(activity.to!!),
                    OrderStatus.INACTIVE,
                    LocalDateTime.ofInstant(activity.timestamp, ZoneOffset.UTC),
                )
                .map {
                    it.copy(status = OrderStatus.ACTIVE)
                }
                .asFlow().toList()
            orderRepository.saveAll(orders).subscribe()

        }
    }
}
