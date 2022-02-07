package info.blockchain.wallet.exceptions

class NoSuchAddressException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
    constructor(message: String) : this(message, null)
}
