# Changelog

All notable changes to this project will be documented in this file.

## [1.3.0] - 2026-06-22
### Added
- **Production Release**: This is a proper release build following the successful testo test (v1.2.0).
  - Sample Code on Billing Screen 
  - APK built with `versionCode=4`, `versionName=1.3.0`
  - Update system fully validated — Android client detects `serverCode(4) > currentCode(3)` → prompts update
  - Diagnostics panel (StatsActivity) confirms update status in real-time

### Changed
- All v1.2.0 features now promoted from test to production-ready.

---
*Checkpoint: v1.3.0 (Build 4) — Production release. Next release will increment to versionCode=5.*

## [1.0.0] - 2024-05-22
### Added
- **Version Release System**: Integrated versioning and display in LoginActivity.
- **Manual Sync System**: Added user-initiated two-phase push synchronization with verification.
- **Optimized Syncing**: Improved sync performance with background threads, retries, and transactional integrity.
- **Image Downloading Progress**: Enhanced UI feedback for image downloads during synchronization, showing real-time percentage and counts.

---
*Checkpoint: Initial release notes with major sync and versioning updates.*
