# BannerManager: Behavior & Components Overview

This document summarizes the behavior of the banner pipeline around `BannerManager`, based on the current code in the SDK.

## Overview

- `BannerManager` orchestrates banner refresh, bid-and-load, and presentation.
- It merges banner viewability and app foreground to produce an effective visibility signal.
- A cadence clock emits refresh ticks, queueing exactly one tick when hidden or while a request is in-flight.
- On each tick, a single non-retrying bid→load attempt is executed.
- Loaded banners are shown immediately if visible, or stored as a prefetched banner until visible.

Key files:
- `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BannerManager.kt` (interface + factory)
- `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BannerManagerImpl.kt` (implementation)

## Construction & Wiring

`BannerManager(...)` factory wires dependencies and returns `BannerManagerImpl`:
- Builds `BidBannerSource` → a `BidAdSource<BannerAdapterDelegate>` for bidding and ad creation.
- Builds `BannerAdLoader` to load one winner with timeout control.
- Creates `BannerManagerImpl` with visibility, lifecycle, connection, metrics, and the loader.

Created by `AdFactoryImpl.createBanner(...)`, and consumed by `CloudXAdView` on attach:
- `CloudXAdView` provides `CloudXAdViewAdapterContainer` that inserts network ad views into layered `FrameLayout` containers.
- Banner visibility is derived from `createViewabilityTracker(...).isViewable`.

## Core Components

- VisibilityGate (`sdk/internal/ads/banner/components/VisibilityGate.kt`)
  - `effective = bannerVisibility && appForeground` (both are `StateFlow<Boolean>`).
- VisibilityAwareOneQueuedClock (`sdk/internal/ads/banner/components/CadenceClock.kt`)
  - Emits refresh ticks every `refreshSeconds`.
  - Queues exactly one tick if hidden or if a request is in-flight.
  - When becoming visible and not in-flight, emits the queued-hidden tick immediately.
- BannerAdLoader (`sdk/internal/ads/banner/components/BannerAdLoader.kt`)
  - Requests a bid from `BidAdSource` and iterates candidates by rank.
  - For each candidate: builds `BannerAdapterDelegate`, calls `load()` with timeout.
  - Returns first successful banner as `BannerLoadOutcome.Success`, else `NoFill` or failure status.
- BidBannerSource (`sdk/internal/ads/banner/BidBannerSource.kt`)
  - Configures `BidAdSource` to produce ranked candidates and to build ad views via network factories.
  - Adds base/bid/logging decorations.
- BannerAdapterDelegate (`sdk/internal/ads/banner/BannerAdapterDelegate.kt`)
  - Wraps a network banner adapter with coroutine-friendly events: `Load`, `Show`, `Impression`, `Click`, `Error`.
  - Handles NURL impression tracking on `Impression`.
- DefaultBannerPresenter (`sdk/internal/ads/banner/components/BannerPresenter.kt`)
  - Swaps the current banner, forwards `onAdDisplayed`, `onAdClicked`, and `onAdHidden` to the publisher listener.

## Manager Behavior (BannerManagerImpl)

- Initializes components:
  - `gate = VisibilityGate(bannerVisibility, appLifecycleService.isResumed)`
  - `clock = VisibilityAwareOneQueuedClock(intervalMs = refreshSeconds * 1000)`
  - `presenter = DefaultBannerPresenter(...)`
- Subscribes to `gate.effective`:
  - Updates `clock.setVisible(visible)`.
  - When becoming visible, immediately shows any `prefetched` banner.
- Starts the `clock` and subscribes to `clock.ticks`:
  - On each tick → `launchRequest()`.

### Cadence & Visibility Semantics

- On `start()`:
  - Clock begins; if currently visible, emits an immediate tick; if hidden, queues one hidden tick.
- While hidden:
  - Only one refresh tick is queued; no uncontrolled backlog.
- While a request is in-flight:
  - Only one in-flight tick is queued to run after completion (if visible).
- On becoming visible:
  - If not in-flight and a hidden tick is queued, it is emitted immediately, triggering a refresh instantly.

### Request Lifecycle (`launchRequest()`)

- Marks request started on the clock for proper queuing.
- Tracks method metric: `MetricsType.Method.BannerRefresh`.
- Awaits connectivity via `ConnectionStatusService.awaitConnection()`.
- Calls `loader.loadOnce()` and handles outcome:
  - `Success(banner)`:
    - If `gate.effective.value == true` → `presenter.show(banner)`.
    - Else → store banner in `prefetched` until visible.
  - `NoFill` → `listener?.onAdLoadFailed("No fill", CLXErrorCode.NO_FILL)`.
  - `PermanentFailure` → `listener?.onAdLoadFailed("Permanent error", CLIENT_ERROR)`.
  - `TrafficControl` → `listener?.onAdLoadFailed("Ads disabled", ADS_DISABLED)`.
  - `TransientFailure` → calls `stopPermanently(...)` (destroys manager; see notes).
- Marks request finished on the clock to possibly emit a queued in-flight tick.

### Presenting & Events

- `presenter.show(banner)`:
  - Hides and destroys previous banner; fires `onAdHidden(previous)`.
  - Fires `onAdDisplayed(current)`.
  - Listens to banner events:
    - `Click` → `onAdClicked(current)`.
    - First `Error` → logs, fires `onAdHidden(current)`, destroys, clears current; cadence continues.

### Teardown

- `stopPermanently(userMessage, code)`:
  - Fires `onAdLoadFailed` with the fatal error, logs, and calls `destroy()`.
