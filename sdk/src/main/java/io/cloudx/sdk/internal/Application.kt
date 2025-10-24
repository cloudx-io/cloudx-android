package io.cloudx.sdk.internal

import android.app.Application

// TODO. IMPORTANT. Revisit, when background services are in action.
// It might crash(?) when no app's UI (Activity) present.
internal fun Application(): Application = (ApplicationContext() as Application)