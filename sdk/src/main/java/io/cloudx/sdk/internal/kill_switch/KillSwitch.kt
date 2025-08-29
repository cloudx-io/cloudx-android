package io.cloudx.sdk.internal.kill_switch

object KillSwitch {

    @Volatile
    var sdkDisabledForSession: Boolean = false
}