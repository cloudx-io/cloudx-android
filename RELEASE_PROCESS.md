# CloudX SDK Release Process

This document describes the release process for the CloudX Android SDK and how to consume different versions.

## Overview

The SDK uses a three-stage release process:

1. **Develop** → GitHub Packages (internal testing)
2. **Release Candidate (RC)** → GitHub Packages (internal validation)
3. **Stable** → Maven Central (public release)

## Version Format

All modules (SDK + adapters) are released with synchronized versions:

- **Develop (CI)**: `X.Y.Z-dev.BUILD+SHA` (e.g., `0.1.0-dev.123+a1b2c3d`)
- **RC (CI)**: `X.Y.Z-rc.BUILD+SHA` (e.g., `0.1.0-rc.5+e4f5g6h`)
- **Stable**: `X.Y.Z` (e.g., `0.1.0`)
- **Local**: `X.Y.Z-local+SHA` (e.g., `0.1.0-local+abc1234`)

Where:
- `X.Y.Z` = Base version from `gradle/libs.versions.toml` (`sdkVersionName`)
- `BUILD` = GitHub Actions run number (incremental, only present in CI builds)
- `SHA` = Short git commit SHA (7 characters, always present)
- `local` = Automatically added when building locally without `-Pversion` flag

### Version Logic

The version is computed in the root `build.gradle.kts`:

```kotlin
// CI can override version with -Pversion=X.Y.Z-dev.42+abc123
// Local builds get -local suffix with commit SHA
val computedVersion = when {
    versionOverride != null && versionOverride != "unspecified" -> versionOverride
    else -> {
        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
        }.standardOutput.asText.get().trim()
        "$baseVersion-local+$gitSha"
    }
}
```

**Key behaviors:**
- CI workflows pass `-Pversion=X.Y.Z-dev.N+SHA` or `-Pversion=X.Y.Z-rc.N+SHA`
- Local builds without `-Pversion` automatically get `-local+SHA` format
- Manual local publishing can specify custom version: `./gradlew publish -Pversion=0.1.0-mytest+abc1234`

## Release Workflows

All release workflows follow a two-stage process:
1. **CI Workflow** runs first (builds all modules, runs all tests)
2. **Publishing Workflow** runs only after CI passes

### 0. Continuous Integration (All Branches)

**Trigger**: Every commit to any branch, and all pull requests

**Actions**:
- Builds all modules (SDK, adapters, demo app)
- Runs all unit tests
- Publishes test reports and summaries

**Workflow**: `.github/workflows/ci.yml`

**Purpose**: Quality gate for all code changes

### 1. Develop Branch (Continuous)

**Trigger**: After CI passes on `develop` branch

**Publishes to**: GitHub Packages

**Version**: `X.Y.Z-dev.BUILD+SHA`

**Use case**: Internal development and testing

**Workflow**: `.github/workflows/publish-develop.yml`

**Process**:
1. Push to `develop` → CI runs
2. CI passes → `publish-develop.yml` triggers
3. Builds release AARs and publishes to GitHub Packages

### 2. Release Candidate (As Needed)

**Trigger**: After CI passes on `release/**` branches

**Publishes to**: GitHub Packages

**Version**: `X.Y.Z-rc.BUILD+SHA`

**Use case**: Internal validation before public release

**Workflow**: `.github/workflows/publish-rc.yml`

**Process**:
1. Push to `release/X.Y.Z` → CI runs
2. CI passes → `publish-rc.yml` triggers
3. Builds release AARs and publishes to GitHub Packages

### 3. Stable Release (Main)

**Trigger**: Git tags
- SDK: `v-sdk-X.Y.Z`
- CloudX Adapter: `v-adapter-cloudx-X.Y.Z`
- Meta Adapter: `v-adapter-meta-X.Y.Z`
- All Adapters: `v-adapter-all-X.Y.Z`

**Publishes to**: Maven Central

**Version**: `X.Y.Z`

**Use case**: Public consumption

**Workflows**:
- `.github/workflows/publish-sdk.yml`
- `.github/workflows/publish-adapters.yml`

## Hotfix Process

**Situation**: A critical bug is discovered in stable release `X.Y.Z`

### Steps to Hotfix

**1. Checkout the original RC branch**
```bash
git checkout release/X.Y.Z
git pull origin release/X.Y.Z
```

**2. Bump version in gradle/libs.versions.toml**
```bash
# Edit gradle/libs.versions.toml
sdkVersionName = "X.Y.Z+1"  # e.g., "0.1.0" → "0.1.1"

git add gradle/libs.versions.toml
git commit -m "Bump version to X.Y.Z+1 for hotfix"
```

