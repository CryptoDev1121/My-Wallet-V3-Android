package com.blockchain.koin

import android.content.Context
import android.preference.PreferenceManager
import com.blockchain.common.util.AndroidDeviceIdGenerator
import com.blockchain.core.Database
import com.blockchain.core.TransactionsCache
import com.blockchain.core.buy.BuyOrdersCache
import com.blockchain.core.buy.BuyPairsCache
import com.blockchain.core.chains.EvmNetworksService
import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.chains.bitcoincash.BchDataStore
import com.blockchain.core.chains.erc20.Erc20DataManager
import com.blockchain.core.chains.erc20.Erc20DataManagerImpl
import com.blockchain.core.chains.erc20.call.Erc20BalanceCallCache
import com.blockchain.core.chains.erc20.call.Erc20HistoryCallCache
import com.blockchain.core.custodial.BrokerageDataManager
import com.blockchain.core.custodial.TradingBalanceCallCache
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.custodial.TradingBalanceDataManagerImpl
import com.blockchain.core.dynamicassets.DynamicAssetsDataManager
import com.blockchain.core.dynamicassets.impl.DynamicAssetsDataManagerImpl
import com.blockchain.core.eligibility.EligibilityRepository
import com.blockchain.core.eligibility.cache.ProductsEligibilityStore
import com.blockchain.core.interest.InterestBalanceCallCache
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.core.interest.InterestBalanceDataManagerImpl
import com.blockchain.core.limits.LimitsDataManager
import com.blockchain.core.limits.LimitsDataManagerImpl
import com.blockchain.core.payload.DataManagerPayloadDecrypt
import com.blockchain.core.payments.PaymentsDataManager
import com.blockchain.core.payments.PaymentsDataManagerImpl
import com.blockchain.core.payments.cache.LinkedCardsStore
import com.blockchain.core.payments.cache.PaymentMethodsEligibilityStore
import com.blockchain.core.payments.cards.CardsCache
import com.blockchain.core.user.NabuUserDataManager
import com.blockchain.core.user.NabuUserDataManagerImpl
import com.blockchain.core.user.WatchlistDataManager
import com.blockchain.core.user.WatchlistDataManagerImpl
import com.blockchain.domain.eligibility.EligibilityService
import com.blockchain.logging.LastTxUpdateDateOnSettingsService
import com.blockchain.logging.LastTxUpdater
import com.blockchain.payload.PayloadDecrypt
import com.blockchain.preferences.AppInfoPrefs
import com.blockchain.preferences.AuthPrefs
import com.blockchain.preferences.BankLinkingPrefs
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.DashboardPrefs
import com.blockchain.preferences.FeatureFlagOverridePrefs
import com.blockchain.preferences.NotificationPrefs
import com.blockchain.preferences.OnboardingPrefs
import com.blockchain.preferences.RatingPrefs
import com.blockchain.preferences.RemoteConfigPrefs
import com.blockchain.preferences.SecureChannelPrefs
import com.blockchain.preferences.SecurityPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.preferences.ThePitLinkingPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.sunriver.XlmHorizonUrlFetcher
import com.blockchain.sunriver.XlmTransactionTimeoutFetcher
import com.blockchain.wallet.SeedAccess
import com.blockchain.wallet.SeedAccessWithoutPrompt
import info.blockchain.wallet.payload.WalletPayloadService
import info.blockchain.wallet.util.PrivateKeyFactory
import java.util.UUID
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.androidcore.data.access.PinRepository
import piuk.blockchain.androidcore.data.access.PinRepositoryImpl
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.auth.WalletAuthService
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.EthMessageSigner
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManagerSeedAccessAdapter
import piuk.blockchain.androidcore.data.payload.PayloadService
import piuk.blockchain.androidcore.data.payload.PromptingSeedAccessAdapter
import piuk.blockchain.androidcore.data.payments.PaymentService
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.SSLPinningEmitter
import piuk.blockchain.androidcore.data.rxjava.SSLPinningObservable
import piuk.blockchain.androidcore.data.rxjava.SSLPinningSubject
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import piuk.blockchain.androidcore.data.settings.PhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.data.settings.SettingsEmailAndSyncUpdater
import piuk.blockchain.androidcore.data.settings.SettingsPhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsService
import piuk.blockchain.androidcore.data.settings.datastore.SettingsDataStore
import piuk.blockchain.androidcore.data.settings.datastore.SettingsMemoryStore
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.AESUtilWrapper
import piuk.blockchain.androidcore.utils.CloudBackupAgent
import piuk.blockchain.androidcore.utils.DeviceIdGenerator
import piuk.blockchain.androidcore.utils.DeviceIdGeneratorImpl
import piuk.blockchain.androidcore.utils.EncryptedPrefs
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcore.utils.UUIDGenerator

