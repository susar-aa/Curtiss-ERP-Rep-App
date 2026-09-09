# Curtiss Rep App — Comprehensive Analysis Report
**Date:** August 11, 2026
**Version Analyzed:** v1.17.0 (Build 17)
**Package:** `com.example.curtiss` | **Platform:** Android (Java, minSdk 22, targetSdk 34)
**Backend:** PHP MVC (Curtiss-ERP) | **Database:** MySQL + SQLite (offline)

---

## 1. PROJECT OVERVIEW

Android mobile app for sales representatives managing daily routes, billing, collections, and two-way sync with the Curtiss ERP server. The app operates **offline-first** with a local SQLite database and syncs bidirectionally with the PHP backend.

### Source File Inventory

| File | Lines | Purpose |
|------|-------|---------|
| `BillingActivity.java` | **5,331** | Main billing/invoicing screen (monolithic) |
| `SyncManager.java` | **2,251** | Two-way sync engine (push & pull) |
| `DatabaseHelper.java` | **1,668** | SQLite local database management |
| `MainActivity.java` | 1,403 | Home dashboard with route management |
| `RepDashboardController.php` | 2,156 | Backend API (sync_pull, sync_push, sync_verify) |
| `BillingActivity.java.rej` | 1,777 | **UNRESOLVED MERGE CONFLICT** |
| `BillingActivity.java.orig` | 3,689 | **UNRESOLVED MERGE CONFLICT** |
| `diff_backup.txt` / `diff_backup_utf8.txt` | 4,368 | Merge conflict backups |

---

## 2. 🔴 CRITICAL ISSUES (Build-Breaking / Data-Loss)

### 2.1 CRITICAL-1: Manifest References Non-Existent Activities
**Severity:** 🔴 CRITICAL — **App will crash at runtime / fail to build**

`AndroidManifest.xml` (lines 78-86) declares two activities that **do not exist** in the source code:
```xml
<activity android:name=".CreditBillsActivity" ... />
<activity android:name=".CreditCheckoutActivity" ... />
```

**Impact:** If any code path attempts to launch these activities, the app will crash with `ActivityNotFoundException`. The build may also fail with a manifest merger error.

**Solution:** Either:
1. **Remove** these declarations from the manifest (if the features are no longer used), OR
2. **Create** the missing `CreditBillsActivity.java` and `CreditCheckoutActivity.java` classes.

### 2.2 CRITICAL-2: Unresolved Merge Conflicts in BillingActivity
**Severity:** 🔴 CRITICAL — **Code corruption risk**

Files present in the project:
- `BillingActivity.java.rej` (1,777 lines) — rejected patch hunks
- `BillingActivity.java.orig` (3,689 lines) — original pre-merge file
- `diff_backup.txt` / `diff_backup_utf8.txt` (4,368 lines) — diff backups

**Impact:** The current `BillingActivity.java` (5,331 lines) may contain incomplete or conflicting code from a failed merge. The `.rej` file shows rejected changes that were never applied.

**Solution:** Manually review and resolve the merge conflicts. Compare `BillingActivity.java` against `.orig` and apply the rejected hunks from `.rej`. Then delete the `.rej`, `.orig`, and `diff_backup*` files.

### 2.3 CRITICAL-3: Fake Firebase API Key in LocationTrackingService
**Severity:** 🔴 CRITICAL — **Location tracking completely broken**

`LocationTrackingService.java` (line 71) uses a **fake placeholder API key**:
```java
.setApiKey("AIzaSyFakePlaceholderKey1234567890ABCDEF")
```

**Impact:** Firebase Realtime Database initialization will fail. Live location tracking for reps **will not work** — the service starts but never successfully pushes location data.

**Solution:** Replace with the real Firebase API key, or better, use `google-services.json` configuration. Also add proper error handling to notify the user when Firebase fails.

### 2.4 CRITICAL-4: `startRouteOffline()` Deletes Synced Data
**Severity:** 🔴 CRITICAL — **Data loss risk**

