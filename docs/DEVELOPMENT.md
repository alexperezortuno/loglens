# LogLens Development Guide

## Prerequisites

- **JDK 21** (or later, configured via Gradle toolchain)
- **IntelliJ IDEA 2024.3+** (Community or Ultimate)
- **Gradle 9.5+** (wrapper included)

## Local Setup

```bash
git clone https://github.com/loglens/loglens.git
cd loglens
./gradlew buildPlugin
```

Open in IntelliJ IDEA:
- File → Open → Select `loglens` directory
- Gradle will sync automatically
- JDK 21 will be detected via toolchain

## Project Structure

```
loglens/
├── build.gradle.kts           # Gradle build script
├── gradle.properties          # Version, platform, plugin properties
├── settings.gradle.kts        # Gradle settings
├── src/
│   ├── main/
│   │   ├── kotlin/io/loglens/ # Main source
│   │   │   ├── ai/            # AI integration (optional)
│   │   │   ├── exception/     # Stack trace detection/assembly
│   │   │   ├── filter/        # Level & advanced filters
│   │   │   ├── icons/         # Plugin icons
│   │   │   ├── model/         # LogEntry, LogLevel, ThrowableInfo
│   │   │   ├── navigation/    # Stack frame navigation
│   │   │   ├── observability/ # Trace grouping, timeline, stats
│   │   │   ├── parser/        # Parser registry & implementations
│   │   │   ├── search/        # Log search (plain/regex)
│   │   │   ├── service/       # File reading, indexing, project service
│   │   │   ├── settings/      # Persistent settings
│   │   │   ├── source/        # Docker, process log sources
│   │   │   ├── ui/            # Tool window, file editor, actions
│   │   │   └── util/          # ANSI, timestamps, raw text
│   │   └── resources/
│   │       ├── META-INF/plugin.xml
│   │       └── icons/loglens.svg
│   └── test/
│       └── kotlin/io/loglens/ # Unit tests
└── docs/                      # Documentation
```

## Gradle Commands

| Command | Description |
|---------|-------------|
| `./gradlew buildPlugin` | Build plugin ZIP (`build/distributions/loglens-<version>.zip`) |
| `./gradlew test` | Run all tests |
| `./gradlew test --tests 'io.loglens.parser.ParserRegistryTest'` | Run specific test class |
| `./gradlew runIde` | Launch sandbox IDE with plugin installed |
| `./gradlew prepareSandbox` | Prepare sandbox without launching |
| `./gradlew verifyPlugin` | Run IntelliJ Plugin Verifier against configured IDEs |
| `./gradlew clean` | Clean build directory |
| `./gradlew build` | Full build (compile, test, plugin ZIP) |

## Running Tests

```bash
# All tests (147 tests)
./gradlew test

# Specific test class
./gradlew test --tests 'io.loglens.parser.ParserRegistryTest'

# Specific test method
./gradlew test --tests 'io.loglens.parser.ParserRegistryTest.registry routes JSON lines through the structured parser'

# With verbose output
./gradlew test --info
```

Test framework: **JUnit 5** with `kotlin.test` assertions.

Test categories:
- Parser tests (`*ParserTest`, `ParserRegistryTest`)
- Filter tests (`*FilterTest`)
- Search tests (`LogSearchTest`)
- Stack trace tests (`*StackTrace*Test`)
- Service tests (`FileReadingServiceTest`, `LogIndexTest`)
- End-to-end (`EndToEndPipelineTest`)

## Running in Sandbox IDE

```bash
./gradlew runIde
```

Launches IntelliJ IDEA Community 2025.1.1 with LogLens installed. Use for:
- Manual UI testing
- Debugging Swing components
- Verifying file editor integration
- Testing Docker/process actions

### Sandbox Configuration

```kotlin
// build.gradle.kts
intellijPlatform {
    sandboxContainer = layout.buildDirectory.dir("idea-sandbox")
}
```

Sandbox location: `build/idea-sandbox/`

## Building the Plugin

```bash
./gradlew buildPlugin
```

Output: `build/distributions/loglens-<version>.zip`

