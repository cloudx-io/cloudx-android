# CloudX SDK Release Process

This document describes the release process for the CloudX Android SDK and how to consume different versions.

## Overview

The SDK uses a three-stage release process:

1. **Develop** → GitHub Packages (internal testing)
2. **Release Candidate (RC)** → GitHub Packages (internal validation)
3. **Stable** → Maven Central (public release)

## Version Format

All modules (SDK + adapters) are released with synchronized versions:

- **Develop**: `X.Y.Z-dev.BUILD+SHA` (e.g., `0.0.1.42-dev.123+a1b2c3d`)
- **RC**: `X.Y.Z-rc.BUILD+SHA` (e.g., `0.0.1.42-rc.5+e4f5g6h`)
- **Stable**: `X.Y.Z` (e.g., `0.0.1.42`)

Where:
- `X.Y.Z` = Base version from `gradle.properties`
- `BUILD` = GitHub Actions run number (incremental)
- `SHA` = Short git commit SHA (7 characters)

## Release Workflows

### 1. Develop Branch (Continuous)

**Trigger**: Every commit to `develop` branch

**Publishes to**: GitHub Packages

**Version**: `X.Y.Z-dev.BUILD+SHA`

**Use case**: Internal development and testing

**Workflow**: `.github/workflows/publish-develop.yml`

### 2. Release Candidate (Weekly)

**Trigger**:
- Commits to `release/**` branches
- Tags matching `*-rc*` pattern

**Publishes to**: GitHub Packages

**Version**: `X.Y.Z-rc.BUILD+SHA`

**Use case**: Internal validation before public release

**Workflow**: `.github/workflows/publish-rc.yml`

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

**2. Bump version in gradle.properties**
```bash
# Edit gradle.properties
cloudx.version.base=X.Y.Z+1  # e.g., 0.0.1.42 → 0.0.1.43

git add gradle.properties
git commit -m "Bump version to X.Y.Z+1 for hotfix"
```

**3. Fix the bug**
```bash
# Make your fixes
git add .
git commit -m "HOTFIX: Fix critical bug in XYZ"
```

**4. Push to trigger RC workflow**
```bash
git push origin release/X.Y.Z
# Publishes: X.Y.Z+1-rc.N+SHA to GitHub Packages
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
release/0.0.1.42: 0.0.1.42-rc.1 → 0.0.1.42-rc.2 → 0.0.1.42 (stable)

# Hotfix on same branch
release/0.0.1.42: bump to 0.0.1.43 → fix bug → 0.0.1.43-rc.5 → 0.0.1.43 (stable)

# Another hotfix
release/0.0.1.42: bump to 0.0.1.44 → fix bug → 0.0.1.44-rc.8 → 0.0.1.44 (stable)
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
    implementation("io.cloudx:sdk:0.0.1.42-dev.+")
    implementation("io.cloudx:adapter-cloudx:0.0.1.42-dev.+")
    implementation("io.cloudx:adapter-meta:0.0.1.42-dev.+")

    // For specific develop build (pinned)
    implementation("io.cloudx:sdk:0.0.1.42-dev.123+a1b2c3d")

    // For RC builds
    implementation("io.cloudx:sdk:0.0.1.42-rc.5+e4f5g6h")

    // For stable releases (from Maven Central)
    implementation("io.cloudx:sdk:0.0.1.42")
}
```

## Updating Base Version

To bump the base version (e.g., from `0.0.1.42` to `0.0.2.0`):

1. Edit `gradle.properties`:
   ```properties
   cloudx.version.base=0.0.2.0
   ```

2. Commit and push to `develop`

3. All subsequent builds will use the new base version

## Publishing Manually (Local)

### Publish to GitHub Packages (Internal Testing)

```bash
# Set credentials
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_yourPersonalAccessToken"

# Publish with custom version
./gradlew publishAllPublicationsToGitHubPackagesRepository -Pversion=0.0.1.42-manual
```

### Publish to Maven Local (Quick Local Testing)

```bash
./gradlew publishToMavenLocal
```

Then in consuming apps, add `mavenLocal()` to repositories.

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

## Best Practices

1. **Use version ranges for develop**: `0.0.1.42-dev.+` to always get latest
2. **Pin RC versions**: Use exact version for validation testing
3. **Use stable for production**: Only use stable versions in production apps
4. **Check build numbers**: Higher build number = newer version
5. **Use dependency locking**: For reproducible builds, use Gradle dependency locking

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
    // Always use latest develop build
    implementation("io.cloudx:sdk:0.0.1.42-dev.+")
    implementation("io.cloudx:adapter-cloudx:0.0.1.42-dev.+")
    implementation("io.cloudx:adapter-meta:0.0.1.42-dev.+")
}
```

```properties
# ~/.gradle/gradle.properties (NOT in project!)
gpr.user=your-github-username
gpr.token=ghp_yourPersonalAccessToken123
```