`DatabaseHelper.java` (lines 587-616) — `startRouteOffline()`:
```java
// Delete synced invoices
db.execSQL("DELETE FROM invoices WHERE is_synced = 1");
// Delete synced payments
db.execSQL("DELETE FROM payments WHERE is_synced = 1");
```

**Impact:** When a rep starts a new route, **all synced invoices and payments are permanently deleted** from the local device. If the server data was lost or the sync was incomplete, this data is unrecoverable.

**Solution:** Do NOT delete synced data. Instead, archive it or keep it for reference. Only clear data when the user explicitly requests a "Clear Local Data" action.

### 2.5 CRITICAL-5: `usesCleartextTraffic="true"` in Manifest
**Severity:** 🔴 CRITICAL — **Security vulnerability**

`AndroidManifest.xml` (line 31):
```xml
android:usesCleartextTraffic="true"
```

**Impact:** Allows unencrypted HTTP traffic. If the base URL is changed to an HTTP endpoint (which the app supports via the hidden base URL dialog), all data — including passwords, customer data, and financial records — is transmitted in plaintext.

**Solution:** Set `android:usesCleartextTraffic="false"` and use a network security config that only allows cleartext for specific development hosts.

---

## 3. 🔴 SYNC ISSUES

### 3.1 SYNC-1: Records Stuck in "Syncing" State on Crash
**Severity:** 🔴 HIGH

`SyncManager.executePushSafe()` (lines 1086-1097) marks records as `sync_status = 2` (Syncing) before the push. If the app crashes or is killed during the push, these records remain stuck in "Syncing" state forever and are never retried.

**Solution:** Add a recovery mechanism in `onOpen()` or at sync start that resets `sync_status = 2` records back to `sync_status = 1` (Pending) if they've been in "Syncing" for more than X minutes.

### 3.2 SYNC-2: Timezone Mismatch in Delta Sync
**Severity:** 🔴 HIGH

`SyncManager.executePull()` (line 138) uses `last_sync_timestamp` from local device time, but the server (`RepDashboardController.php` line 91) sets `date_default_timezone_set('UTC')` and uses `updated_at` columns. If the device timezone differs from UTC, delta syncs will miss records or re-download everything.

**Solution:** Use the server's `system_date` (already returned in the pull response) as the `last_sync_timestamp`, not the device's local time.

### 3.3 SYNC-3: `executeVerification()` Uses `getColumnIndexOrThrow()`
**Severity:** 🔴 HIGH — **Crash risk**

`SyncManager.executeVerification()` (lines 1831, 1846, 1861, 1876) uses `cursor.getColumnIndexOrThrow("uuid")` which will crash if the `uuid` column doesn't exist in older database schemas.

**Solution:** Replace with `DatabaseHelper.safeGetString()` / `safeGetInt()` utility methods.

### 3.4 SYNC-4: `unproductive_visits` Not Included in Pre-Check
**Severity:** 🟡 MEDIUM

`SyncManager.executePushSafe()` (lines 1068-1072) pre-check query doesn't include `unproductive_visits`. If only unproductive visits are pending, the push is skipped.

**Solution:** Add `unproductive_visits` to the pre-check UNION query.

### 3.5 SYNC-5: `unproductive_visits` Not Included in Verification
**Severity:** 🟡 MEDIUM

`SyncManager.executeVerification()` doesn't verify `unproductive_visits` records. They can be marked as synced without server confirmation.

**Solution:** Add unproductive visits to the verification loop.

### 3.6 SYNC-6: `active_route` JSON Order Dependency
**Severity:** 🟡 MEDIUM

`SyncManager.parseAndSaveSyncData()` uses `syncTempLocalRouteId[0]` which is set when `active_route` is parsed. If `active_route_invoices` appears before `active_route` in the JSON stream, the route ID will be `-1` and invoices won't be linked to the route.

**Solution:** Buffer `active_route_invoices` until `active_route` is parsed, or use a two-pass approach.

