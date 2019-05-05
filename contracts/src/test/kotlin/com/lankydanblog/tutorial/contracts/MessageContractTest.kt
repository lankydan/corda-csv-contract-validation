package com.lankydanblog.tutorial.contracts

import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class MessageContractTest {

  private companion object {
    val LAURA = TestIdentity(CordaX500Name.parse("L=London,O=Laura,OU=Trade,C=GB"))
    val DAN = TestIdentity(CordaX500Name.parse("L=London,O=Laura,OU=Trade,C=GB"))
  }

  private val ledgerServices = MockServices(LAURA, DAN)

  @Test
  fun `message in csv passes validation`() {
    ledgerServices.ledger {
      transaction {
        output(
          MessageContract.CONTRACT_ID, MessageState(
            sender = LAURA.party,
            recipient = DAN.party,
            contents = "hello world"
          )
        )
        val id = attachment(this::class.java.getResource("/valid_messages.zip").openStream())
        attachment(id)
        command(listOf(LAURA.party.owningKey, DAN.party.owningKey), MessageContract.Commands.Send(id))
        verifies()
      }
    }
  }

  @Test
  fun `message not in csv fails validation`() {
    ledgerServices.ledger {
      transaction {
        output(
          MessageContract.CONTRACT_ID, MessageState(
            sender = LAURA.party,
            recipient = DAN.party,
            contents = "i am a failure"
          )
        )
        val id = attachment(this::class.java.getResource("/valid_messages.zip").openStream())
        attachment(id)
        command(listOf(LAURA.party.owningKey, DAN.party.owningKey), MessageContract.Commands.Send(id))
        fails()
      }
    }
  }
}