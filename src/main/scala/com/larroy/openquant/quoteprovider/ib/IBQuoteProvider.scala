package com.larroy.openquant.quoteprovider.ib

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.slf4j.{Logger, LoggerFactory}
import java.util.Date

import com.ib.client.Types.{BarSize, SecType, WhatToShow}
import com.ib.client.{Contract ⇒ IBContract}
import com.larroy.ibclient.IBClient
import com.larroy.ibclient.contract.{CashContract ⇒ IBCashContract, FutureContract ⇒ IBFutureContract, StockContract ⇒ IBStockContract}
import com.larroy.openquant.quoteprovider.QuoteProvider
import com.larroy.openquant.quoteprovider._
import com.larroy.quant.common._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

class IBQuoteProvider protected(ibclient: IBClient)(implicit ec: ExecutionContext) extends QuoteProvider {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)


  import scala.language.implicitConversions
  implicit def zonedDateTimeToDate(x: ZonedDateTime) = Date.from(x.toInstant)

  override def name = "Interactive Brokers historical quote provider"
  override def connected = ibclient.isConnected

  /**
    * @param contract
    * @param startDate
    * @param endDate
    * @param resolution
    * @return Either a Throwable on error or a map of seconds since epoch to Bars
    */
  override def quotes(contract: Contract, startDate: ZonedDateTime, endDate: ZonedDateTime, resolution: Resolution.Enum): Future[IndexedSeq[Bar]] = {
    val ibcontract: Future[IBContract] = toIBContract(contract) match {
      case -\/(e) ⇒ Future.failed(e)
      case \/-(ibcontract) ⇒ toFullIBContract(ibcontract)
    }
    ibcontract.flatMap[IndexedSeq[Bar]] { ibcontract ⇒
      log.debug(s"IBQuoteProvider: requesting quotes for ${contract}")
      val whatToShow = ibcontract.secType match {
        case SecType.STK ⇒ WhatToShow.TRADES
        case SecType.CASH ⇒ WhatToShow.MIDPOINT
        case SecType.FUT ⇒ WhatToShow.TRADES
        case _ ⇒ WhatToShow.MIDPOINT
      }
      val futureBars = ibclient.easyHistoricalData(ibcontract, startDate, endDate, resolutionToBarSize(resolution), whatToShow)
      val ourFutureBars: Future[IndexedSeq[Bar]] = futureBars.map { bars ⇒
        bars.map { bar ⇒
          Bar(bar.time, bar.open, bar.close, bar.high, bar.low, -1, bar.volume, "InteractiveBrokers:IBclient")
        }
      }
      ourFutureBars
    }
  }

  def contractTypeToSecType(contractType: ContractType.Enum): SecType = contractType match {
    case ContractType.Future ⇒ SecType.FUT
    case ContractType.Stock ⇒ SecType.STK
    case ContractType.Currency ⇒ SecType.CASH
  }

  /**
    * @param contract
    * @param resolution
    * @return true if there are quotes for the given contract and resoltion
    */
  override def available(contract: Contract, resolution: Resolution.Enum): Future[String] = {
    log.info(s"Getting contract details for ${contract.symbol}")
    val exceptionOrFuture = toIBContract(contract).map { ibcontract ⇒
      val futuredetails = ibclient.contractDetails(ibcontract)
      futuredetails.map { details ⇒
        log.info(s"Contract details for ${contract.symbol}:\n${details.mkString("\n")}")
        details.mkString("\n")
      }
    }
    exceptionOrFuture match {
      case -\/(e) ⇒ Future.failed[String](e)
      case \/-(f) ⇒ f
    }
  }

  override def close(): Unit = {
    log.debug("IBQuoteProvider close")
    ibclient.disconnect()
  }

  def resolutionToBarSize(x: Resolution.Enum): BarSize = {
    BarSize.valueOf(x.toString)
  }

  def toFullIBContract(ibcontract: IBContract): Future[IBContract] = {
    val futuredetails = ibclient.contractDetails(ibcontract)
    val fullContract = futuredetails.map[com.ib.client.Contract] { details ⇒
      log.info(s"Contract details for ${ibcontract.symbol}:\n${details.mkString("\n")}")
      // Find details for contract of the same type
      val found = details.find(detail ⇒ detail.contract.secType == ibcontract.secType)
      found match {
        case Some(detail) ⇒ detail.contract
        case None ⇒ ibcontract
      }
    }
    fullContract
  }

  def toIBContract(contract: Contract): Throwable \/ IBContract = contract match {
    case StockContract(symbol, exchange, currency) ⇒
      \/-(new IBStockContract(symbol, exchange, currency))

    case FutureContract(symbol, exchange, currency, expiry) ⇒
      val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
      val expiryStr = expiry.map { x ⇒ x.format(dtf) }.getOrElse("")
      \/-(new IBFutureContract(symbol, expiryStr, exchange, currency))

    case CurrencyContract(symbol, exchange, currency) ⇒
      symbol.split("\\.") match {
        case Array(first, second) ⇒
          \/-(new IBCashContract(first, symbol, exchange, currency))

        case _ ⇒
          -\/(new IllegalArgumentException("Please specify currency pair with a dot separating the pairs as in EUR.USD"))
      }
  }
}

object IBQuoteProvider {
  def apply(host: String, port: Int, clientId: Int, timeout_s: Int = 30)(implicit ec: ExecutionContext): Future[QuoteProvider] = {
    val futureIBclient = new IBClient(host, port, clientId).connect()
    futureIBclient.map { ibclient ⇒
      // now connected
      val quoteProvider = new IBQuoteProvider(ibclient)(ec)
      quoteProvider
    }
  }
}
