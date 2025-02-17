package com.blockchain.core.price.impl.assetpricestore

import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.core.price.impl.SupportedTickerList
import com.blockchain.core.price.model.AssetPriceError
import com.blockchain.core.price.model.AssetPriceNotCached
import com.blockchain.core.price.model.AssetPriceRecord2
import com.blockchain.outcome.Outcome
import com.blockchain.outcome.doOnSuccess
import com.blockchain.outcome.map
import com.blockchain.store.KeyedStoreRequest
import com.blockchain.store.StoreRequest
import com.blockchain.store.StoreResponse
import com.blockchain.store.firstOutcome
import info.blockchain.balance.Currency
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal class AssetPriceStore2(
    private val cache: AssetPriceStoreCache,
    private val supportedTickersStore: SupportedTickersStore,
) {

    private val quoteTickerToCurrentPrices = ConcurrentHashMap<String, List<AssetPriceRecord2>>()
    lateinit var fiatQuoteTickers: SupportedTickerList
        private set

    internal suspend fun warmSupportedTickersCache(): Outcome<AssetPriceError, Unit> =
        supportedTickersStore.stream(StoreRequest.Fresh)
            .firstOutcome()
            .doOnSuccess { tickerGroup ->
                fiatQuoteTickers = tickerGroup.fiatQuoteTickers
            }
            .map { @Suppress("RedundantUnitExpression") Unit }

    internal fun getCurrentPriceForAsset(
        base: Currency,
        quote: Currency
    ): Flow<StoreResponse<AssetPriceError, AssetPriceRecord2>> =
        if (base.networkTicker == quote.networkTicker) {
            flowOf(createEqualityRecordResponse(base.networkTicker, quote.networkTicker))
        } else {
            cache.stream(
                KeyedStoreRequest.Cached(
                    key = AssetPriceStoreCache.Key.GetAllCurrent(quote.networkTicker),
                    forceRefresh = false
                )
            ).onEach { response ->
                if (response is StoreResponse.Data) {
                    quoteTickerToCurrentPrices.putAll(response.data.groupBy { it.quote })
                }
            }.findAssetOrError(base, quote)
                .distinctUntilChanged()
        }

    internal fun getYesterdayPriceForAsset(
        base: Currency,
        quote: Currency
    ): Flow<StoreResponse<AssetPriceError, AssetPriceRecord2>> =
        cache.stream(
            KeyedStoreRequest.Cached(
                key = AssetPriceStoreCache.Key.GetAllYesterday(quote.networkTicker),
                forceRefresh = false
            )
        ).findAssetOrError(base, quote)
            .distinctUntilChanged()

    internal suspend fun getHistoricalPriceForAsset(
        base: Currency,
        quote: Currency,
        timeSpan: HistoricalTimeSpan
    ): Outcome<AssetPriceError, List<AssetPriceRecord2>> = cache.stream(
        KeyedStoreRequest.Cached(
            key = AssetPriceStoreCache.Key.GetHistorical(base, quote.networkTicker, timeSpan),
            forceRefresh = false
        )
    ).firstOutcome()

    fun getCachedAssetPrice(fromAsset: Currency, toFiat: Currency): AssetPriceRecord2 =
        quoteTickerToCurrentPrices[toFiat.networkTicker]
            ?.find { it.base == fromAsset.networkTicker }
            ?: throw AssetPriceNotCached(fromAsset.networkTicker, toFiat.networkTicker)

    fun getCachedFiatPrice(fromFiat: Currency, toFiat: Currency): AssetPriceRecord2 =
        quoteTickerToCurrentPrices[toFiat.networkTicker]
            ?.find { it.base == fromFiat.networkTicker }
            ?: throw AssetPriceNotCached(fromFiat.networkTicker, toFiat.networkTicker)

    private fun Flow<StoreResponse<AssetPriceError, List<AssetPriceRecord2>>>.findAssetOrError(
        base: Currency,
        quote: Currency
    ): Flow<StoreResponse<AssetPriceError, AssetPriceRecord2>> =
        map { response ->
            when (response) {
                is StoreResponse.Data -> {
                    val assetPrice = response.data.find {
                        it.base == base.networkTicker && it.quote == quote.networkTicker
                    }
                    if (assetPrice != null) StoreResponse.Data(assetPrice)
                    else StoreResponse.Error(AssetPriceError.PricePairNotFound(base, quote))
                }
                is StoreResponse.Error -> response
                is StoreResponse.Loading -> response
            }
        }

    private fun createEqualityRecordResponse(
        base: String,
        quote: String
    ): StoreResponse<AssetPriceError, AssetPriceRecord2> = StoreResponse.Data(
        AssetPriceRecord2(
            base = base,
            quote = quote,
            rate = 1.0.toBigDecimal(),
            fetchedAt = Calendar.getInstance().timeInMillis,
            marketCap = 0.0
        )
    )
}
