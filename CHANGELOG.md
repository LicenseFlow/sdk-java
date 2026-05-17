# Changelog

All notable changes to the LicenseFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-02-17

## [2.2.0] - 2026-05-17

### Added
- `updateEntitlement(id, updates)` for PUT-style entitlement edits (parity with JS/Python/PHP/Ruby)
- `validateProofOffline(proof, secret)` HS256 JWT verification (parity with other SDKs)
- `consumeCredits(...)` overload with `referenceId`, `referenceType`, and `metadata` params
- Cache TTL: in-memory verification cache now expires after 5 min (configurable via 4-arg constructor)

### Changed
- Internal cache backed by `ConcurrentHashMap` for thread safety

### Added
- Environment scoping support: `environmentId` parameter in all license operations
- Cache isolation between environments to prevent cross-environment cache collisions
- Floating license lease methods: `checkoutLicense()`, `checkinLicense()`, `getLeaseStatus()`
- Credit system methods: `consumeCredits()`, `getCreditsBalance()`
- Heartbeat support: `startHeartbeat()`, `stopHeartbeat()`

### Changed
- Cache key format now includes environment context
- Defaults to `"default"` environment when `environmentId` is null

## [2.0.0] - 2026-01-19

### Added
- Entitlements system: `hasFeature()`, `getEntitlement()`
- Release management: `checkForUpdates()`, `downloadArtifact()`
- Offline licensing: `verifyOfflineLicense()` with Ed25519 signature verification
- Usage tracking: `recordUsage()`

### Changed
- Verification response now includes optional `entitlements` field
- Updated to OkHttp 4.x for improved connection pooling

## [1.0.0] - 2025-06-01

### Added
- Initial release with license activation, verification, and deactivation
- Hardware ID auto-detection
- In-memory caching with configurable TTL
- Retry logic with exponential backoff
- Custom exception classes

[2.1.0]: https://github.com/licenseflow/java-sdk/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/licenseflow/java-sdk/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/licenseflow/java-sdk/releases/tag/v1.0.0
