package io.cloudx.sdk.internal.privacy

/**
 * US_PRIVACY string representation based on [isDoNotSell] CCPA flag.
 * More [info](https://github.com/InteractiveAdvertisingBureau/USPrivacy/blob/master/CCPA/US%20Privacy%20String.md)
 */
internal fun doNotSellToUSPrivacy(doNotSell: Boolean?): String = when (doNotSell) {
    /**
     * Digital property has determined the use of "us_privacy" string and CCPA does not apply to this transaction.
     */
    null -> "1---"
    /**
     * Digital property has asked a vendor to create a US Privacy String on their behalf,
     * knowing only whether the user has opted of sale of personal data.
     * The user has made a choice to opt out of sale.
     */
    true -> "1-Y-"
    /**
     * Digital property has asked a vendor to create a US Privacy String on their behalf,
     * knowing only whether the user has opted of sale of personal data.
     * The user has made a choice to opt in sale.
     */
    else -> "1-N-"
}