### 3.7 SYNC-7: `getOutstandingCustomersByActiveRouteMainTerritory()` Uses `getColumnIndexOrThrow()`
**Severity:** 🟡 MEDIUM — **Crash risk**

`DatabaseHelper.java` (lines 1412-1414) uses `getColumnIndexOrThrow("customer_id")`, `getColumnIndexOrThrow("customer_name")`, `getColumnIndexOrThrow("total_outstanding")` which can crash if the `credit_invoices` table schema is outdated.

**Solution:** Use `safeGetInt()` / `safeGetString()` / `safeGetDouble()`.

### 3.8 SYNC-8: No Conflict Resolution for Concurrent Edits
**Severity:** 🟡 MEDIUM

If two reps edit the same customer profile offline, the last push wins. There's no timestamp-based conflict detection or merge strategy.

**Solution:** Implement `updated_at` comparison on the server. If the server's `updated_at` is newer than the pushed `updated_at`, reject the update and return a conflict response.

### 3.9 SYNC-9: `executePush()` Doesn't Handle Partial Failures
**Severity:** 🟡 MEDIUM

`executePush()` processes customers, routes, invoices, payments, and unproductive visits in a single HTTP request. If one invoice fails validation, the entire push fails and all records are marked as failed.

**Solution:** Process records individually or in smaller batches, with per-record error reporting.

### 3.10 SYNC-10: Image Download Blocks Sync Completion
**Severity:** 🟡 MEDIUM

`SyncManager.executePull()` (lines 191-235) starts an async thread that polls `image_download_queue` for up to 90 seconds, blocking the sync completion callback.

**Solution:** Make image downloads fully asynchronous and don't block the sync completion.

---

## 4. 🔴 SECURITY ISSUES

### 4.1 SEC-1: No SSL Certificate Pinning
**Severity:** 🔴 CRITICAL

All network calls use raw `HttpURLConnection` with no certificate pinning. A MITM attack can intercept all data.

**Solution:** Migrate to OkHttp/Retrofit with certificate pinning.

### 4.2 SEC-2: Password Sent in Plaintext JSON
**Severity:** 🔴 HIGH

`LoginActivity.performNetworkLogin()` (line 280) sends the password as plaintext JSON:
```java
payload.put("password", password);
```

**Solution:** Use HTTPS (already the default) and consider adding client-side password hashing or using a challenge-response authentication mechanism.

### 4.3 SEC-3: No Biometric/PIN App Lock
**Severity:** 🟡 MEDIUM

The app opens directly to the dashboard if a session exists. Anyone with the device can access customer data, billing, and financial records.

**Solution:** Add `BiometricPrompt` from AndroidX Biometric (already in dependencies) for app unlock.

### 4.4 SEC-4: `allowBackup="true"` Exposes Sensitive Data
**Severity:** 🟡 MEDIUM

`AndroidManifest.xml` (line 23) has `android:allowBackup="true"`. The SQLite database and SharedPreferences can be extracted via ADB backup.

**Solution:** Set `android:allowBackup="false"` or use `android:fullBackupContent` to exclude sensitive data.

### 4.5 SEC-5: `REQUEST_INSTALL_PACKAGES` Permission
**Severity:** 🟡 MEDIUM

The app requests `REQUEST_INSTALL_PACKAGES` permission, which allows installing APKs. This is needed for the update feature but is a security risk.

**Solution:** Keep the permission but ensure the APK download URL is always HTTPS and verify the APK signature before installation.

### 4.6 SEC-6: No Server-Side Session Token
**Severity:** 🟡 MEDIUM

The app authenticates with username/password but the server doesn't issue a session token. All subsequent API calls just send `X-User-ID` header, which can be spoofed.

**Solution:** Implement a proper session token (JWT or similar) that's issued at login and validated on every API call.

---

## 5. 🟡 CODE QUALITY & ARCHITECTURE ISSUES

