import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.math.BigDecimal
import manta.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.withTimeout
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage

val DESTINATIONS = listOf(
        Destination(amount = BigDecimal(5),
                destinationAddress = "btc_daddress",
                cryptoCurrency = "BTC")
)

val MERCHANT = Merchant(
        name = "Merchant 1",
        address = "5th Avenue"
)

val MESSAGE = PaymentRequestMessage(
        merchant = MERCHANT,
        amount = BigDecimal(10.5),
        fiatCurrency = "euro",
        destinations = DESTINATIONS,
        supportedCryptos = setOf("BTC", "XMR", "NANO")
)


class MYTests : StringSpec() {
    init {

        "Parse url" {
            val result = MantaWallet.parseURL("manta://localhost/JqhCQ64gTYi02xu4GhBzZg==")
            result[0] shouldBe "localhost"
            result[2] shouldBe "JqhCQ64gTYi02xu4GhBzZg=="
        }

        "Parse url with port" {
            val result = MantaWallet.parseURL("manta://127.0.0.1:8000/123")
            result[0] shouldBe "127.0.0.1"
            result[1] shouldBe "8000"
            result[2] shouldBe "123"
        }

        "Parse NFC Url" {
            val result = MantaWallet.parseURL("http://manta.appia.co/127.0.0.1:8000/123")
            result[0] shouldBe "127.0.0.1"
            result[1] shouldBe "8000"
            result[2] shouldBe "123"
        }

        "Parse invalid url" {
            val result = MantaWallet.parseURL("manta://localhost")
            result.count() shouldBe 0
        }

        "Test decode json" {
            val jsonString = """
                {"message": "{\"merchant\": {\"name\": \"Merchant 1\", \"address\": \"5th Avenue\"}, \"amount\": \"10.5\", \"fiat_currency\": \"euro\", \"DESTINATIONS\": [{\"amount\": \"5\", \"destination_address\": \"btc_daddress\", \"crypto_currency\": \"btc\"}, {\"amount\": \"10\", \"destination_address\": \"nano_daddress\", \"crypto_currency\": \"nano\"}], \"supported_cryptos\": [\"xmr\", \"nano\", \"btc\"]}", "signature": "leB7LqWbVQ6Aji2j/xn2mN12hgBO01lgP3XEv6NRsRZGGAd4Ml8fdQKk1GRSRFruvdPP6RxaSI/dY2kn5k4NVGfAyk00S4BK/pPUyyNg37XamfIDqXRWkit8dod3JQKkPik7OcyAwBKSWGUjFjffBiSf/2t9XN4KwHNY7DGMe0fmZsJFAm3X2T9pMLN/RijB/BwM/0wcs+cRBsNe+OfoBTeb7AXisFO8iuDZgPyhdovjFpRCChXFAxX0xr16rDv6rPvJ7ENRxwbqHHNy/QMWs4/0hntiayiv9e3sN96zfH3z7w3G4wsa+QN/rsLCqbVb8Vd+Idion/89C0hYjr0REg==", "version": "1.6"}
                """.trimIndent()

            val envelope = Message.fromJSON<PaymentRequestEnvelope>(jsonString)

            val message = envelope?.unpack()

            message?.merchant?.name shouldBe "Merchant 1"
            message?.amount shouldBe BigDecimal("10.5")


        }

        "Test get payment request" {
            val client = mockk<MqttClient>(relaxed = true)

            val wallet = MantaWallet.factory("manta://localhost:8000/123", client = client)

            verify { client.setCallback(any()) }

            every { client.publish("payment_requests/123/BTC", any(), 1, false) } answers {
                val message = MqttMessage(MESSAGE.getEnvelope().toJSON()!!.toByteArray())
                wallet?.messageArrived("payment_requests/123", message)
            }

            withTimeout(3000) {
                val envelope = wallet?.getPaymentRequest("BTC")

                envelope?.signature.shouldBe("fake-signature")

                val msg = envelope?.unpack()

                msg?.merchant?.name shouldBe "Merchant 1"
            }


        }

        "Test send payment" {
            val client = mockk<MqttClient>(relaxed = true)

            val wallet = MantaWallet.factory("manta://localhost:8000/123", client= client)

            val expected = PaymentMessage(
                    transactionHash = "myhash",
                    cryptoCurrency = "NANO"
            )

            wallet?.sendPayment("myhash", "NANO")

            verify {
                client.subscribe("acks/123")
                client.publish("payments/123", expected.toJSON()?.toByteArray(), 1, false)
            }

        }


    }
}