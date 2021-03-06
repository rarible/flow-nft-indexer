package com.rarible.flow.core.domain


import com.rarible.protocol.dto.FlowOrderPlatformDto
import java.math.BigDecimal
import java.time.Instant
import com.nftco.flow.sdk.FlowAddress
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType

sealed interface FlowActivity

sealed class TypedFlowActivity : FlowActivity {
    abstract val type: FlowActivityType
}

/**
 * Common activity
 *
 * @property type               activity type
 */
sealed class BaseActivity : TypedFlowActivity() {
    abstract val timestamp: Instant
}

/**
 * Base NFT Activity
 *
 * @property contract           NFT item contract
 * @property tokenId            NFT token ID

 */
sealed class NFTActivity : BaseActivity() {
    abstract val contract: String
    abstract val tokenId: TokenId /* = kotlin.Long */

}

/**
 * Base NFT Activity
 *
 * @property owner              NFT owner account address
 * @property value              amount of NFT's (default 1)
 */
sealed class FlowNftActivity : NFTActivity() {
    abstract val owner: String?
    abstract val value: Long
}

/**
 * Order activity
 *
 * @property price      order price
 * @property priceUsd   order price in USD
 */
sealed class FlowNftOrderActivity : NFTActivity() {
    abstract val price: BigDecimal
    abstract val priceUsd: BigDecimal
}

/**
 * Buy activity
 *
 * @property left               buyer
 * @property right              seller
 */
data class FlowNftOrderActivitySell(
    override val type: FlowActivityType = FlowActivityType.SELL,
    @Field(targetType = FieldType.DECIMAL128)
    override val price: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    override val priceUsd: BigDecimal,
    override val tokenId: TokenId,
    override val contract: String,
    override val timestamp: Instant,
    val hash: String,
    val left: OrderActivityMatchSide,
    val right: OrderActivityMatchSide,
    val payments: List<FlowNftOrderPayment> = emptyList(),
    val platform: FlowOrderPlatformDto? = FlowOrderPlatformDto.RARIBLE
) : FlowNftOrderActivity()

/**
 * Sell (List) activity
 *
 * @property hash           TODO????
 * @property maker          NFT item
 */
data class FlowNftOrderActivityList(
    override val type: FlowActivityType = FlowActivityType.LIST,
    @Field(targetType = FieldType.DECIMAL128)
    override val price: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    override val priceUsd: BigDecimal,
    override val tokenId: TokenId,
    override val contract: String,
    override val timestamp: Instant,
    val hash: String,
    val maker: String,
    val make: FlowAsset,
    val take: FlowAsset,
) : FlowNftOrderActivity()

data class FlowNftOrderActivityCancelList(
    override val type: FlowActivityType = FlowActivityType.CANCEL_LIST,
    override val timestamp: Instant,
    val hash: String,
    @Field(targetType = FieldType.DECIMAL128)
    val price: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val priceUsd: BigDecimal? = null,
    val tokenId: TokenId? = null,
    val contract: String? = null,
    val maker: String? = null,
    val make: FlowAsset? = null,
    val take: FlowAsset? = null,
) : BaseActivity()

data class FlowNftOrderActivityBid(
    override val type: FlowActivityType = FlowActivityType.BID,
    override val price: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    override val priceUsd: BigDecimal,
    override val tokenId: TokenId,
    override val contract: String,
    override val timestamp: Instant,
    val hash: String,
    val maker: String,
    val make: FlowAsset,
    val take: FlowAsset,
) : FlowNftOrderActivity()

data class FlowNftOrderActivityCancelBid(
    override val type: FlowActivityType = FlowActivityType.CANCEL_BID,
    override val timestamp: Instant,
    val hash: String,
    val price: BigDecimal? = null,
    val priceUsd: BigDecimal? = null,
    val tokenId: TokenId? = null,
    val contract: String? = null,
    val maker: String? = null,
    val make: FlowAsset? = null,
    val take: FlowAsset? = null,
) : BaseActivity()


data class FlowNftOrderPayment(
    val type: PaymentType,
    val address: String,
    val rate: BigDecimal,
    val amount: BigDecimal,
)

enum class PaymentType {
    BUYER_FEE,
    SELLER_FEE,
    OTHER,
    ROYALTY,
    REWARD,
}

/**
 * Mint Activity
 */
data class MintActivity(
    override val type: FlowActivityType = FlowActivityType.MINT,
    override val owner: String,
    override val contract: String,
    override val tokenId: TokenId,
    override val value: Long = 1L,
    override val timestamp: Instant,
    val creator: String?,
    val royalties: List<Part>,
    val metadata: Map<String, String>,
    val collection: String? = null
) : FlowNftActivity()