**3. Fix the bug**
```bash
# Make your fixes
git add .
git commit -m "HOTFIX: Fix critical bug in XYZ"
```

**4. Push to trigger CI and RC workflow**
```bash
git push origin release/X.Y.Z
# CI runs first, then publishes: X.Y.Z+1-rc.N+SHA to GitHub Packages
```

**5. Test the RC version in internal apps**
```kotlin
implementation("io.cloudx:sdk:X.Y.Z+1-rc.+")
```

**6. Tag for stable release when validated**
```bash
git tag v-sdk-X.Y.Z+1
git tag v-adapter-all-X.Y.Z+1
git push origin v-sdk-X.Y.Z+1 v-adapter-all-X.Y.Z+1
# Publishes: X.Y.Z+1 to Maven Central
```

**7. Merge back to main and develop**
```bash
# Merge to main
git checkout main
git merge release/X.Y.Z
git push origin main

# Merge to develop
git checkout develop
git merge release/X.Y.Z
git push origin develop
```

**Why reuse the RC branch?**
- All version history stays in one place
- Clear commit history from initial release through hotfixes
- Simpler workflow - no need to create new branches
- Easy to see the evolution: original RC → release → hotfix → next hotfix

**Example:**
```bash
# Original release
release/0.1.0: 0.1.0-rc.1 → 0.1.0-rc.2 → 0.1.0 (stable)

# Hotfix on same branch
release/0.1.0: bump to 0.1.1 → fix bug → 0.1.1-rc.5 → 0.1.1 (stable)

# Another hotfix
release/0.1.0: bump to 0.1.2 → fix bug → 0.1.2-rc.8 → 0.1.2 (stable)
```

## Consuming from GitHub Packages (Internal Apps)

### Step 1: Configure Repository

Add GitHub Packages repository to your project's `build.gradle.kts` or `settings.gradle.kts`:

```kotlin
repositories {
    mavenCentral()  // For stable releases

    // For develop/RC versions (internal only)
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/cloudx-io/cloudx-android")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### Step 2: Add Credentials

**Option A: Using gradle.properties (Recommended for local development)**

Add to `~/.gradle/gradle.properties` (NOT in the project):

```properties
gpr.user=your-github-username
gpr.token=ghp_yourPersonalAccessToken
```

To create a Personal Access Token (PAT):
1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scopes: `read:packages`
4. Copy the token and add to `gradle.properties`

**Option B: Using Environment Variables (Recommended for CI)**

```bash
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_yourPersonalAccessToken"
```

### Step 3: Add Dependencies

```kotlin
dependencies {
    // For develop builds (always latest)
    implementation("io.cloudx:sdk:0.1.0-dev.+")
    implementation("io.cloudx:adapter-cloudx:0.1.0-dev.+")
    implementation("io.cloudx:adapter-meta:0.1.0-dev.+")

    // For specific develop build (pinned)
    implementation("io.cloudx:sdk:0.1.0-dev.123+a1b2c3d")

    // For RC builds
    implementation("io.cloudx:sdk:0.1.0-rc.5+e4f5g6h")

    // For stable releases (from Maven Central)
    implementation("io.cloudx:sdk:0.1.0")
}
```

## Updating Base Version

To bump the base version (e.g., from `0.1.0` to `0.2.0`):

1. Edit `gradle/libs.versions.toml`:
   ```toml
   sdkVersionName = "0.2.0"
   ```

2. Commit and push to `develop`

3. All subsequent builds will use the new base version

## Publishing Manually (Local)

### Publish to Maven Local (Quick Local Testing)

The simplest way to test locally:

```bash
# Builds and publishes with automatic version: X.Y.Z-local+SHA
./gradlew publishToMavenLocal
```

Then in consuming apps, add `mavenLocal()` to repositories:

```kotlin
repositories {
    mavenLocal()  // Add this first to prioritize local builds
    google()
    mavenCentral()
}
```

**What version gets published?**
- Automatically uses: `X.Y.Z-local+<current-git-sha>`
- Example: `0.1.0-local+fe5b6f8`

### Publish to GitHub Packages (Internal Testing)

For sharing builds with team members:

```bash
# Set credentials
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_yourPersonalAccessToken"

# Option 1: Publish with automatic local version
./gradlew publishAllPublicationsToGitHubPackagesRepository
# Publishes: 0.1.0-local+abc1234

