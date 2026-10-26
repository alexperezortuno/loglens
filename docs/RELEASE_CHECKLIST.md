# LogLens 1.0.0 — Release Checklist

## Verification Results

| Check | Status | Notes |
|-------|--------|-------|
| Unit tests (clean build) | ✅ PASS | 119 tests passing on `feature/r-1.0` branch |
| Plugin Verifier (IC-243, IC-251, IC-252) | ✅ PASS | 4 deprecated API usages (non-blocking) |
| Plugin Verifier (IC-253) | ⚠️ PENDING | IntelliJ 2025.3 not yet released |
| Plugin loads in sandbox | ✅ PASS | `Loaded custom plugins: LogLens (1.0.0)` |
| No LogLens exceptions in IDE logs | ✅ PASS | Clean sandbox logs |
| ZIP integrity | ✅ PASS | Valid ZIP, 3.3 MB |
| SHA-256 checksum | ✅ PASS | `83138ffcd5dc156a126e211b40201790119eee39cfeb1a6ca920f572213736cb` |
| No embedded credentials | ✅ PASS | Verified JAR contents |
| Plugin metadata complete | ✅ PASS | ID, version, since/until build, deps |

---

## Supported IDE Versions

| IDE Edition | Since Build | Until Build | Verified |
|-------------|-------------|-------------|----------|
| IntelliJ IDEA Community | 243 (2024.3) | 253.* (2025.3) | 243, 251, 252 ✅ |
| IntelliJ IDEA Ultimate | 243 (2024.3) | 253.* (2025.3) | Not tested (depends on Community APIs only) |
| Other IC-based IDEs | 243 | 253.* | Implicit via Community verification |

**Note**: `until-build="253.*"` declares forward compatibility with IntelliJ 2025.3. Verification against 253 will be possible once JetBrains publishes 2025.3 EAP/RC builds. The plugin uses only stable/platform APIs available since 2024.3.

---

## Test Reconciliation

| Branch / Context | Test Files | Test Cases | Notes |
|------------------|------------|------------|-------|
| `feature/r-1.0` (current HEAD) | 27 | 119 | Includes `AiProviderTest` (6 tests) |
| `develop` | 26 | 113 | Missing `AiProviderTest` |
| `master` | 25 | 107 | Older baseline |
| Plugin XML change-notes | — | 147 | **Inaccurate** — no commit/branch in history has 147 tests |

**Finding**: The "147 tests passing" claim in `plugin.xml` change-notes does not match any historical test count in this repository. The actual progression has been 107 → 113 → 119 tests. No tests were removed; counts only increased. The 147 figure appears to be a stale/incorrect documentation artifact.

**Action**: Update change-notes to reflect actual count (119) before publication.

---

## Manual Smoke Tests (Sandbox)

| Feature | Automated | Manual | Result |
|---------|-----------|--------|--------|
| Plugin loads | ✅ (sandbox log) | — | PASS |
| LogLens Tool Window opens | — | ⬜ | PENDING |
| Open local `.log` / `.out` / `.txt` | — | ⬜ | PENDING |
| Level filtering (ERROR/WARN/INFO/DEBUG) | — | ⬜ | PENDING |
| Full-text search | — | ⬜ | PENDING |
| Regex search | — | ⬜ | PENDING |
| Tail mode (pause/resume/auto-scroll) | — | ⬜ | PENDING |
| Java stack-trace navigation (clickable frames) | — | ⬜ | PENDING |
| Docker / Docker Compose log sources | — | ⬜ | PENDING |
| Local process log capture | — | ⬜ | PENDING |
| Settings persistence | — | ⬜ | PENDING |

**Manual verification required** before GO decision. Run `./gradlew runIde` and exercise the above scenarios.

---

## Release Artifact Details

| Property | Value |
|----------|-------|
| Plugin ID | `io.loglens.plugin` |
| Version | `1.0.0` |
| Artifact | `build/distributions/loglens-1.0.0.zip` |
| Size | 3,304,027 bytes (3.3 MB) |
| SHA-256 | `83138ffcd5dc156a126e211b40201790119eee39cfeb1a6ca920f572213736cb` |
| License | Apache-2.0 (declared in `plugin.xml` via vendor/description) |
| Signing | Unsigned (local build) — requires signing for Marketplace |

---

## Known Limitations

1. **IntelliJ 2025.3 verification pending** — `until-build="253.*"` is declared but not yet verified.
2. **Deprecated API usages** (4 total):
   - `FilenameIndex.getVirtualFilesByName` in `StackFrameNavigator`
   - `Messages.showChooseDialog` in `StackFrameNavigator`
   - `FileChooserDescriptorFactory.createSingleFileDescriptor` in `OpenDockerComposeLogAction` and `OpenLogFileAction`
   - Non-blocking; migration tracked in backlog.
3. **Unsigned plugin** — Marketplace publication requires a valid certificate.
4. **AI features require local Ollama/OpenAI-compatible endpoint** — not functional out of the box without user configuration.
5. **No Windows/macOS native sandbox test** — verified on Linux only.

---

## Outstanding Issues

- [ ] Update `plugin.xml` change-notes: replace "147 tests passing" with "119 tests passing"
- [ ] Complete manual smoke tests (table above)
- [ ] Obtain signing certificate for Marketplace publication
- [ ] Verify on IntelliJ 2025.3 EAP when available
- [ ] Test on Windows and macOS sandboxes

---

## GO / NO-GO Recommendation

**CONDITIONAL GO** — pending completion of manual smoke tests and change-notes correction.

**Rationale**:
- All automated checks pass (tests, verification, artifact integrity)
- Plugin loads and initializes cleanly in sandbox
- Compatibility matrix covers current stable IntelliJ versions (2024.3–2025.2)
- Known issues are documented and non-blocking for release

**Blockers for unconditional GO**:
1. Manual smoke test validation (UI/UX features)
2. Change-notes accuracy fix
3. Signing certificate for Marketplace

---

*Generated: 2026-10-26*  
*Commit: de462e3 (feature/r-1.0)*  
*Gradle: 9.5.1 | JDK: 21 | Kotlin: 2.1.20 | IntelliJ Platform: 2025.1.1*