### 5.1 ARCH-1: BillingActivity is 5,331 Lines — Extremely Monolithic
**Severity:** 🔴 HIGH

**Solution:** Extract into:
- `BillingViewModel` — cart state, product loading, checkout logic
- `ProductListFragment` — product catalog
- `CartFragment` — cart summary
- `CustomerSelectFragment` — customer selection
- `CheckoutService` (already exists) — invoice creation

### 5.2 ARCH-2: SyncManager is 2,251 Lines
**Severity:** 🟡 MEDIUM

**Solution:** Split into `SyncService`, `SyncRepository`, `SyncWorker`, `SyncConflictResolver`.

### 5.3 ARCH-3: No MVVM/Repository Pattern
**Severity:** 🟡 MEDIUM

Database queries are directly in Activities. No ViewModel or Repository layer.

**Solution:** Add ViewModel + Repository + Room (or keep SQLite but abstract it).

### 5.4 ARCH-4: No Dependency Injection
**Severity:** 🟡 MEDIUM

`DatabaseHelper.getInstance(this)` is called everywhere. No DI framework.

**Solution:** Add Hilt or manual DI.

### 5.5 ARCH-5: No Unit Tests
**Severity:** 🟡 MEDIUM

`app/src/test/` is empty. No JUnit or Mockito tests.

**Solution:** Add unit tests for `DatabaseHelper`, `SyncManager`, `CheckoutService`, `CurrencyUtils`.

### 5.6 ARCH-6: No UI Tests
**Severity:** 🟡 MEDIUM

`app/src/androidTest/` is empty. No Espresso tests.

**Solution:** Add Espresso UI tests for critical flows (login, billing, sync).

### 5.7 ARCH-7: Dead Code in MainActivity
**Severity:** 🟢 LOW

`MainActivity.onResume()` (lines 254-273) computes `pendingCount` but never uses it. `txtSyncStatus` and `progressSync` are commented out but still referenced.

**Solution:** Clean up dead code.

### 5.8 ARCH-8: Hardcoded Null Views in BillingActivity
**Severity:** 🟡 MEDIUM

`BillingActivity` hardcodes several views to `null`:
```java
btnViewCart = null;
layoutExpandedSearch = null;
btnSearchToggle = null;
btnClearSearch = null;
layoutCartViewMode = null;
layoutCartCheckoutMode = null;
txtCheckoutCustomerName = null;
txtCheckoutItemSummary = null;
```

**Impact:** Cart view button, search toggle, and checkout customer info features are **non-functional**.

**Solution:** Either implement these features or remove the dead code.

---

## 6. 🟡 FUNCTIONAL BUGS

### 6.1 BUG-1: `CheckoutService` Doesn't Validate Stock
`CheckoutService.performCheckout()` doesn't check if `quantity_on_hand - quantity_reserved >= quantity` before saving. Over-selling is possible.

**Solution:** Add stock validation before saving the invoice.

### 6.2 BUG-2: `CheckoutService` Doesn't Enforce Credit Limit
`CheckoutService.performCheckout()` doesn't check if the customer's outstanding balance + new invoice total exceeds their `credit_limit`.

**Solution:** Add credit limit enforcement with a warning/block dialog.

### 6.3 BUG-3: `MainActivity` Network Callback Doesn't Trigger Sync
`MainActivity.registerNetworkCallback()` (lines 1346-1365) logs "Network available. Sync deferred to user manual initiation." but doesn't actually trigger a sync.

**Solution:** Trigger a pull sync when network becomes available.

### 6.4 BUG-4: `SyncWorker` Returns `Result.failure()` on No Session
`SyncWorker.doWork()` (line 25) returns `Result.failure()` when no user is logged in. This causes WorkManager to stop retrying, but the worker should return `Result.success()` to avoid unnecessary retries.

**Solution:** Return `Result.success()` when there's no session.

### 6.5 BUG-5: `UpdateActivity` Doesn't Verify APK MD5
`SplashActivity` receives `apkMd5` from the server but `UpdateActivity` never verifies the downloaded APK against it.

