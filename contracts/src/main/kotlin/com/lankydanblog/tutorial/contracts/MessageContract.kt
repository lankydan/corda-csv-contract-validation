package com.lankydanblog.tutorial.contracts

import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.LedgerTransaction
import org.apache.commons.csv.CSVFormat
import java.io.InputStreamReader
import java.util.jar.JarInputStream

class MessageContract : Contract {

  companion object {
    val CONTRACT_ID = MessageContract::class.qualifiedName!!
  }

  interface Commands : CommandData {
    class Send(attachmentId: AttachmentId) : CommandWithAttachmentId(attachmentId), Commands
    class Reply(attachmentId: AttachmentId) : CommandWithAttachmentId(attachmentId), Commands
  }

  abstract class CommandWithAttachmentId(val attachmentId: AttachmentId) : CommandData {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
  }

  override fun verify(tx: LedgerTransaction) {
    val command = tx.commands.requireSingleCommand<Commands>()
    when (command.value) {
      is Commands.Send -> requireThat {
        "No inputs should be consumed when sending a message." using (tx.inputs.isEmpty())
        "Only one output state should be created when sending a message." using (tx.outputs.size == 1)
      }
      is Commands.Reply -> requireThat {
        "One input should be consumed when replying to a message." using (tx.inputs.size == 1)
        "Only one output state should be created when replying to a message." using (tx.outputs.size == 1)
      }
    }
    require(isMessageInCsv(tx)) {
      "The output message must be contained within the csv of valid messages. " +
              "See attachment with hash = ${tx.attachments.first().id} for its contents"
    }
  }

  private fun isMessageInCsv(tx: LedgerTransaction): Boolean {
    val message = tx.outputsOfType<MessageState>().first()
    val attachmentId = tx.commandsOfType<CommandWithAttachmentId>().single().value.attachmentId
    return tx.getAttachment(attachmentId).openAsJAR().use { zipInputStream: JarInputStream ->
      zipInputStream.nextJarEntry.name
      val csv = CSVFormat.DEFAULT.withHeader("valid_messages")
        .withFirstRecordAsHeader()
        .parse(InputStreamReader(zipInputStream))
      csv.records.any { row -> row.get("valid messages") == message.contents }
    }
  }
}