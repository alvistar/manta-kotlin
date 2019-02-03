package demo

import com.google.gson.*
import java.math.BigDecimal
import java.util.*
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type

val MANTAVERSION = "1.6"


class BDSerializer : JsonSerializer<BigDecimal> {
    override fun serialize(src: BigDecimal?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }

}

open class Message {
    companion object {
        private val gsonBuilder = GsonBuilder()

        init {
            gsonBuilder.registerTypeAdapter(BigDecimal::class.java, BDSerializer())
        }

        val myGson = gsonBuilder.create()

        inline fun <reified T> fromJSON(json: String): T? {
            return Gson().fromJson(json, T::class.java)
        }

    }

    fun toJSON(): String? {
        return myGson.toJson(this)
    }


}

data class Destination(val amount: BigDecimal,
                       @SerializedName("destination_address") val destinationAddress: String,
                       val cryptoCurrency: String)

data class Merchant(val name: String,
                    val address: String?) {

}

data class PaymentRequestMessage(val merchant: Merchant,
                                 val amount: BigDecimal,
                                 @SerializedName("fiat_currency") val fiatCurrency: String,
                                 val destinations: List<Destination>,
                                 @SerializedName("supported_cryptos") val supportedCryptos: Set<String>
) : Message() {

    // Fake
    fun getEnvelope(): PaymentRequestEnvelope {
        return PaymentRequestEnvelope(message = this.toJSON()!!, signature = "fake-signature")
    }
}

data class PaymentRequestEnvelope(val message: String,
                                  val signature: String,
                                  val version: String = MANTAVERSION) : Message() {
    fun unpack(): PaymentRequestMessage? {
        return Message.fromJSON<PaymentRequestMessage>(this.message)
    }
}

data class PaymentMessage(@SerializedName ("crypto_currency") val cryptoCurrency: String,
                          @SerializedName ("transaction_hash") val transactionHash: String,
                          val version: String = MANTAVERSION): Message ()