The ZIP contains:
- `lib/loglens.jar` — Compiled plugin classes
- `META-INF/plugin.xml` — Plugin descriptor (patched with version)
- `icons/loglens.svg` — Plugin icon

## Plugin Verification

```bash
./gradlew verifyPlugin
```

Runs IntelliJ Plugin Verifier against:
- IC-2024.3 (since-build 243)
- IC-2025.1
- IC-2025.2

Checks:
- Binary compatibility
- Deprecated API usage
- Internal API usage
- Override-only API usage

## Code Conventions

### Kotlin Style

- Follows [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- `kotlin.code.style=official` in `gradle.properties`
- 4-space indentation, no tabs
- Max line length: 160 chars
- Trailing commas in multi-line declarations

### Architecture Rules

1. **Parser Independence** — Parsers, filters, search, services must not depend on Swing/UI
2. **Parser Registry** — Add new parsers to `ParserRegistry.defaults()` in specificity order, `PlainTextLogParser` last
3. **Offline-First** — No required network calls; AI is optional and disabled by default
4. **Bounded Resources** — File reading, caching, retention all have hard limits
5. **Background I/O** — File reading and parsing off EDT (Swing thread)

### Testing Guidelines

- Parser behavior covered by unit tests
- Every parser: valid input, malformed input, missing fields, unknown levels, multiline
- Stack-trace parsing has dedicated tests
- UI tests only where meaningful value

## Debugging

### Remote Debug Sandbox

```bash
./gradlew runIde --debug-jvm
```

Connect debugger to `localhost:5005`.

### Logging

```kotlin
// In code
import com.intellij.openapi.diagnostic.Logger
private val LOG = Logger.getInstance(MyClass::class.java)
LOG.info("Message")
```

View in sandbox: **Help → Show Log in Files**

### Inspecting LogEntry

```kotlin
// In tests or debug
val entry = registry.parse(line)
println(entry.raw)
println(entry.metadata)
```

## Adding a New Parser

1. Create `MyLogParser.kt` implementing `LogParser`
2. Add tests in `MyLogParserTest.kt`
3. Register in `ParserRegistry.defaults()` before `PlainTextLogParser`
4. Run `./gradlew test` to verify

## Adding a New UI Action

1. Create action class extending `AnAction`
2. Register in `plugin.xml` under `<actions>`
3. Add icon if needed
4. Test with `./gradlew runIde`

## Updating IntelliJ Platform Version

Edit `gradle.properties`:
```properties
platformVersion=2025.2.1
pluginSinceBuild=243
pluginUntilBuild=253.*
```

Then:
```bash
./gradlew clean buildPlugin verifyPlugin
```

## Publishing to JetBrains Marketplace

### Prerequisites

1. JetBrains Marketplace account
2. Plugin signing certificate (optional but recommended)
3. Repository secrets configured:
   - `PUBLISH_TOKEN` — Marketplace API token
   - `SIGNING_KEY` — Base64-encoded keystore (if signing)
   - `SIGNING_PASSWORD` — Keystore password
   - `SIGNING_KEY_ALIAS` — Key alias
   - `SIGNING_KEY_PASSWORD` — Key password

### Release Workflow

Triggered by pushing a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release workflow:
1. Verifies version consistency
2. Runs all tests
3. Builds plugin ZIP
4. Generates SHA-256 checksum
5. Creates GitHub Release with artifacts
6. Publishes to JetBrains Marketplace (requires `release` environment approval)

### Manual Publishing

```bash
./gradlew buildPlugin signPlugin publishPlugin
```

Requires `signing.properties` or environment variables.

## Common Issues

| Issue | Solution |
|-------|----------|
| Gradle daemon incompatible | `./gradlew --stop` then retry |
| JDK not found | Ensure JDK 21 installed; check `gradle.properties` toolchain |
| Plugin Verifier fails | Check network access to `download.jetbrains.com` |
| Tests hang | Increase heap: `org.gradle.jvmargs=-Xmx4g` |
| Sandbox won't start | Delete `build/idea-sandbox/` and retry |

## Useful Links

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Gradle IntelliJ Platform Plugin](https://github.com/JetBrains/gradle-intellij-plugin)
- [Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)