**Solution:** Verify the MD5 checksum before installing.

### 6.6 BUG-6: `LocationHelper` Falls Back to Hardcoded Coordinates
`LocationHelper.captureCurrentLocation()` (lines 23, 53, 64) falls back to hardcoded `(7.1824, 79.8801)` coordinates when GPS is unavailable. This creates fake location data.

**Solution:** Use `(0, 0)` or `null` for missing locations and flag them as "no GPS" on the server.

### 6.7 BUG-7: `DatabaseHelper.getVariationReservedQty()` Doesn't Handle NULL
`getVariationReservedQty()` (line 1555) uses `SUM(ii.quantity)` which can return `NULL` if no rows match. The `cursor.getInt(0)` will return 0 for NULL, which is correct, but the query could be more robust with `COALESCE`.

**Solution:** Add `COALESCE(SUM(ii.quantity), 0)`.

### 6.8 BUG-8: `MainActivity` Payment Collection Doesn't Validate Total
`MainActivity.showCollectPaymentDialog()` allows collecting more than the outstanding balance. The balance label turns green but there's no validation to prevent over-collection.

**Solution:** Add validation to prevent collecting more than the outstanding amount.

---

## 7. 🟡 BACKEND ISSUES

### 7.1 BE-1: Migration Failures in `app_errors.log`
The error log shows repeated failures:
- `seed_petty_cash_config` — `Unknown column 'cash_limit'` (repeated 10+ times)
- `items_description` — `Cannot execute queries while other unbuffered queries are active`
- `create_stock_batches` — same unbuffered query error
- Database connection refused errors

**Solution:** Fix the migration scripts. Add `PDO::MYSQL_ATTR_USE_BUFFERED_QUERY` to the Database class. Ensure migrations are idempotent.

### 7.2 BE-2: `sync_pull` Loads All Products into Memory
`RepDashboardController.sync_pull()` (line 179) calls `$this->itemModel->getItemsDelta($lastSync)` which loads ALL products into memory before streaming. With `memory_limit = 512M`, this can exhaust memory on large catalogs.

**Solution:** Stream products like customers are streamed.

### 7.3 BE-3: `sync_push` Has No Global Transaction
`RepDashboardController.sync_push()` processes customers, routes, invoices, payments, and unproductive visits without a global database transaction. If an error occurs mid-processing, partial data is committed.

**Solution:** Wrap the entire push processing in a transaction, or implement per-record error handling with proper rollback.

### 7.4 BE-4: `sync_verify` Doesn't Check `unproductive_visits`
`RepDashboardController.sync_verify()` only checks invoices, payments, routes, and customers. Unproductive visits are never verified.

**Solution:** Add unproductive visits verification.

### 7.5 BE-5: No Rate Limiting on API Endpoints
The API endpoints (`api_login`, `sync_pull`, `sync_push`, `sync_verify`) have no rate limiting. Brute-force attacks on login are possible.

**Solution:** Add rate limiting (e.g., max 5 login attempts per minute per IP).

---

## 8. 🟢 MISSING FEATURES & IMPROVEMENTS

### 8.1 HIGH PRIORITY

| # | Feature | Description | Effort |
|---|---------|-------------|--------|
| 1 | **Push Notifications (FCM)** | Route assignments, critical updates, sync alerts | 8 hrs |
| 2 | **Barcode Scanning** | Quick product lookup via camera | 8 hrs |
| 3 | **Customer Credit Limit Enforcement** | Block billing if credit limit exceeded | 4 hrs |
| 4 | **Sync Health Dashboard** | Show last sync time, pending/failed records per table | 6 hrs |
| 5 | **Per-Record Sync Status** | Color-coded indicators on invoice/customer lists | 6 hrs |
| 6 | **Failed Sync Retry with Backoff** | Retry individual failed records with exponential backoff | 6 hrs |
| 7 | **Customer Visit Tracking** | Notes, photos, geo-tagging per visit | 10 hrs |
| 8 | **Route Navigation** | "Navigate" button opens Google Maps to customer | 2 hrs |

