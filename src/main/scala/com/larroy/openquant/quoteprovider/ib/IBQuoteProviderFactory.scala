package com.larroy.openquant.quoteprovider.ib

import com.larroy.openquant.quoteprovider.{QuoteProvider, QuoteProviderFactory}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class IBQuoteProviderFactory extends QuoteProviderFactory {
  val cfg = ConfigFactory.load().getConfig("ibquoteprovider")
  override def apply(implicit ec: ExecutionContext): QuoteProvider = {
    val host = cfg.as[String]("tws.host")
    val port = cfg.as[Int]("tws.port")
    val clientId = cfg.as[Int]("tws.clientId")
    val timeout = cfg.as[Int]("tws.timeout_s")
    Await.result(IBQuoteProvider(host, port, clientId, timeout), Duration(timeout, SECONDS))
  }
}
