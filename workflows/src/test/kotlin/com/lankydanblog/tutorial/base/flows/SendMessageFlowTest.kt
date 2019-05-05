package com.lankydanblog.tutorial.base.flows

import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class SendMessageFlowTest {

  private lateinit var mockNetwork: MockNetwork
  private lateinit var partyA: StartedMockNode
  private lateinit var partyB: StartedMockNode
  private lateinit var notaryNode: MockNetworkNotarySpec

  @Before
  fun setup() {
    notaryNode = MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))
    mockNetwork = MockNetwork(
      listOf(
        "com.lankydanblog"
      ),
      notarySpecs = listOf(notaryNode)
    )
    partyA =
      mockNetwork.createNode(
        MockNodeParameters(
          legalName = CordaX500Name(
            "PartyA",
            "Berlin",
            "DE"
          )
        )
      )

    partyB =
      mockNetwork.createNode(
        MockNodeParameters(
          legalName = CordaX500Name(
            "PartyB",
            "Berlin",
            "DE"
          )
        )
      )
    mockNetwork.runNetwork()
  }

  @After
  fun tearDown() {
    mockNetwork.stopNodes()
  }

  @Test
  fun `message in csv passes validation`() {
    partyA.services.attachments.importAttachment(
      this::class.java.getResourceAsStream("/valid_messages.zip"),
      "me",
      "valid_messages.csv"
    )
    val future = partyA.startFlow(
      SendMessageFlow(
        MessageState(
          contents = "hello world",
          recipient = partyB.info.singleIdentity(),
          sender = partyA.info.singleIdentity(),
          linearId = UniqueIdentifier()
        ), "valid_messages.csv"
      )
    )
    mockNetwork.runNetwork()
    future.get()
  }

  @Test
  fun `message not in csv fails validation`() {
    partyA.services.attachments.importAttachment(
      this::class.java.getResourceAsStream("/valid_messages.zip"),
      "me",
      "valid_messages.csv"
    )
    val future = partyA.startFlow(
      SendMessageFlow(
        MessageState(
          contents = "whats up?",
          recipient = partyB.info.singleIdentity(),
          sender = partyA.info.singleIdentity(),
          linearId = UniqueIdentifier()
        ), "valid_messages.csv"
      )
    )
    mockNetwork.runNetwork()
    assertThatExceptionOfType(ExecutionException::class.java).isThrownBy {
      future.get()
    }.withCauseInstanceOf(TransactionVerificationException.ContractRejection::class.java)
      .withMessageContaining("The output message must be contained")
  }
}