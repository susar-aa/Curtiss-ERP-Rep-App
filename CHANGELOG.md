# Changelog

All notable changes to this project will be documented in this file.

## [1.2.0] - 2026-06-20
### Added
- **testo Release Build**: This is a test release (testo) to verify the end-to-end update release system:
  - APK built with `versionCode=3`, `versionName=1.2.0`
  - Uploaded via ERP Release Panel to test automatic APK metadata extraction
  - Android client detects `serverCode(3) > currentCode(2)` → prompts update
  - Diagnostics panel (StatsActivity) confirms "Update Available" / "Up to Date"

### Changed
- **All v1.1.0 features fully implemented and finalized**: Automatic APK metadata extraction, update diagnostics panel, streamlined versionCode comparison, catalog browsing, billing workflow, synced worker, and restructured navigation.

---
*Checkpoint: v1.2.0 (Build 3) — Test release (testo) to validate the APK update release system end-to-end.*

## [1.0.0] - 2024-05-22
### Added
- **Version Release System**: Integrated versioning and display in LoginActivity.
- **Manual Sync System**: Added user-initiated two-phase push synchronization with verification.
- **Optimized Syncing**: Improved sync performance with background threads, retries, and transactional integrity.
- **Image Downloading Progress**: Enhanced UI feedback for image downloads during synchronization, showing real-time percentage and counts.

---
*Checkpoint: Initial release notes with major sync and versioning updates.*