### 8.2 MEDIUM PRIORITY

| # | Feature | Description | Effort |
|---|---------|-------------|--------|
| 1 | **Receipt Printing** | Bluetooth thermal printer support | 12 hrs |
| 2 | **Signature Capture** | Delivery confirmation via signature pad | 6 hrs |
| 3 | **Invoice PDF Generation** | Generate + share invoice PDF (partially exists in HistoryActivity) | 6 hrs |
| 4 | **Sales Performance Charts** | Bar/line charts in StatsActivity | 8 hrs |
| 5 | **Customer Ledger/Statement** | Invoice + payment history per customer | 8 hrs |
| 6 | **Real-time Stock Check** | API call to check current stock on server | 4 hrs |
| 7 | **Search/Filter in Product Catalog** | Filter by category, brand, price range | 4 hrs |
| 8 | **Multi-language Support** | Extract strings to `res/values-*` | 6 hrs |
| 9 | **Discount Approval Workflow** | Manager PIN for over-limit discounts | 4 hrs |

### 8.3 NICE-TO-HAVE

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Offline Map Tiles** | Google Maps offline caching |
| 2 | **Voice-to-Text Notes** | For customer visit notes |
| 3 | **WhatsApp Payment Link** | Share payment link via WhatsApp |
| 4 | **Expense Tracking** | Photo receipts + mileage logging |
| 5 | **Competitor Price Tracking** | Per-product competitor pricing |
| 6 | **Warehouse Transfer Request** | From field to warehouse |
| 7 | **Push-to-Talk** | With office dispatcher |

---

## 9. PERFORMANCE ISSUES

### 9.1 PERF-1: No Pagination in Product Catalog
`BillingActivity.loadCatalogItems()` loads ALL products into memory at once. With a large catalog, this causes slow loading and high memory usage.

**Solution:** Implement pagination (load 50 at a time with scroll-to-load).

### 9.2 PERF-2: `ListView` Used Instead of `RecyclerView`
Several screens use `ListView` with `BaseAdapter` instead of `RecyclerView` with `ViewHolder`. This causes poor scrolling performance.

**Solution:** Migrate to `RecyclerView` with `ViewHolder` pattern.

### 9.3 PERF-3: `loadCatalogItems()` Creates New Thread Per Call
`BillingActivity.loadCatalogItems()` (line 791) creates a new `Executors.newSingleThreadExecutor()` on every call. This leaks threads.

**Solution:** Use a shared executor or `AsyncTask`/`Coroutine`.

### 9.4 PERF-4: `getOutstandingCustomersByActiveRouteMainTerritory()` Logs Every Row
`DatabaseHelper.java` (lines 1408-1417) logs every row to Logcat with `Log.d()`. This is excessive logging that slows down the app.

**Solution:** Remove the debug logging or gate it behind `BuildConfig.DEBUG`.

---

## 10. GRADLE / BUILD CONFIGURATION ISSUES

### 10.1 GRADLE-1: AGP Version 9.2.1 is Very New
`libs.versions.toml` uses `agp = "9.2.1"` which may not be stable or compatible with the installed Android Studio.

**Solution:** Verify AGP compatibility. Consider using a stable version like 8.x.

### 10.2 GRADLE-2: `minSdk = 22` with `security-crypto` Requires API 23+
`build.gradle.kts` has `minSdk = 22` but `androidx.security:security-crypto:1.0.0` requires API 23+. The manifest uses `tools:overrideLibrary` to bypass this, but `SecurePreferences` falls back to plaintext on API 22 devices.

**Solution:** Either raise `minSdk` to 23, or accept the fallback (documented in `SecurePreferences.java`).

### 10.3 GRADLE-3: Duplicate Material Dependencies
`build.gradle.kts` has both `libs.google.material` and `libs.material` which are the same library.

