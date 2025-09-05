package io.cloudx.demo.demoapp.loglistview

import io.cloudx.demo.demoapp.MainActivity

// Common Filtered and renamed log tag rules for any screen (banner, int, rew)
fun commonLogTagListRules(forTag: String): String? {
    // Handle new CX:Component format
    val component = if (forTag.startsWith("CX:")) {
        forTag.substringAfter("CX:")
    } else {
        forTag
    }
    
    return when (component) {
        MainActivity.TAG -> SDK
        "BidAdSourceImpl" -> "Bidding"
        "Endpoints" -> "SDK"
        else -> if (component.endsWith("Initializer")) component else null
    }
}

private const val SDK = "SDK"