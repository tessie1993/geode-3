# Play Store release specification

Checked 7 October 2026. **Release remains blocked** until the gates below have
evidence. The existence of a release workflow is not evidence of a shippable app.

## Build and signing

1. Preserve `dev.geode`; confirm whether it already exists in the owner's Play
   Console before changing package identity. Choose the next unused versionCode
   there, not by guessing from this checkout's `32`.
2. Use the pinned Gradle wrapper/checksum, dependency catalog and recursive
   submodules. Record the source SHA and dependency revisions for the release.
3. Run compile, all module unit tests, focused instrumentation, static analysis,
   formatting and release lint in Actions. Detekt 1.23.8 requires a compatible
   runtime separate from the Java 25 compilation toolchain.
4. Build the minified release AAB with R8 resource shrinking. Keep JNI entry
   points, serializers and third-party bindings with tested rules. Exercise the
   release build; debug success does not prove R8 correctness.
5. Supply the existing repository secret names: `MUSICVIZ_KEYSTORE_BASE64`,
   `MUSICVIZ_KEYSTORE_PASSWORD`, `MUSICVIZ_KEY_ALIAS`, `MUSICVIZ_KEY_PASSWORD`.
   Workflow maps them to `GEODE_*`. Never commit a keystore or send a private key
   in chat. Enrol in Play App Signing and preserve the upload-key recovery path.
6. Set `GEODE_REQUIRE_RELEASE_SIGNING=true` for distribution builds. Empty/missing
   values or missing file must fail. Cryptographically verify the AAB signature,
   record its checksum, retain R8 mapping and full native debug symbols.
7. Run `:app:checkNativePageAlignment` (Python 3 required). It checks every
   packaged `.so`, all PT_LOAD alignments, malformed ELF, RELRO end alignment,
   and uncompressed APK ZIP offsets. AAB generation alone does not prove APK
   alignment: inspect bundletool config for `PAGE_ALIGNMENT_16K`, generate APKs,
   run `zipalign -c -P 16 -v 4`, and install on a 16 KB runtime.
8. Verify update from the previous signed production version with saved playlists,
   presets, URI grants, projects and entitlement state. Debug keys are not the
   production upgrade identity; `dev.geode.debug` installs alongside production.