# Option 2: Publish with custom version
./gradlew publishAllPublicationsToGitHubPackagesRepository -Pversion=0.1.0-john-bugfix
# Publishes: 0.1.0-john-bugfix
```

**Note:** When you specify `-Pversion`, you can include or omit the `+SHA` suffix:
- With SHA: `-Pversion=0.1.0-test+abc1234`
- Without SHA: `-Pversion=0.1.0-test` (not recommended, harder to trace)

## Troubleshooting

### "Could not find io.cloudx:sdk:X.Y.Z-dev.+"

**Cause**: No develop versions published yet, or credentials not set

**Fix**:
1. Check that develop workflow has run successfully
2. Verify GitHub Packages credentials are correct
3. Ensure you have `read:packages` permission

### "401 Unauthorized" when fetching from GitHub Packages

**Cause**: Invalid or missing credentials

**Fix**:
1. Verify your Personal Access Token (PAT) is valid
2. Check token has `read:packages` scope
3. Ensure `gpr.user` and `gpr.token` are set correctly

### Version not updating in internal app

**Cause**: Gradle cache

**Fix**:
```bash
./gradlew --refresh-dependencies
```

Or clear Gradle cache:
```bash
rm -rf ~/.gradle/caches
```

## Version Comparison & Sorting

Understanding version precedence is important for backend systems and dependency resolution:

### Semantic Version Ordering

Following [semver.org](https://semver.org/) specification:

1. **Stable > Pre-release**: `0.1.0` > `0.1.0-rc.5` > `0.1.0-dev.123`
2. **Pre-release identifiers sort alphanumerically**: `0.1.0-rc.5` > `0.1.0-dev.999`
3. **Build metadata is ignored in precedence**: `0.1.0-dev.5+abc123` = `0.1.0-dev.5+xyz789`
4. **Higher build numbers are newer**: `0.1.0-dev.123` > `0.1.0-dev.122`

### Practical Examples

```
# Sorted from newest to oldest
0.2.0                    # Stable 0.2.0
0.2.0-rc.1+abc1234      # RC for upcoming 0.2.0
0.2.0-dev.50+def5678    # Develop build for 0.2.0
0.1.0                    # Stable 0.1.0
0.1.0-rc.3+ghi9012      # RC that became 0.1.0
0.1.0-local+jkl3456     # Local build based on 0.1.0
0.1.0-dev.25+mno7890    # Develop build for 0.1.0
```

### Build Source Identification

You can determine where a build came from by the identifier:

- **`-dev.N+SHA`** → CI build from `develop` branch
- **`-rc.N+SHA`** → CI build from `release/*` branch
- **`-local+SHA`** → Local build from developer machine
- **`-custom+SHA`** → Manual build with custom identifier
- **No suffix** → Stable release from Git tag

### Tracing Source Code

Every version (except stable) includes a commit SHA:

```bash
# From version 0.1.0-dev.123+fe5b6f8
git checkout fe5b6f8

# Or view on GitHub
https://github.com/cloudx-io/cloudx-android/commit/fe5b6f8
```

## Best Practices

1. **Use version ranges for develop**: `0.1.0-dev.+` to always get latest
2. **Pin RC versions**: Use exact version for validation testing
3. **Use stable for production**: Only use stable versions in production apps
4. **Check build numbers**: Higher build number = newer version within same pre-release stage
5. **Always include commit SHA**: When manually publishing, always add `+SHA` for traceability
6. **Use dependency locking**: For reproducible builds, use Gradle dependency locking
7. **Don't rely on build metadata for ordering**: Use the BUILD number, not the SHA

## Example: Internal App Setup

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        // GitHub Packages for internal builds
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cloudx-io/cloudx-android")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    // Option 1: Always use latest develop build from CI
    implementation("io.cloudx:sdk:0.1.0-dev.+")
    implementation("io.cloudx:adapter-cloudx:0.1.0-dev.+")
    implementation("io.cloudx:adapter-meta:0.1.0-dev.+")

    // Option 2: Use specific local build (for testing local changes)
    implementation("io.cloudx:sdk:0.1.0-local+abc1234")
    implementation("io.cloudx:adapter-cloudx:0.1.0-local+abc1234")
    implementation("io.cloudx:adapter-meta:0.1.0-local+abc1234")

    // Option 3: Use specific RC build (for validation)
    implementation("io.cloudx:sdk:0.1.0-rc.5+def5678")
    implementation("io.cloudx:adapter-cloudx:0.1.0-rc.5+def5678")
    implementation("io.cloudx:adapter-meta:0.1.0-rc.5+def5678")
}
```

```properties
# ~/.gradle/gradle.properties (NOT in project!)
gpr.user=your-github-username
gpr.token=ghp_yourPersonalAccessToken123
```
