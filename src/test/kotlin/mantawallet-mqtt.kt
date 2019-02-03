import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.json.responseJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import demo.MantaWallet
import demo.PaymentRequestEnvelope
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

val STOREURL = "http://localhost:8080/"
val PP_HOST = "http://localhost:8081"

class MantaWalletMQTTTests : StringSpec() {
    var wallet: MantaWallet? = null

    init {

        "Test get payment request" {

            val map = hashMapOf("amount" to "10", "fiat" to "EUR")

            val (request, response, result) = Fuel.post("${STOREURL}merchant_order")
                    .body(Gson().toJson(map))
                    .awaitStringResponseResult()

            val resultMap: Map<String, Any> = Gson().fromJson(result.get(), object : TypeToken<Map<String, Any>>() {}.type)

            wallet = MantaWallet.factory(resultMap["url"] as String)

            val envelope = wallet?.getPaymentRequest("NANO")

            val msg = envelope?.unpack()

            msg?.amount shouldBe BigDecimal(10)
            msg?.fiatCurrency shouldBe "EUR"
        }

        "Test send payment" {
            wallet?.sendPayment(cryptoCurrency = "NANO", transactionHash = "myhash")
        }

    }

}