Primary sources: [app signing](https://developer.android.com/studio/publish/app-signing),
[16 KB guidance](https://developer.android.com/guide/practices/page-sizes),
[target API](https://developer.android.com/google/play/requirements/target-sdk).
The target API page checked for this blueprint requires API 36 for new apps and
updates from 31 August 2026. Recheck Console requirements at upload.

## Permission and data inventory

| Capability | Permission/service boundary | User-facing behavior |
|---|---|---|
| Music library | Version-appropriate audio/media read access; SAF as alternative | Ask when importing/browsing; denial leaves document selection available |
| Local playback | Media playback foreground service + MediaSession | Accurate notification controls; no surprise background start |
| Microphone | RECORD_AUDIO; microphone FGS only if authorized background capture is designed | Explicit source choice, live indicator, stop control and prompt stop on revocation |
| Other-app capture | MediaProjection consent + eligible playback capture + projection FGS | Explain restrictions; stop on revoked projection; never claim protected media support |
| Export | Media processing service on applicable versions; compatible fallback on older OS | Progress/cancel; handle service timeout and storage exhaustion |
| Wallpaper | WallpaperService | No implicit microphone capture; pause invisible rendering |
| Auth/backup/billing | Reviewed network use and INTERNET when actually implemented | Disclose identity, purchases and chosen backup data; core local use stays available |

Audit the **merged release manifest**, including transitive SDK additions. Keep
components unexported unless needed; exported playback/browse components must
validate allowed controllers and inputs. PendingIntents are explicit and immutable
unless a documented API requires otherwise. Limit FileProvider paths and grants.
Disable cleartext traffic. Secrets, tokens and purchase records must be excluded
from Android backup and logs; user-created non-sensitive data follows an explicit
backup policy.

## Privacy and account requirements

The current `docs/privacy-policy.html` says the app has no INTERNET permission.
That statement cannot remain unchanged when auth/Drive/backend billing is added.
Before those integrations ship, publish the revised policy with developer identity,
support contact, data purpose/location, providers, retention, deletion, optional
backup contents and microphone processing behavior.

Complete Data safety from actual code and SDK behavior, including diagnostic and
purchase verification services. Do not reuse competitors' “no data collected”
declarations. Identify collected vs shared, ephemeral vs retained, required vs
optional, security controls, deletion and third-party processing.

For applicable account creation, implement in-app deletion and a functioning
external deletion page, not just sign-out. Explain retained purchase/fraud records
and delete eligible account/backup data. A subscription cancellation link is
separate from account deletion and should be offered before deletion.

Primary sources: [deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111),
[foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[Billing security](https://developer.android.com/google/play/billing/security).

## Console and infrastructure setup

| Owner/configuration action | Required evidence |
|---|---|
| Play app record, developer identity/contact and countries | Correct package, verified account and reachable support |
| App signing / upload key | Release signature fingerprint; secret configuration; recovery ownership |
| Products | Active `premium_monthly`, `premium_yearly`, `premium_lifetime` as the existing plan specifies; base plans/offers/prices reviewed |
| Licence testers | Test accounts and internal-track installs; pending/refund/restore scenarios recorded |
| Google Cloud OAuth | Android clients with production Play signing fingerprints and a Web client ID for server audience; consent screen |
| Backend | Deployed HTTPS origin, managed secret/key storage, least-privilege Play API access, persistent ledger, backup/restore and monitoring |
| Play notifications | Authenticated Pub/Sub delivery, retries and reconciliation; no trust in notification payload as the entitlement itself |
| Drive | API enabled, appDataFolder authorization and backup deletion tested |
| Privacy/deletion URLs | Public, working and accurate before review; linked in app and Console |
| App content | Data safety, content rating, ads declaration, audience, FGS declarations and reviewer instructions |

## Test and rollout gates

- Minimum supported OS, Android 10 capture, Android 13 media permission, Android
  14/15 service behavior, target Android 16 and current supported OS/device.
- At least two GPU vendors, low/mid/high memory tiers, phone and large screen,
  60/90/120 Hz, 4 KB and 16 KB runtimes; real speaker/wired/USB/Bluetooth routes.
- 30-minute playback and 20-minute graphics thermal sessions; rotation/fold,
  process death, screen lock, notifications and external controller launches.
- Subscription/lifetime purchase, pending/cancel, offline, refund, expiry,
  restore, duplicate token, account change and deletion, with release signing.
- Export every lane plus overlap/gaps, different sample rates, disk full,
  cancel, background timeout, missing URI and process loss; inspect saved media.
- Play pre-launch report, Android vitals and accessibility review. For applicable
  new personal accounts, meet the closed-test requirement in
  [Play's account-specific testing guidance](https://support.google.com/googleplay/android-developer/answer/14151465)
  (the documented standard is 12 testers continuously opted in for 14 days).
- Promote internal → closed → production only with no R0 defect. Start a staged
  rollout, monitor crashes/ANRs/purchases/export failures, halt rollout on a
  regression, and issue a higher-versionCode fix. Do not downgrade user schemas.

## Store listing draft (publish only after capability verification)

**Title:** Geode: Music & Visuals

**Short description:** Play your music, shape reactive visuals, and create music videos.

**Full description draft:**

Turn your music into a visual experience with Geode. Listen to local tracks,
explore flowing colours and immersive scenes, and make each look your own.

Browse your library and playlists, adjust the sound, and keep listening with
background playback and system controls. Choose a visual preset, change its
colour and motion, or interact directly with touch.

Build a music video in Studio using your audio, visuals, media and captions.
Preview your project, choose supported export settings, and save the finished
video to your device. Use eligible looks as a live wallpaper.

Microphone and supported device-audio modes are optional and require permission.
Some apps do not allow their audio to be captured. Available export quality
depends on your device. Premium features and prices are shown before purchase.

This draft intentionally has no unsupported “4K on every phone”, “all music apps”,
medical benefit, latency or frame-rate guarantee. Remove any sentence whose
feature has not passed the release matrix.

## Listing assets to produce from the final release candidate

512×512 icon; 1024×500 feature graphic; real phone screenshots of Listen,
signature visual, Fluid touch, Customize, Studio and premium offer; tablet images
if tablet support is advertised. Check current Console image requirements before
upload. Use licensed demo audio and original rendered artwork. Screenshots must
match the shipping UI and should not show fake purchase prices or account data.
