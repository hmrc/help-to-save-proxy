/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosaveproxy.config

import java.io._
import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import com.typesafe.sslconfig.ssl.{KeyStoreConfig, TrustStoreConfig}

import javax.inject.{Inject, Singleton}
import play.api.inject.{Binding, Module}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosaveproxy.util.{Logging, base64Decode}

import scala.jdk.CollectionConverters._

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment)
    extends WSConfigParser(configuration.underlying, env.classLoader) with Logging {

  logger.info("Starting CustomWSConfigParser")

  override def parse(): WSClientConfig = {
    logger.info("Parsing WSClientConfig")

    val internalParser = new WSConfigParser(configuration.underlying, env.classLoader)
    val config = internalParser.parse()

    val trustStores = config.ssl.trustManagerConfig.trustStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ts =>
      (ts.filePath, ts.data) match {
        case (None, Some(data)) =>
          createTrustStoreConfig(ts, data)

        case _ =>
          logger.info(s"Adding ${ts.storeType} type truststore from ${ts.filePath}")
          ts
      }
    }

    val wsClientConfig = config.copy(
      ssl = config.ssl
        .withKeyManagerConfig(config.ssl.keyManagerConfig
          .withKeyStoreConfigs(enhanceKeyStoreConfig(config.ssl.keyManagerConfig.keyStoreConfigs)))
        .withTrustManagerConfig(config.ssl.trustManagerConfig
          .withTrustStoreConfigs(trustStores))
    )

    wsClientConfig
  }

  def enhanceKeyStoreConfig(config:scala.collection.immutable.Seq[KeyStoreConfig]):scala.collection.immutable.Seq[KeyStoreConfig] =
    config.filter(_.data.forall(_.nonEmpty)).map { ks =>
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        case (_, None, Some(data)) =>
          createKeyStoreConfig(ks, data)

        case other =>
          logger.info(s"Adding ${other._1} type keystore")
          ks
      }
    }

  private def createTrustStoreConfig(ts: TrustStoreConfig, data: String): TrustStoreConfig = {

    val (filePath, fileBytes) = createTempFileForData(data)

    val keyStore = initKeystore()

    generateCertificates(fileBytes).foreach {
      case c: X509Certificate =>
        val alias = c.getSubjectX500Principal.getName
        keyStore.setCertificateEntry(alias, c)
      case other =>
        logger.warn(s"Expected X509Certificate but got ${other.getType}")
    }

    val stream = new FileOutputStream(filePath)

    try {
      keyStore.store(stream, "".toCharArray)
      logger.info(s"Successfully wrote truststore data to file: $filePath")
      ts.withFilePath(Some(filePath))
    } finally {
      stream.close()

    }
  }

  private def initKeystore(): KeyStore = {
    val keystore = KeyStore.getInstance("jks")
    keystore.load(null, null) // scalastyle:ignore null
    keystore
  }

  private def generateCertificates(file: Array[Byte]): Seq[Certificate] = {
    val stream = new ByteArrayInputStream(file)
    try {
      CertificateFactory
        .getInstance("X.509")
        .generateCertificates(stream)
        .asScala
        .toList
    } finally {
      stream.close()
    }
  }

  /**
    * @return absolute file path with the bytes written to the file
    */
  def createTempFileForData(data: String): (String, Array[Byte]) = {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    try {
      val bytes = base64Decode(data.trim)
      os.write(bytes)
      os.flush()
      file.getAbsolutePath -> bytes
    } finally {
      os.close()
    }
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    logger.info("Creating key store config")
    val (ksFilePath, _) = createTempFileForData(data)
    logger.info(s"Successfully wrote keystore data to file: $ksFilePath")

    val decryptedPass = ks.password
      .filter(_.nonEmpty)
      .map(base64Decode)
      .map(new String(_))

    ks.withFilePath(Some(ksFilePath)).withPassword(decryptedPass)
  }
}

class CustomWSConfigParserModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[WSConfigParser].to[CustomWSConfigParser]
    )

}
