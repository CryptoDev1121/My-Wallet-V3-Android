package piuk.blockchain.android.ui.linkbank.presentation.openbanking.permission

import com.blockchain.commonarch.presentation.mvi_v2.ModelState
import com.blockchain.core.payments.model.YapilyInstitution

data class OpenBankingPermissionModelState(
    val termsOfServiceLink: String = "",
    val institution: YapilyInstitution? = null
) : ModelState
