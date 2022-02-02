package com.rarible.flow.core.converter

import com.rarible.flow.core.domain.Order
import com.rarible.flow.core.domain.OrderData
import com.rarible.flow.core.domain.OrderStatus
import com.rarible.flow.log.Log
import com.rarible.protocol.currency.api.client.CurrencyControllerApi
import com.rarible.protocol.currency.dto.BlockchainDto
import com.rarible.protocol.dto.FlowOrderDataDto
import com.rarible.protocol.dto.FlowOrderDto
import com.rarible.protocol.dto.FlowOrderStatusDto
import com.rarible.protocol.dto.PayInfoDto
import kotlinx.coroutines.reactor.awaitSingle
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset


class OrderToDtoConverter(
    private val currencyApi: CurrencyControllerApi
) {

    private val logger by Log()

    private val multiplier: Int = 18

    suspend fun convert(source: Order): FlowOrderDto {
        try {
            val usdRate = try {
                currencyApi.getCurrencyRate(
                    BlockchainDto.FLOW,
                    source.take.contract,
                    Instant.now().toEpochMilli()
                ).awaitSingle().rate
            } catch (e: Exception) {
                BigDecimal.ZERO
            }

            return FlowOrderDto(
                id = source.id,
                itemId = "${source.itemId}",
                maker = source.maker.formatted,
                taker = null,
                make = FlowAssetConverter.convert(source.make),
                take = FlowAssetConverter.convert(source.take),
                fill = source.fill,
                cancelled = source.cancelled,
                createdAt = source.createdAt.toInstant(ZoneOffset.UTC),
                lastUpdateAt = source.lastUpdatedAt!!.toInstant(ZoneOffset.UTC),
                amount = source.amount,
                data = convert(source.data ?: OrderData(emptyList(), emptyList())),
                priceUsd = usdRate * source.take.value,
                collection = source.collection,
                makeStock = source.makeStock!!.movePointRight(multiplier).toBigInteger(),
                status = convert(source.status)
            )
        } catch (e: Exception) {
            logger.error(e.message, e)
            throw Throwable(e)
        }
    }

    fun convert(data: OrderData) = FlowOrderDataDto(
        payouts = data.payouts.map {
            PayInfoDto(
                account = it.account.formatted,
                value = it.value
            )
        },
        originalFees = data.originalFees.map {
            PayInfoDto(
                account = it.account.formatted,
                value = it.value
            )
        }
    )

    fun convert(status: OrderStatus): FlowOrderStatusDto {
        return when(status) {
            OrderStatus.ACTIVE -> FlowOrderStatusDto.ACTIVE
            OrderStatus.FILLED -> FlowOrderStatusDto.FILLED
            OrderStatus.HISTORICAL -> FlowOrderStatusDto.HISTORICAL
            OrderStatus.INACTIVE -> FlowOrderStatusDto.INACTIVE
            OrderStatus.CANCELLED -> FlowOrderStatusDto.CANCELLED
        }
    }
}