/**
 * Burn Activity
 */
data class BurnActivity(
    override val type: FlowActivityType = FlowActivityType.BURN,
    override val contract: String,
    override val tokenId: TokenId,
    override val value: Long = 1L,
    override val owner: String? = null,
    override val timestamp: Instant,
) : FlowNftActivity()

/**
 * Activity type
 */
enum class FlowActivityType {
    /**
     * Mint NFT
     */
    MINT,

    /**
     * Burn NFT
     */
    BURN,

    /**
     * NFT Sold
     */
    SELL,

    BUY,

    /**
     * List to sell
     */
    LIST,

    /**
     * Cancel listing
     */
    CANCEL_LIST,

    /**
     * Open bid
     */
    BID,
    MAKE_BID,
    GET_BID,

    /**
     * Cancel bid
     */
    CANCEL_BID,

    TRANSFER,
    TRANSFER_FROM,
    TRANSFER_TO,

    LOT_AVAILABLE,
    LOT_COMPLETED,
    LOT_CANCELED,
    LOT_END_TIME_CHANGED,
    LOT_CLEANED,
    OPEN_BID,
    CLOSE_BID,
    INCREASE_BID

}

sealed class FlowAsset {
    abstract val contract: String
    abstract val value: BigDecimal
}

data class FlowAssetNFT(
    override val contract: String,
    @Field(targetType = FieldType.DECIMAL128)
    override val value: BigDecimal,
    val tokenId: TokenId,
) : FlowAsset()

data class FlowAssetFungible(
    override val contract: String,
    @Field(targetType = FieldType.DECIMAL128)
    override val value: BigDecimal,
) : FlowAsset()

object FlowAssetEmpty : FlowAsset() {
    override val contract: String = ""
    override val value: BigDecimal = 0.toBigDecimal()
}

data class OrderActivityMatchSide(
    val maker: String,
    val asset: FlowAsset,
)

data class TransferActivity(
    override val type: FlowActivityType = FlowActivityType.TRANSFER,
    override val contract: String,
    override val tokenId: TokenId, /* = kotlin.Long */
    override val timestamp: Instant,
    val from: String,
    val to: String,
    val purchased: Boolean? = false,
) : NFTActivity()

data class AuctionActivityLot(
    override val type: FlowActivityType = FlowActivityType.LOT_AVAILABLE,
    override val timestamp: Instant,
    override val contract: String,
    override val tokenId: TokenId,
    val lotId: Long,
    val currency: String,
    val minStep: BigDecimal,
    val startPrice: BigDecimal,
    val buyoutPrice: BigDecimal?,
    val startAt: Instant,
    val duration: Long,
    val finishAt: Instant?,
    val seller: String
) : NFTActivity()

data class AuctionActivityLotCanceled(
    override val type: FlowActivityType = FlowActivityType.LOT_CANCELED,
    override val timestamp: Instant,
    val lotId: Long
) : BaseActivity()

data class AuctionActivityLotHammered(
    override val type: FlowActivityType = FlowActivityType.LOT_COMPLETED,
    override val timestamp: Instant,
    override val contract: String,
    override val tokenId: TokenId,
    val winner: FlowAddress,
    val hammerPrice: BigDecimal,
    val hammerPriceUsd: BigDecimal,
    val lotId: Long,
    val payments: List<Payout>,
    val originFees: List<Payout>
) : NFTActivity()

data class AuctionActivityBidOpened(
    override val type: FlowActivityType = FlowActivityType.OPEN_BID,
    override val timestamp: Instant,
    val lotId: Long,
    val bidder: String,
    val amount: BigDecimal
) : BaseActivity()

data class AuctionActivityBidClosed(
    override val type: FlowActivityType = FlowActivityType.CLOSE_BID,
    override val timestamp: Instant,
    val lotId: Long,
    val bidder: String,
    val isWinner: Boolean
) : BaseActivity()

data class AuctionActivityLotEndTimeChanged(
    override val type: FlowActivityType = FlowActivityType.LOT_END_TIME_CHANGED,
    override val timestamp: Instant,
    val lotId: Long,
    val finishAt: Instant
) : BaseActivity()

data class AuctionActivityLotCleaned(
    override val type: FlowActivityType = FlowActivityType.LOT_CLEANED,
    override val timestamp: Instant,
    val lotId: Long
) : BaseActivity()

data class AuctionActivityBidIncreased(
    override val type: FlowActivityType = FlowActivityType.INCREASE_BID,
    override val timestamp: Instant,
    val lotId: Long,
    val bidder: String,
    val amount: BigDecimal
): BaseActivity()
