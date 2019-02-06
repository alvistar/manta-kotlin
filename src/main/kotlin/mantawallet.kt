package manta

import org.eclipse.paho.client.mqttv3.*
import java.util.regex.Pattern
import kotlinx.coroutines.*
 import kotlinx.coroutines.future.future
import mu.KotlinLogging
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.util.concurrent.CompletableFuture

val LOG = KotlinLogging.logger {}

class MantaWallet(val sessionID: String,
                  host: String = "localhost",
                  port: Int = 1883,
                  persistence: MqttClientPersistence = MqttDefaultFilePersistence(),
                  mqttClient: MqttClient? = null) : MqttCallback {
    private val client: MqttClient
    private val connOpts: MqttConnectOptions
    private var paymentRequestFuture: CompletableDeferred<PaymentRequestEnvelope>? = null

    companion object {
        fun parseURL(url: String): List<String> {
            // val regex = "^manta://((?:\\w|\\.)+)(?::(\\d+))?/(.+)$"

            val regex = "^(?:manta://|http://manta\\.appia\\.co/)((?:\\w|\\.)+)(?::(\\d+))?\\/(.+)$"
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(url)

            if (matcher.find()) {
                return listOf(matcher.group(1), matcher.group(2), matcher.group(3))
            }
            return listOf()

        }

        fun factory(url: String,
                    persistence: MqttClientPersistence = MqttDefaultFilePersistence(),
                    client: MqttClient? = null): MantaWallet? {
            val result = MantaWallet.parseURL(url)

            if (result.count() > 0) {

                @Suppress("SENSELESS_COMPARISON")
                return MantaWallet(sessionID = result[2],
                        host = result[0],
                        port = if (result[1] != null ) result[1].toInt() else 1883,
                        persistence = persistence,
                        mqttClient = client)
            }

            return null
        }

    }

    init {

        val broker = "tcp://$host:$port"
        val clientid = MqttClient.generateClientId()

        client = mqttClient ?: MqttClient(broker, clientid, persistence)

        connOpts = MqttConnectOptions()

        client.setCallback(this)

        connOpts.isCleanSession = true

    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
    }

    override fun connectionLost(cause: Throwable?) {

    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        val tokens = topic?.split('/') ?: return
        LOG.debug("New msg on topic $topic with payload ${message.toString()}")

        if (tokens[0] == "payment_requests") {
            LOG.info ("New payment_request for sessionID ${tokens[1]}")
            val envelope = Message.fromJSON<PaymentRequestEnvelope>(message.toString())
            if (envelope != null) {
                paymentRequestFuture?.complete(envelope)
            }
        }
    }

    fun connect() {
        if (client.isConnected) return
        client.connect(connOpts)
        LOG.info("Connected to MQTT broker with ID ${client.clientId}")
    }

    suspend fun getPaymentRequest(cryptoCurrency: String = "all", timeout: Long = 3000): PaymentRequestEnvelope? {
        connect()

        client.subscribe("payment_requests/$sessionID")

        val payload = MqttMessage("".toByteArray())

        paymentRequestFuture = CompletableDeferred<PaymentRequestEnvelope>()

        client.publish("payment_requests/$sessionID/$cryptoCurrency", "".toByteArray(), 1, false)

        LOG.info("Get Payment Request $sessionID for $cryptoCurrency")

        var paymentRequestEnvelope: PaymentRequestEnvelope? = null;

        try {

            withTimeout(timeout) {
                paymentRequestEnvelope = paymentRequestFuture?.await()
            }
        }    catch (t: TimeoutCancellationException) {
            LOG.error ("Timoout in get payment request")
        }

        return paymentRequestEnvelope;

    }

    fun getPaymentRequestAsync(cryptoCurrency: String = "all"): CompletableFuture<PaymentRequestEnvelope?> {
        return GlobalScope.future { getPaymentRequest(cryptoCurrency) }
    }

    fun sendPayment(transactionHash: String, cryptoCurrency: String) {
        connect()

        client.subscribe("acks/$sessionID")

        val message = PaymentMessage (
                transactionHash = transactionHash,
                cryptoCurrency = cryptoCurrency
        )

        client.publish("payments/$sessionID", message.toJSON()?.toByteArray(), 1, false)
        LOG.info ("Sending payment with hash: $transactionHash crypto: $cryptoCurrency")
    }


}