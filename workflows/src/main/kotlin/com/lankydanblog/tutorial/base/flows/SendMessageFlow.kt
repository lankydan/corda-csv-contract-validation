package com.lankydanblog.tutorial.base.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.Send
import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.Builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.node.services.vault.Builder.equal as equal1

@InitiatingFlow
@StartableByRPC
class SendMessageFlow(private val message: MessageState, private val attachmentName: String) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    logger.info("Started sending message ${message.contents}")
    val tx = verifyAndSign(transaction())
    val sessions = listOf(initiateFlow(message.recipient))
    val stx = collectSignature(tx, sessions)
    return subFlow(FinalityFlow(stx, sessions)).also {
      logger.info("Finished sending message ${message.contents}")
    }
  }

  @Suspendable
  private fun collectSignature(
    transaction: SignedTransaction,
    sessions: List<FlowSession>
  ): SignedTransaction = subFlow(CollectSignaturesFlow(transaction, sessions))

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

  private fun transaction(): TransactionBuilder =
    TransactionBuilder(notary()).apply {
      val attachmentId = attachment()
      addOutputState(message)
      addCommand(Command(Send(attachmentId), message.participants.map(Party::owningKey)))
      addAttachment(attachmentId)
    }

  private fun attachment(): AttachmentId {
    return serviceHub.attachments.queryAttachments(
      AttachmentQueryCriteria.AttachmentsQueryCriteria(
        filenameCondition = Builder.equal(
          attachmentName
        )
      )
    ).first()
  }

  private fun notary(): Party = serviceHub.networkMapCache.notaryIdentities.first()
}

@InitiatedBy(SendMessageFlow::class)
class SendMessageResponder(private val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    val stx = subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
    subFlow(
      ReceiveFinalityFlow(
        otherSideSession = session,
        expectedTxId = stx.id
      )
    )
  }
}