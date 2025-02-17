package com.blockchain.deeplinking.processor

import android.net.Uri
import com.blockchain.deeplinking.navigation.Destination
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplinkProcessorV2Test {

    private val deeplinkProcessorV2Subject = DeeplinkProcessorV2()

    @Test
    fun process() {
    }

    @Test
    fun `test parse of assetview deeplink URI`() {
        val assetViewTestURL = Uri.parse("https://www.login.blockchain.com/app/asset?code=BTC")
        val test = deeplinkProcessorV2Subject.process(assetViewTestURL).test()
        test.assertValue { deeplinkResult ->
            deeplinkResult is DeepLinkResult.DeepLinkResultSuccess &&
                deeplinkResult.destination is Destination.AssetViewDestination &&
                (deeplinkResult.destination as Destination.AssetViewDestination).networkTicker == "BTC"
        }
    }

    @Test
    fun `test parse of assetBuy deeplink URI`() {
        val assetBuyTestURL = Uri.parse("https://www.login.blockchain.com/app/asset/buy?code=BTC&amount=50")
        val test = deeplinkProcessorV2Subject.process(assetBuyTestURL).test()
        test.assertValue { deeplinkResult ->
            deeplinkResult is DeepLinkResult.DeepLinkResultSuccess &&
                deeplinkResult.destination is Destination.AssetBuyDestination &&
                (deeplinkResult.destination as Destination.AssetBuyDestination).networkTicker == "BTC" &&
                (deeplinkResult.destination as Destination.AssetBuyDestination).amount == "50"
        }
    }

    @Test
    fun `test parse of activityView deeplink URI`() {
        val activityViewTestURL = Uri.parse("https://www.login.blockchain.com/app/activity")
        val test = deeplinkProcessorV2Subject.process(activityViewTestURL).test()
        test.assertValue { deeplinkResult ->
            deeplinkResult is DeepLinkResult.DeepLinkResultSuccess &&
                deeplinkResult.destination is Destination.ActivityDestination
        }
    }

    @Test
    fun `test parse of assetSend deeplink URI`() {
        val assetSendTestURL = Uri.parse(
            "https://www.login.blockchain.com/app/asset/send?" +
                "code=BTC&" +
                "amount=0.1&" +
                "address=2bIRbcq3xIgHSUgBaWenCCU0jh6KI2F2cf"
        )
        val test = deeplinkProcessorV2Subject.process(assetSendTestURL).test()
        test.assertValue { deeplinkResult ->
            deeplinkResult is DeepLinkResult.DeepLinkResultSuccess &&
                deeplinkResult.destination is Destination.AssetSendDestination &&
                (deeplinkResult.destination as Destination.AssetSendDestination).networkTicker == "BTC" &&
                (deeplinkResult.destination as Destination.AssetSendDestination).amount == "0.1" &&
                (deeplinkResult.destination as Destination.AssetSendDestination).accountAddress == "2bIRbcq3xIgHSUgBaWenCCU0jh6KI2F2cf"
        }
    }
}
