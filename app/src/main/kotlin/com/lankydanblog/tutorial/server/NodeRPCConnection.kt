package com.lankydanblog.tutorial.server

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

/**
 * Wraps a node RPC proxy.
 *
 * The RPC proxy is configured based on the properties in `application.properties`.
 *
 * @param host The host of the node we are connecting to.
 * @param rpcPort The RPC port of the node we are connecting to.
 * @param username The username for logging into the RPC client.
 * @param password The password for logging into the RPC client.
 * @property proxy The RPC proxy.
 */
@Component
class NodeRPCConnection(
    @Value("\${config.rpc.host}") private val host: String,
    @Value("\${config.rpc.port}") private val rpcPort: Int,
    @Value("\${config.rpc.username}") private val username: String,
    @Value("\${config.rpc.password}") private val password: String) : AutoCloseable {

  private val rpcConnection: CordaRPCConnection
  // final because of the kotlin spring plugin making everything open by default
  final val proxy: CordaRPCOps

  init {
    val rpcAddress = NetworkHostAndPort(host, rpcPort)
    val rpcClient = CordaRPCClient(rpcAddress)
    rpcConnection = rpcClient.start(username, password)
    proxy = rpcConnection.proxy
  }

  @PreDestroy
  override fun close() {
    rpcConnection.notifyServerAndClose()
  }

  @Bean
  fun objectMapper(): ObjectMapper {
    val mapper =  JacksonSupport.createDefaultMapper(proxy)
    mapper.addMixIn(AbstractParty::class.java, PartyMixin::class.java)
    return mapper
  }

  abstract class PartyMixin {
    @JsonIgnore
    abstract fun owningKey()
  }
}