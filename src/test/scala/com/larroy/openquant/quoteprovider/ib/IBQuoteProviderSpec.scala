package com.larroy.openquant.quoteprovider.ib

import java.time.ZonedDateTime

import com.larroy.quant.common.CurrencyContract
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.mutable._

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Await}
import scala.util.{Success, Failure}
import net.ceedubs.ficus.Ficus._

/**
  * Created by piotr on 17.01.16.
  */
class IBQuoteProviderSpec extends Specification {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  val cfg = ConfigFactory.load().getConfig("ibquoteprovider.test")
  val timeoutDuration = Duration(cfg.getInt("tws.timeout_s"), SECONDS)
  "IBQuoteProvider" should {
    "get quotes" in { implicit ec: ExecutionContext =>
      val ibQuoteProvider = Await.result(IBQuoteProvider(cfg.as[String]("tws.host"), cfg.as[Int]("tws.port"), cfg.as[Int]("tws.clientId")), timeoutDuration)
      val futureQuotes = ibQuoteProvider.quotes(CurrencyContract("EUR.USD"), ZonedDateTime.now.minusDays(4), ZonedDateTime.now)
      val success = Await.ready(futureQuotes, timeoutDuration).value match {
        // completed with error
        case Some(x @Failure(e)) ⇒ log.error(e.toString)
          false
        // completed with failure
        case Some(Success(bars)) ⇒ log.info(bars.toString)
          true
        // not completed
        case None ⇒ false
      }
      success must beTrue
    }
  }
}