- `destroy()`:
  - Cancels scope/jobs, stops clock, destroys any `prefetched` banner and the presenter.

## Integration Details

- Created by `AdFactoryImpl.createBanner(...)` with:
  - `refreshSeconds`, `adType`, bid factories, bid extras providers, load timeout.
  - `BidApi`, `CdpApi`, `EventTracker`, `MetricsTrackerNew`.
  - `ConnectionStatusService`, `AppLifecycleService`.
- `CloudXAdView` (`sdk/.../CloudXAdView.kt`):
  - Creates the manager on `onAttachedToWindow()`.
  - Supplies `CloudXAdViewAdapterContainer` that adds/removes/adopts child containers.
  - Manages a layered list of containers to keep the newest banner in the foreground.
  - Exposes `show()`/`hide()` and forwards listener to the manager.

## Notable Observations / Potential Deviations vs. MVP Expectations

- Transient failures treated as permanent: `BannerLoadOutcome.TransientFailure` triggers `stopPermanently(...)`. Typically, transient errors should be retried; current behavior tears down the manager.
- Unused parameter: `CloudXAdView` passes `suspendPreloadWhenInvisible` to `createBannerManager`, but `BannerManager` factory/impl ignore it. Current behavior always allows prefetch while hidden.
- Immediate-first-request behavior: When the view first becomes visible, the clock emits a queued-hidden tick instantly (no wait for `refreshSeconds`). Tests rely on this.
- Single non-retrying load per tick: `BannerAdLoader.loadOnce()` intentionally tries candidates sequentially but does not retry a failing network; aligns with “MVP” simplification.
- Prefetch semantics: If a banner loads while hidden, it is stored and shown immediately upon visibility; previous prefetched is destroyed when replaced by a newer prefetch.

## MVP Comparison

### Banner Refresh (MVP) vs Implementation

Aligns:
- Single-flight: Only one active request at a time. Clock queues at most one tick while hidden or in-flight, and `reqJob` is single.
- Wall-clock interval continues while hidden; if elapsed while hidden, exactly one request is queued for when visible.
- No refresh while not visible: Requests are launched only when an effective-visibility tick is emitted.
- Hidden during request: Request completes; on success, creative is prefetched and shown on next visible.
- No banner-level retries: Loader does a single attempt per tick.
- Destroying view cancels timers/requests and clears prefetched creatives.

Deviations:
- Timer restart semantics: MVP says “Wait to restart the timer until the bid request either succeeds or fails (avoid stacking).” The current clock is free-running. If an interval elapses during an in-flight request, it queues an immediate next tick upon completion. This can cause back-to-back requests immediately after a long request, rather than waiting a full interval post-completion.
- Transient failure handling: MVP expects emitting an error and waiting for the next interval. Current code treats `TransientFailure` as fatal and calls `destroy()`.

Recommendations:
- Adjust cadence to restart the interval after `markRequestFinished()` (i.e., don’t queue an immediate in-flight tick; reset the delay instead).
- Handle `TransientFailure` like other non-fill cases: call `onAdLoadFailed(...)` and continue cadence.

### Network Retries (MVP) vs Implementation

Aligns:
- Max 1 retry per bid request (`retryMax = 1`).
- Retries on server errors (5xx), exceptions, timeouts: enabled via Ktor retry (`retryOnExceptionOrServerErrors`) with constant 1s delay.
- Retries on 429 Too Many Requests: explicit predicate plus respect for `Retry-After` header.
- No retries on 4xx (except 429): 4xx mapped to `CLIENT_ERROR` without retry.
- Fixed delay (1s) with no exponential backoff in v1.
- After retries are exhausted, the final failure surfaces back up; application flow isn’t blocked at the network layer.

Notes:
- Total elapsed time = per-attempt timeout + 1s retry delay (+ second attempt). This matches MVP “bounded by these retry delays”.
- Mapping in `BidAdSource`: 4xx → PermanentFailure; ADS_DISABLED (kill switch) → TrafficControl; other network/server/timeouts → TransientFailure.

### Bid Request Kill Switch (MVP) vs Implementation

Aligns:
- Server-triggered sample-out via `X-CloudX-Status: ADS_DISABLED` on 204 mapped to `CLXErrorCode.ADS_DISABLED` (308).
- Surfaces to banner via `BidSourceResult.TrafficControl`, and to listener as `onAdLoadFailed(ADS_DISABLED)`.
- Only affects current request; next ticks proceed normally and are subject to server sampling.


## Listener Surface

- Success path: `onAdDisplayed(banner)` → `onAdClicked(banner)` (on click) → `onAdHidden(banner)` (on swap/error/destroy).
- Failures: `onAdLoadFailed(CloudXAdError)` for `NoFill`, `PermanentFailure`, `TrafficControl`, and before teardown on `TransientFailure` path.

## References (paths)

- Manager: `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BannerManager.kt`, `BannerManagerImpl.kt`
- Components: `components/CadenceClock.kt`, `components/BannerAdLoader.kt`, `components/VisibilityGate.kt`, `components/BannerPresenter.kt`
- Adapter delegate: `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BannerAdapterDelegate.kt`
- Bid source: `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BidBannerSource.kt`, `sdk/src/main/java/io/cloudx/sdk/internal/ads/BidAdSource.kt`
- View integration: `sdk/src/main/java/io/cloudx/sdk/CloudXAdView.kt`, `sdk/src/main/java/io/cloudx/sdk/internal/common/ViewabilityTracker.kt`
