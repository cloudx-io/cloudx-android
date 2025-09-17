# WinLoss Tracking System - Comprehensive Flow Analysis

## Flow Overview: From BidAdSource Entry Point

### 1. **BidAdSource Entry Point** (Lines 194-200)
**What happens:** After successful bid response, all bids are automatically collected
```kotlin
bidAdSourceResponse.bidItemsByRank.forEach { bidItem ->
    winLossTracker.addBid(auctionId = auctionId, bid = bidItem.bid)
}
```
**Purpose:** Establishes auction context and collects all participating bids
**Data collected:** Bid objects containing price, rank, network, nurl/lurl, etc.

---

### 2. **WinLossTracker.addBid()** → **AuctionBidManager.addBid()**
**What happens:** Each bid gets stored in auction-specific collections
**Status:** All bids start as `BidStatus.PENDING`
**Data stored:** Bid objects + metadata (status, loss reason, actual win price)

---

### 3. **Ad Loading Waterfall** (BannerManager/FullscreenAdManager)
**Current State:** ✅ BannerManager properly implemented (lines 243-313)
**Missing:** Similar integration in FullscreenAdManager

#### For BannerManager:
```kotlin
// ✅ Already implemented in your changes
for ((index, bidItem) in bidItemsByRank.withIndex()) {
    val result = loadOrDestroyBanner(bidAdLoadTimeoutMillis, bidItem.createBidAd)
    if (banner != null) {
        // Winner found
        winnerIndex = index
        break
    } else {
        // Failed bid
        lossReasons[bidItem.bid.id] = LossReason.TechnicalError
    }
}
```

#### **MISSING CALLS (Need to Add):**
```kotlin
// After winner determination (line 271)
AuctionBidManager.setBidWinner(auctionId, bidItem.bid.id)

// After each bid failure (line 280)
AuctionBidManager.setBidLoadResult(auctionId, bidItem.bid.id, false, LossReason.TechnicalError)

// After complete waterfall (line 312)
AuctionBidManager.processAuctionWinLoss(auctionId, winnLossTracker)
```

---

### 4. **Winner Determination & Auction Processing**
**Triggered by:** `AuctionBidManager.processAuctionWinLoss()`
**What happens:**
- Iterates through all bids in auction
- Calls `WinLossTracker.sendWin()` for winners
- Calls `WinLossTracker.sendLoss()` for losers
- Clears auction data

---

### 5. **Win/Loss Notification Generation**
**Path:** `WinLossTracker.sendWin/sendLoss()` → `WinLossFieldResolver.buildWinLossPayload()`

**Field Resolution Process:**
1. Sets win/loss data via `WinLossFieldResolver.setWinData/setLossData()`
2. Resolves dynamic fields using configuration mapping:
   - `"cpm": "bid.price"` → extracts bid price
   - `"url": "sdk.[bid.nurl|bid.lurl]"` → extracts notification URLs
   - `"accountId": "config.accountID"` → gets from config
3. Builds final payload for backend

---

### 6. **HTTP Notification & Caching**
**What happens:**
- Saves payload to local database (caching)
- Sends HTTP request to configured endpoint
- Deletes from cache on success, retries on failure

---

## Method Necessity Analysis

### **AuctionBidManager Methods - Necessity Review:**

#### ✅ **Essential Methods:**
1. **`addBid()`** - Entry point for bid collection
2. **`setBidWinner()`** - Critical for winner determination
3. **`setBidLoadResult()`** - Tracks load success/failure
4. **`processAuctionWinLoss()`** - Main processing trigger
5. **`clearAuction()`** - Memory management
6. **`getWinningBid()`** - Used by field resolver
7. **`getAllBids()`** - Used by field resolver for auction metrics

#### ⚠️ **Questionable Methods:**
8. **`cancelAuction()`** - **QUESTION:** When would this be called? Do we have timeout scenarios?
9. **`getWinningBidPrice()`** - **REDUNDANT:** Could be `getWinningBid()?.price`
10. **`getLosingBids()`** - **UNUSED:** Not referenced anywhere in field resolver
11. **`getBid()`** - **UNUSED:** Not used in current flow

#### **Recommendation:** Remove methods 9-11, clarify usage of method 8

---

### **WinLossTracker Interface Methods - Necessity Review:**

#### ✅ **Essential Methods:**
1. **`addBid()`** - Entry point
2. **`setWinner()`** - Winner determination
3. **`setBidLoadResult()`** - Load result tracking
4. **`processAuctionResults()`** - Main processing trigger
5. **`sendWin()/sendLoss()`** - Core notification methods
6. **`setEndpoint()/setConfig()`** - Configuration
7. **`trySendingPendingWinLossEvents()`** - Retry mechanism

#### **All methods are necessary** - No redundancy found

---

## Missing Integration Points

### **High Priority Missing:**

1. **FullscreenAdManager Integration:**
```kotlin
// Need to add in FullscreenAdManager.popAdAndSetLastShown()
AuctionBidManager.setBidWinner(auctionId, winningBidId)
AuctionBidManager.processAuctionWinLoss(auctionId, winLossTracker)
```

2. **AuctionId Access:**
   - **QUESTION:** How does BannerManager get `auctionId` from `BidAdSourceResponse`?
   - Current response has `auctionId` property, but managers need access to it

3. **Bid ID Mapping:**
   - **QUESTION:** How do managers map from `BannerAdapterDelegate` back to `bidId` for tracking?

### **Medium Priority Missing:**

4. **Error Scenarios:**
   - Auction timeouts - when to call `cancelAuction()`?
   - Network failures during bid loading
   - App backgrounding during auction

5. **Memory Management:**
   - **QUESTION:** Should auction data auto-expire after X time?
   - Current implementation holds data until `processAuctionResults()` called

---

## Questions & Ambiguities

### **Critical Questions:**
1. **Auction ID Propagation:** How do managers access `auctionId` from BidAdSourceResponse?
2. **Bid ID Tracking:** How to map loaded ads back to original `bidId`?
3. **FullscreenAdManager Flow:** Where exactly should winner determination happen?
4. **Timeout Handling:** When/how should `cancelAuction()` be triggered?

### **Design Questions:**
5. **Memory Management:** Auto-cleanup strategy for stale auction data?
6. **Retry Logic:** Should failed auctions be retried or marked as lost?
7. **Concurrent Auctions:** How to handle multiple concurrent auctions per placement?

---

## Architecture Strengths

### ✅ **What's Working Well:**
1. **Clean Separation:** BidAdSource → AuctionBidManager → WinLossTracker → Backend
2. **Smart Field Resolution:** Dynamic payload mapping via configuration
3. **URL Extraction:** Clever nurl/lurl extraction instead of direct HTTP calls
4. **Caching Strategy:** Robust retry mechanism for failed notifications
5. **Rich Context:** Auction-wide metrics available for field resolution

### ✅ **Design Excellence:**
- Configuration-driven payload mapping
- Centralized auction state management
- Reliable notification delivery with retry
- Clean abstraction between bid management and HTTP delivery

---

## Next Steps Priority

### **1. Critical (Blocking):**
- Add missing AuctionBidManager calls to BannerManager waterfall
- Resolve auctionId/bidId access questions
- Implement FullscreenAdManager integration

### **2. Important:**
- Clean up unnecessary AuctionBidManager methods
- Define timeout/cancellation scenarios
- Add memory management strategy

### **3. Nice-to-Have:**
- Performance optimization for concurrent auctions
- Enhanced error handling scenarios
- Metrics collection for auction success rates