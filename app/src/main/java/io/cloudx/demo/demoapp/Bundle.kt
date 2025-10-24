package io.cloudx.demo.demoapp

import android.os.Bundle

fun Bundle.putPlacements(placementNames: ArrayList<String>) {
    putStringArrayList(KEY_PLACEMENT_NAME, placementNames)
}

fun Bundle.getPlacements(): ArrayList<String?> = getStringArrayList(KEY_PLACEMENT_NAME) ?: arrayListOf()

private const val KEY_PLACEMENT_NAME = "KEY_PLACEMENT_NAME"