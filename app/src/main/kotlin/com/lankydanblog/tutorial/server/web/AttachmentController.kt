package com.lankydanblog.tutorial.server.web

import com.lankydanblog.tutorial.server.NodeRPCConnection
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RestController
@RequestMapping("/attachments")
class AttachmentController(rpc: NodeRPCConnection) {

  private val proxy = rpc.proxy

  @PostMapping
  fun upload(@RequestParam file: MultipartFile, @RequestParam uploader: String): ResponseEntity<String> {
    val filename = file.originalFilename
    require(filename != null) { "File name must be set" }
    val hash: SecureHash = if (file.contentType != "zip" || file.contentType != "jar") {
      uploadZip(file.inputStream, uploader, filename!!)
    } else {
      proxy.uploadAttachmentWithMetadata(
        jar = file.inputStream,
        uploader = uploader,
        filename = filename!!
      )
    }
    return ResponseEntity.created(URI.create("attachments/$hash")).body("Attachment uploaded with hash - $hash")
  }

  private fun uploadZip(inputStream: InputStream, uploader: String, filename: String): AttachmentId {
    val zipName = "$filename-${UUID.randomUUID()}.zip"
    FileOutputStream(zipName).use { fileOutputStream ->
      ZipOutputStream(fileOutputStream).use { zipOutputStream ->
        val zipEntry = ZipEntry(filename)
        zipOutputStream.putNextEntry(zipEntry)
        inputStream.copyTo(zipOutputStream, 1024)
      }
    }
    return FileInputStream(zipName).use { fileInputStream ->
      val hash = proxy.uploadAttachmentWithMetadata(
        jar = fileInputStream,
        uploader = uploader,
        filename = filename
      )
      Files.deleteIfExists(Paths.get(zipName))
      hash
    }
  }
}