package com.rarible.flow.core.repository

import com.nftco.flow.sdk.FlowAddress
import com.rarible.core.test.ext.MongoTest
import com.rarible.flow.core.config.CoreConfig
import com.rarible.flow.core.domain.Ownership
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import java.time.Instant
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

@MongoTest
@DataMongoTest(
    properties = [
        "application.environment = dev",
        "spring.cloud.service-registry.auto-registration.enabled = false",
        "spring.cloud.discovery.enabled = false",
        "spring.cloud.consul.config.enabled = false",
        "logging.logstash.tcp-socket.enabled = false"
    ]
)
@ContextConfiguration(classes = [CoreConfig::class])
@ActiveProfiles("test")
internal class OwnershipRepositoryTest {
    @Autowired
    private lateinit var ownershipRepository: OwnershipRepository

    @BeforeEach
    fun beforeEach() {
        ownershipRepository.deleteAll().block()
    }

    @Test
    fun `should delete and return ownerships`() {
        runBlocking {
            val contract = randomAddress()
            val owner = randomAddress()

            val (o1, o2, o3) = listOf(
                createOwnership(contract, owner),
                createOwnership(contract, owner),
                createOwnership(contract, owner)
            )
            ownershipRepository.coSaveAll(
                listOf(o1, o2, o3)
            )

            ownershipRepository.deleteAllByContractAndTokenId(o1.contract, o1.tokenId).subscribe {
                it shouldBeEqualToComparingFields o1
            }
        }

    }

    @Test
    fun `should find by ids`() = runBlocking<Unit> {
        val contract = randomAddress()
        val owner = randomAddress()

        val all = listOf(
            createOwnership(contract, owner),
            createOwnership(contract, owner),
            createOwnership(contract, owner)
        )
            .sortedBy { it.id.toString() }
        ownershipRepository.coSaveAll(all)

        val result = ownershipRepository.findByIdIn(all.map { it.id.toString() }).asFlow().toList()
            .sortedBy { it.id.toString() }

        assertThat(result).hasSize(3)
        assertThat(result).containsAll(all)
    }

    private fun createOwnership(contract: FlowAddress = randomAddress(), owner: FlowAddress = randomAddress()) =
        Ownership(
            contract.formatted,
            Random.nextLong(),
            owner,
            owner,
            Instant.now(),
        )

    private fun randomAddress() =
        FlowAddress("0x${RandomStringUtils.random(16, "0123456789ABCDEF")}".lowercase(Locale.ENGLISH))
}
