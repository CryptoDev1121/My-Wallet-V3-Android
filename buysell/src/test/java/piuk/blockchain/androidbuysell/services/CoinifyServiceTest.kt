package piuk.blockchain.androidbuysell.services

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should equal to`
import org.junit.Before
import org.junit.Test
import piuk.blockchain.androidbuysell.MockWebServerTest
import piuk.blockchain.androidbuysell.api.PATH_COINFY_SIGNUP_TRADER
import piuk.blockchain.androidbuysell.api.PATH_COINFY_TRADES_QUOTE
import piuk.blockchain.androidbuysell.models.coinify.QuoteRequest
import piuk.blockchain.androidbuysell.models.coinify.SignUpDetails
import piuk.blockchain.androidcore.data.rxjava.RxBus
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

class CoinifyServiceTest : MockWebServerTest() {

    private lateinit var subject: CoinifyService
    private val rxBus = RxBus()
    private val moshiConverterFactory = MoshiConverterFactory.create()
    private val rxJava2CallAdapterFactory = RxJava2CallAdapterFactory.create()

    @Before
    override fun setUp() {
        super.setUp()

        val okHttpClient = OkHttpClient.Builder()
                .build()
        val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(server.url("/").toString())
                .addConverterFactory(moshiConverterFactory)
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .build()

        subject = CoinifyService(retrofit, rxBus)
    }

    @Test
    fun `signUp success`() {
        // Arrange
        server.enqueue(
                MockResponse()
                        .setResponseCode(200)
                        .setBody(SIGN_UP_RESPONSE)
        )
        // Act
        val testObserver = subject.signUp(
                path = PATH_COINFY_SIGNUP_TRADER,
                signUpDetails = SignUpDetails.basicSignUp(
                        "example@email.com",
                        "USD",
                        "US",
                        "token"
                )
        ).test()
        // Assert
        testObserver.awaitTerminalEvent()
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        val traderResponse = testObserver.values().first()
        traderResponse.trader.id `should equal to` 754035
        traderResponse.trader.profile.address.countryCode `should equal to` "US"
        server.takeRequest().path `should equal to` "/$PATH_COINFY_SIGNUP_TRADER"
    }

    @Test
    fun `getQuote success`() {
        // Arrange
        server.enqueue(
                MockResponse()
                        .setResponseCode(200)
                        .setBody(QUOTE_RESPONSE)
        )
        // Act
        val testObserver = subject.getQuote(
                path = PATH_COINFY_TRADES_QUOTE,
                quoteRequest = QuoteRequest("BTC", "USD")
        ).test()
        // Assert
        testObserver.awaitTerminalEvent()
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        val quote = testObserver.values().first()
        quote.baseCurrency `should equal to` "BTC"
        quote.quoteCurrency `should equal to` "USD"
        quote.baseAmount `should equal to` -1
        quote.quoteAmount `should equal to` 8329.89
        server.takeRequest().path `should equal to` "/$PATH_COINFY_TRADES_QUOTE"
    }

    companion object {

        private const val SIGN_UP_RESPONSE = "{\n" +
                "  \"trader\": {\n" +
                "    \"id\": 754035,\n" +
                "    \"email\": \"example@email.com\",\n" +
                "    \"defaultCurrency\": \"USD\",\n" +
                "    \"profile\": {\n" +
                "      \"address\": {\n" +
                "        \"country\": \"US\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"level\": {}\n" +
                "  },\n" +
                "  \"offlineToken\": \"aGFja2VydHlwZXIuY29tIGlzIG15IElERQ==\"\n" +
                "}"

        private const val QUOTE_RESPONSE = "{\n" +
                "  \"baseCurrency\": \"BTC\",\n" +
                "  \"quoteCurrency\": \"USD\",\n" +
                "  \"baseAmount\": -1,\n" +
                "  \"quoteAmount\": 8329.89,\n" +
                "  \"issueTime\": \"2018-04-13T12:42:32.000Z\",\n" +
                "  \"expiryTime\": \"2018-04-13T12:42:32.000Z\"\n" +
                "}"

    }
}