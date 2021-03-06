package com.rarible.flow.scanner.config

import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowChainId
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("flow-api")
data class FlowApiProperties(
    val flowAccessUrl: String,
    val flowAccessPort: Int,
    val chainId: FlowChainId,
    val serviceAccount: FlowNetworkServiceAccount = FlowNetworkServiceAccount(FlowAddress("0x00"), "")
)

data class FlowNetworkServiceAccount(val address: FlowAddress, val privateKey: String)