val coreModule = module {

    single { RxBus() }

    single { SSLPinningSubject() }.bind(SSLPinningObservable::class).bind(SSLPinningEmitter::class)

    factory {
        WalletAuthService(
            walletApi = get()
        )
    }

    factory { PrivateKeyFactory() }

    scope(payloadScopeQualifier) {

        factory {
            TradingBalanceCallCache(
                balanceService = get(),
                assetCatalogue = get(),
                authHeaderProvider = get()
            )
        }

        scoped {
            TradingBalanceDataManagerImpl(
                balanceCallCache = get()
            )
        }.bind(TradingBalanceDataManager::class)

        scoped {
            BrokerageDataManager(
                brokerageService = get(),
                authenticator = get()
            )
        }

        scoped {
            LimitsDataManagerImpl(
                limitsService = get(),
                exchangeRatesDataManager = get(),
                assetCatalogue = get(),
                authenticator = get()
            )
        }.bind(LimitsDataManager::class)

        factory {
            ProductsEligibilityStore(
                authenticator = get(),
                productEligibilityApi = get()
            )
        }

        scoped {
            EligibilityRepository(
                productsEligibilityStore = get()
            )
        }.bind(EligibilityService::class)

        factory {
            InterestBalanceCallCache(
                balanceService = get(),
                assetCatalogue = get(),
                authHeaderProvider = get()
            )
        }

        scoped {
            BuyPairsCache(nabuService = get())
        }
        scoped {
            TransactionsCache(
                nabuService = get(),
                authenticator = get()
            )
        }

        scoped {
            CardsCache(
                paymentMethodsService = get(),
                authenticator = get()
            )
        }

        scoped {
            BuyOrdersCache(authenticator = get(), nabuService = get())
        }

        scoped {
            InterestBalanceDataManagerImpl(
                balanceCallCache = get()
            )
        }.bind(InterestBalanceDataManager::class)

        factory {
            EvmNetworksService(
                remoteConfig = get()
            )
        }

        scoped {
            EthDataManager(
                payloadDataManager = get(),
                ethAccountApi = get(),
                ethDataStore = get(),
                metadataRepository = get(),
                lastTxUpdater = get(),
                evmNetworksService = get(),
                nonCustodialEvmService = get()
            )
        }.bind(EthMessageSigner::class)

        factory {
            Erc20BalanceCallCache(
                erc20Service = get(),
                evmService = get(),
                assetCatalogue = get()
            )
        }

        factory {
            Erc20HistoryCallCache(
                ethDataManager = get(),
                erc20Service = get(),
                evmService = get(),
                assetCatalogue = get()
            )
        }

        scoped {
            Erc20DataManagerImpl(
                ethDataManager = get(),
                balanceCallCache = get(),
                historyCallCache = get(),
                assetCatalogue = get(),
                ethMemoForHotWalletFeatureFlag = get(ethMemoHotWalletFeatureFlag),
                ethLayerTwoFeatureFlag = get(ethLayerTwoFeatureFlag)
            )
        }.bind(Erc20DataManager::class)

        factory { BchDataStore() }

        scoped {
            BchDataManager(
                payloadDataManager = get(),
                bchDataStore = get(),
                bitcoinApi = get(),
                defaultLabels = get(),
                metadataRepository = get(),
                remoteLogger = get()
            )
        }

        factory {
            PayloadService(
                payloadManager = get()
            )
        }

        factory {
            PayloadDataManager(
                payloadService = get(),
                privateKeyFactory = get(),
                bitcoinApi = get(),
                payloadManager = get(),
                remoteLogger = get()
            )
        }.bind(WalletPayloadService::class)

        factory {
            DataManagerPayloadDecrypt(
                payloadDataManager = get(),
                bchDataManager = get()
            )
        }.bind(PayloadDecrypt::class)

        factory { PromptingSeedAccessAdapter(PayloadDataManagerSeedAccessAdapter(get()), get()) }
            .bind(SeedAccessWithoutPrompt::class)
            .bind(SeedAccess::class)

        scoped { EthDataStore() }

        scoped { WalletOptionsState() }

        scoped {
            SettingsDataManager(
                settingsService = get(),
                settingsDataStore = get(),
                currencyPrefs = get(),
                walletSettingsService = get(),
                assetCatalogue = get()
            )
        }

        scoped { SettingsService(get()) }

        scoped {
            SettingsDataStore(SettingsMemoryStore(), get<SettingsService>().getSettingsObservable())
        }

        factory {
            WalletOptionsDataManager(
                authService = get(),
                walletOptionsState = get(),
                settingsDataManager = get(),
                explorerUrl = getProperty("explorer-api")
            )
        }.bind(XlmTransactionTimeoutFetcher::class)
            .bind(XlmHorizonUrlFetcher::class)

        scoped { FeeDataManager(get()) }

        factory {
            AuthDataManager(
                prefs = get(),
                authApiService = get(),
                walletAuthService = get(),
                pinRepository = get(),
                aesUtilWrapper = get(),
                remoteLogger = get()
            )
        }

        factory { LastTxUpdateDateOnSettingsService(get()) }.bind(LastTxUpdater::class)

        factory {
            SendDataManager(
                paymentService = get(),
                lastTxUpdater = get()
            )
        }

        factory { SettingsPhoneNumberUpdater(get()) }.bind(PhoneNumberUpdater::class)

        factory { SettingsEmailAndSyncUpdater(get(), get()) }.bind(EmailSyncUpdater::class)

        scoped {
            NabuUserDataManagerImpl(
                nabuUserService = get(),
                authenticator = get(),
                tierService = get()
            )
        }.bind(NabuUserDataManager::class)

        scoped {
            LinkedCardsStore(
                authenticator = get(),
                paymentMethodsService = get()
            )
        }

        scoped {
            PaymentMethodsEligibilityStore(
                authenticator = get(),
                paymentMethodsService = get()
            )
        }

        scoped {
            PaymentsDataManagerImpl(
                paymentsService = get(),
                paymentMethodsService = get(),
                tradingBalanceDataManager = get(),
                simpleBuyPrefs = get(),
                authenticator = get(),
                googlePayFeatureFlag = get(googlePayFeatureFlag),
                googlePayManager = get(),
                assetCatalogue = get(),
                linkedCardsStore = get(),
                cardsCache = get(),
                cachingStoreFeatureFlag = get(cachingStoreFeatureFlag)
            )
        }.bind(PaymentsDataManager::class)

        scoped {
            WatchlistDataManagerImpl(
                authenticator = get(),
                watchlistService = get(),
                assetCatalogue = get()
            )
        }.bind(WatchlistDataManager::class)
    }

    single {
        DynamicAssetsDataManagerImpl(
            discoveryService = get()
        )
    }.bind(DynamicAssetsDataManager::class)

    factory {
        AndroidDeviceIdGenerator(
            ctx = get()
        )
    }

    factory {
        DeviceIdGeneratorImpl(
            platformDeviceIdGenerator = get(),
            analytics = get()
        )
    }.bind(DeviceIdGenerator::class)

    factory {
        object : UUIDGenerator {
            override fun generateUUID(): String = UUID.randomUUID().toString()
        }
    }.bind(UUIDGenerator::class)

    single {
        PrefsUtil(
            ctx = get(),
            store = get(),
            backupStore = CloudBackupAgent.backupPrefs(ctx = get()),
            idGenerator = get(),
            uuidGenerator = get(),
            assetCatalogue = get(),
            environmentConfig = get()
        )
    }.bind(PersistentPrefs::class)
        .bind(CurrencyPrefs::class)
        .bind(NotificationPrefs::class)
        .bind(DashboardPrefs::class)
        .bind(SecurityPrefs::class)
        .bind(ThePitLinkingPrefs::class)
        .bind(RemoteConfigPrefs::class)
        .bind(SimpleBuyPrefs::class)
        .bind(RatingPrefs::class)
        .bind(WalletStatus::class)
        .bind(EncryptedPrefs::class)
        .bind(AuthPrefs::class)
        .bind(AppInfoPrefs::class)
        .bind(BankLinkingPrefs::class)
        .bind(SecureChannelPrefs::class)
        .bind(FeatureFlagOverridePrefs::class)
        .bind(OnboardingPrefs::class)

    factory {
        PaymentService(
            payment = get(),
            dustService = get()
        )
    }

    factory {
        PreferenceManager.getDefaultSharedPreferences(
            /* context = */ get()
        )
    }

    factory(featureFlagsPrefs) {
        get<Context>().getSharedPreferences("FeatureFlagsPrefs", Context.MODE_PRIVATE)
    }

    single {
        PinRepositoryImpl()
    }.bind(PinRepository::class)

    factory { AESUtilWrapper() }

    single {
        Database(driver = get())
    }
}
