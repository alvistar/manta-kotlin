package demo

import org.eclipse.paho.client.mqttv3.*
import java.util.regex.Pattern
import kotlinx.coroutines.*

class MantaWallet(val sessionID: String,
                  host: String = "localhost",
                  port: Int = 1883,
                  mqttClient: MqttClient? = null) : MqttCallback {
    private val client: MqttClient
    private val connOpts: MqttConnectOptions
    private var paymentRequestFuture: CompletableDeferred<PaymentRequestEnvelope>? = null

    companion object {
        fun parseURL(url: String): List<String> {
            // val regex = "^manta://((?:\\w|\\.)+)(?::(\\d+))?/(.+)$"
            val regex = "^manta://((?:\\w|\\.)+)(?::(\\d+))?/(.+)$"
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(url)

            if (matcher.find()) {
                return listOf(matcher.group(1), matcher.group(2), matcher.group(3))
            }
            return listOf()

        }

        fun factory(url: String, client: MqttClient? = null): MantaWallet? {
            val result = MantaWallet.parseURL(url)

            if (result.count() > 0) {

                return MantaWallet(sessionID = result[2],
                        host = result[0],
                        port = if (result[1] != null ) result[1].toInt() else 1883,
                        mqttClient = client)
            }

            return null
        }

    }

    init {

        val broker = "tcp://$host:$port"
        val clientid = "manta-java"

        client = mqttClient ?: MqttClient(broker, clientid)

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

        if (tokens[0] == "payment_requests") {
            val envelope = Message.fromJSON<PaymentRequestEnvelope>(message.toString())
            if (envelope != null) {
                paymentRequestFuture?.complete(envelope)
            }
        }
    }

    fun connect() {
        if (client.isConnected) return
        client.connect(connOpts)
    }

    suspend fun getPaymentRequest(cryptoCurrency: String = "all"): PaymentRequestEnvelope? {
        connect()

        client.subscribe("payment_requests/$sessionID")

        val payload = MqttMessage("".toByteArray())

        paymentRequestFuture = CompletableDeferred<PaymentRequestEnvelope>()

        client.publish("payment_requests/$sessionID/$cryptoCurrency", "".toByteArray(), 1, false)

        return paymentRequestFuture?.await()

    }

    fun sendPayment(transactionHash: String, cryptoCurrency: String) {
        connect()

        client.subscribe("acks/$sessionID")

        val message = PaymentMessage (
                transactionHash = transactionHash,
                cryptoCurrency = cryptoCurrency
        )

        client.publish("payments/$sessionID", message.toJSON()?.toByteArray(), 1, false)

    }


}