**Solution:** Remove the duplicate.

### 10.4 GRADLE-4: Firebase Dependencies Without `google-services.json`
The app includes `firebase-database` and `firebase-common` but there's no `google-services.json` file. The app initializes Firebase programmatically with a fake API key.

**Solution:** Either add `google-services.json` or remove the Firebase dependencies if location tracking is not critical.

---

## 11. PRIORITIZED ACTION PLAN

### Phase 1: Critical Fixes (Immediate — 1-2 days)
1. **Fix manifest** — Remove or create `CreditBillsActivity` / `CreditCheckoutActivity`
2. **Resolve merge conflicts** — Fix `BillingActivity.java` and delete `.rej`/`.orig`/`diff_backup*` files
3. **Fix Firebase API key** — Replace fake key or remove Firebase
4. **Fix `startRouteOffline()`** — Stop deleting synced data
5. **Fix `usesCleartextTraffic`** — Set to `false`
6. **Fix `getColumnIndexOrThrow()` crashes** — Replace with safe getters

### Phase 2: Sync Reliability (1 week)
1. **Fix stuck "Syncing" records** — Add recovery mechanism
2. **Fix timezone mismatch** — Use server `system_date` for `last_sync_timestamp`
3. **Add `unproductive_visits` to pre-check and verification**
4. **Fix `active_route` JSON order dependency**
5. **Add conflict resolution** — Timestamp-based detection

### Phase 3: Security Hardening (1 week)
1. **Add SSL certificate pinning** — Migrate to OkHttp
2. **Add biometric/PIN app lock**
3. **Set `allowBackup="false"`**
4. **Implement server-side session tokens**
5. **Add rate limiting to API endpoints**

### Phase 4: Architecture Refactoring (2-3 weeks)
1. **Split BillingActivity** — Extract ViewModel, Fragments, Repository
2. **Split SyncManager** — Extract SyncService, SyncRepository
3. **Add MVVM/Repository pattern**
4. **Add unit tests** — JUnit + Mockito
5. **Add UI tests** — Espresso

### Phase 5: Feature Enhancements (Ongoing)
1. **Push notifications (FCM)**
2. **Barcode scanning**
3. **Credit limit enforcement**
4. **Sync health dashboard**
5. **Customer visit tracking**
6. **Receipt printing**
7. **Sales charts**

---

## 12. SUMMARY STATISTICS

| Category | Count |
|----------|-------|
| 🔴 Critical Issues | 5 |
| 🔴 Sync Issues | 10 |
| 🔴 Security Issues | 6 |
| 🟡 Architecture Issues | 8 |
| 🟡 Functional Bugs | 8 |
| 🟡 Backend Issues | 5 |
| 🟢 Missing Features (High) | 8 |
| 🟢 Missing Features (Medium) | 9 |
| 🟢 Missing Features (Nice-to-have) | 7 |
| **Total** | **66** |

---

## 13. BOTTOM LINE

The Curtiss Rep App has a solid foundation with offline-first architecture, two-phase sync, and a comprehensive feature set. However, it suffers from:

1. **Build-breaking issues** — Missing activities in manifest, unresolved merge conflicts
2. **Data-loss risks** — `startRouteOffline()` deletes synced data
3. **Broken features** — Firebase location tracking (fake API key), cart view button, search toggle
4. **Security vulnerabilities** — No SSL pinning, cleartext traffic, no session tokens
5. **Sync reliability issues** — Stuck records, timezone mismatches, missing verification
6. **Massive technical debt** — 5,331-line BillingActivity, no tests, no architecture pattern

**Immediate priorities:** Fix the manifest, resolve merge conflicts, fix the Firebase key, and stop the data deletion in `startRouteOffline()`. These are blocking issues that could cause crashes, data loss, or broken functionality in production.

**Long-term priorities:** Refactor the monolithic activities, add proper security (SSL pinning, session tokens, biometric lock), implement conflict resolution, and add comprehensive test coverage.