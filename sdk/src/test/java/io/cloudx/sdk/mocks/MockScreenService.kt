package io.cloudx.sdk.mocks

import io.cloudx.sdk.internal.screen.ScreenService

internal object MockScreenService : ScreenService {
    override suspend fun invoke() = ScreenService.ScreenData(
        1080, 1920, 1080, 1920, 400, 5f
    )
}