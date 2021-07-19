package com.rarible.flow.listener.handler

import com.rarible.core.daemon.sequential.ConsumerEventHandler
import com.rarible.flow.core.domain.*
import com.rarible.flow.core.repository.*
import com.rarible.flow.events.EventMessage
import com.rarible.flow.events.NftEvent
import com.rarible.flow.log.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import org.bson.types.ObjectId
import org.onflow.sdk.FlowAddress
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Instant


class EventHandler(
    private val itemRepository: ItemRepository,
    private val ownershipRepository: OwnershipRepository,
    private val orderRepository: OrderRepository,
    private val protocolEventPublisher: ProtocolEventPublisher,
    private val itemMetaRepository: ItemMetaRepository
) : ConsumerEventHandler<EventMessage> {

    override suspend fun handle(event: EventMessage) {
        val nftEvent = event.convert()
        if (nftEvent != null) {
            handle(nftEvent)
        } else {
            log.warn(
                "Failed to convert message [$event] to NftEvent. Probably not NFT event, or contract is not supported."
            )
        }
    }

    private suspend fun handle(event: NftEvent) {
        val contract = event.eventId.contractAddress
        val tokenId = event.id
        when(event) {
            is NftEvent.Destroy -> burn(contract, tokenId)
            is NftEvent.Deposit -> deposit(contract, tokenId, event.to)
            is NftEvent.Mint -> mint(contract, tokenId, event.to, event.metadata)
            is NftEvent.Withdraw -> withdraw(contract, tokenId, event.from)
            is NftEvent.Bid -> bid(contract, tokenId, event.bidder, event.amount)
            is NftEvent.List -> list(contract, tokenId)
            is NftEvent.Unlist -> unlist(contract, tokenId)
            is NftEvent.BidNft -> bidNft(contract, tokenId, event.bidder, event.offeredNftAddress, event.offeredNftId)
            is NftEvent.OrderOpened -> orderOpened(event, contract)
            is NftEvent.OrderClosed -> orderClosed(event, contract)
            is NftEvent.OrderWithdraw -> orderWithdraw(event, contract)
            is NftEvent.OrderAssigned -> orderAssigned(event, contract)
        }
    }

    private suspend fun orderAssigned(event: NftEvent.OrderAssigned, address: FlowAddress) {
        val order = orderRepository.findByItemId(address, event.id)
        if(order != null) {
            orderRepository.save(order.copy(taker = event.to))
        }
    }

    private suspend fun orderWithdraw(event: NftEvent.OrderWithdraw, address: FlowAddress) {
    }

    private suspend fun orderClosed(event: NftEvent.OrderClosed, address: FlowAddress) {
        val order = orderRepository.findByItemId(address, event.id)
        if(order != null) {
            orderRepository.save(order.copy(fill = 1))
        }
    }

    private suspend fun orderOpened(event: NftEvent.OrderOpened, address: FlowAddress) {
        orderRepository.save(
            Order(
                id = ObjectId.get(),
                itemId = ItemId(address, event.askId),
                maker = event.maker,
                amount = event.bidAmount
            )
        )

        update(address, event.id) { item ->
            item.copy(listed = true)
        }
    }

    private suspend fun bidNft(
        address: FlowAddress,
        tokenId: BigInteger,
        bidder: FlowAddress,
        offeredNftAddress: FlowAddress,
        offeredNftId: Int
    ) {
        /*withItem(address, tokenId) { myNft ->
            withItem(offeredNftAddress.formatted, offeredNftId) { theirNft ->
                orderRepository.save(
                    Order(
                        ObjectId(),
                        Item.makeId(address, tokenId),
                        Address(bidder.bytes.bytesToHex()),
                        1.toBigDecimal(),
                        theirNft.id
                    )
                )
            }
        }*/
    }

    private suspend fun unlist(address: FlowAddress, tokenId: BigInteger) {
        update(address, tokenId) {
            it.copy(listed = false)
        }
    }

    private suspend fun list(address: FlowAddress, tokenId: BigInteger) {
        update(address, tokenId) {
            it.copy(listed = true)
        }
    }

    private suspend fun bid(address: FlowAddress, tokenId: BigInteger, bidder: FlowAddress, amount: BigDecimal) {
        /*withItem(address, tokenId) {
            orderRepository.save(
                Order(
                    ObjectId(),
                    Item.makeId(address, tokenId),
                    Address(bidder.bytes.bytesToHex()),
                    amount
                )
            )
        }*/
    }

    private fun withdraw(address: FlowAddress, tokenId: BigInteger, from: FlowAddress) {

    }

    //todo handle metadata
    private suspend fun mint(contract: FlowAddress, tokenId: BigInteger, to: FlowAddress, metadata: Map<String, String>) {
        val existingEvent = itemRepository.findById(ItemId(contract, tokenId))
        if (existingEvent == null) {
            val item = Item(
                contract,
                tokenId,
                to,
                emptyList(),
                to,
                Instant.now(),
                ""
            )
            itemMetaRepository.save(
                ItemMeta(item.id, metadata["title"] ?: "", metadata["description"] ?: "", URI.create(metadata["uri"] ?: ""))
            )

            itemRepository.save(
                item.copy(meta = "/v0.1/items/meta/${item.id}")
            )?.let {
                val result = protocolEventPublisher.onItemUpdate(it)
                log.info("item update message is sent: $result")
            }


            ownershipRepository.save(
                Ownership(
                    contract,
                    tokenId,
                    to,
                    Instant.now()
                )
            )
        }
    }

    suspend fun update(address: FlowAddress, tokenId: BigInteger, fn: suspend (Item) -> Item) {
        withItem(address, tokenId) {
            itemRepository
                .save(fn(it))
                ?.let { saved ->
                    val result = protocolEventPublisher.onItemUpdate(saved)
                    log.info("item update message is sent: $result")
                }
        }
    }

    suspend fun <T> withItem(address: FlowAddress, tokenId: BigInteger, fn: suspend (Item) -> T) {
        val existingEvent = itemRepository.findById(ItemId(address, tokenId))
        if(existingEvent != null) {
            fn(existingEvent)
        }
    }

    suspend fun burn(address: FlowAddress, tokenId: BigInteger) = coroutineScope{
        val items = async { itemRepository.delete(ItemId(address, tokenId)) }
        val ownerships = async {
            ownershipRepository.deleteAllByContractAndTokenId(address, tokenId)
        }

        items.await()?.let { deleted ->
            val result = protocolEventPublisher.onItemDelete(deleted)
            log.info("item delete message is sent: $result")

        }
        ownerships.await()
    }


    suspend fun deposit(address: FlowAddress, id: BigInteger, to: FlowAddress) = coroutineScope {
        val items = async {
            itemRepository
                .findById(ItemId(address, id))
                ?.let {
                    itemRepository.save(it.copy(owner = to))
                }
        }
        val ownership = async {
            ownershipRepository.findAllByContractAndTokenId(
                address, id
            ).map { it.copy(owner = to) }.let { ownershipRepository.saveAll(it) }
        }

        items.await()
        ownership.await()
    }

    companion object {
        val log by Log()
    }
}
