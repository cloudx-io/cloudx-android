#!/bin/bash
set -e

# CloudX SDK Release Promoter
#
# This script promotes a release branch to main for public release.
# Must be run from a release/* branch.
#
# Usage: ./scripts/promote-release.sh [--dry-run]
# Examples:
#   ./scripts/promote-release.sh           # Promote to main and create tags
#   ./scripts/promote-release.sh --dry-run # Test without pushing

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_error() {
    echo -e "${RED}ERROR: $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}➜ $1${NC}"
}

print_step() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

DRY_RUN=false

# Check for --dry-run flag
if [ "$1" = "--dry-run" ]; then
    DRY_RUN=true
    print_info "DRY RUN MODE - No changes will be pushed"
fi

# Check if we're in the repo root
if [ ! -f "gradle/libs.versions.toml" ]; then
    print_error "gradle/libs.versions.toml not found"
    echo "Please run this script from the repository root"
    exit 1
fi

# Check if we're on a clean working tree
if ! git diff-index --quiet HEAD --; then
    print_error "Working tree is not clean"
    echo "Please commit or stash your changes before promoting to main"
    exit 1
fi

# Get current branch
CURRENT_BRANCH=$(git branch --show-current)

# Ensure we're on a release branch
if [[ ! "$CURRENT_BRANCH" =~ ^release/ ]]; then
    print_error "Must be on a release/* branch to promote to main"
    echo "Current branch: $CURRENT_BRANCH"
    echo ""
    echo "To promote a release, first checkout the release branch:"
    echo "  git checkout release/X.Y.Z"
    exit 1
fi

print_info "On release branch: $CURRENT_BRANCH ✓"

# Read version from libs.versions.toml
VERSION=$(grep '^sdkVersionName = ' gradle/libs.versions.toml | sed 's/sdkVersionName = "\(.*\)"/\1/')

if [ -z "$VERSION" ]; then
    print_error "Could not read version from gradle/libs.versions.toml"
    exit 1
fi

print_info "Release version: $VERSION"

# Validate version format
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    print_error "Version format is invalid: $VERSION"
    echo "Expected X.Y.Z format"
    exit 1
fi

# Verify the last commit is a version bump
LAST_COMMIT_MSG=$(git log -1 --pretty=%B)
if [[ ! "$LAST_COMMIT_MSG" =~ ^"Bump version to $VERSION" ]]; then
    print_error "Last commit is not a version bump for $VERSION"
    echo ""
    echo "Last commit message:"
    echo "  $LAST_COMMIT_MSG"
    echo ""
    echo "Expected commit message to start with:"
    echo "  Bump version to $VERSION for release"
    echo "  OR"
    echo "  Bump version to $VERSION for hotfix"
    echo ""
    echo "Please ensure you've run the version bump script before promoting:"
    echo "  For new releases: ./scripts/create-release.sh major|minor"
    echo "  For hotfixes: ./scripts/create-hotfix.sh"
    exit 1
fi

print_success "Last commit is a version bump for $VERSION ✓"

echo ""
print_step "Step 1: Fetch latest changes"
git fetch origin

# Check version on main to ensure we're promoting a newer version
print_info "Checking version on main..."
git checkout main -q
MAIN_VERSION=$(grep '^sdkVersionName = ' gradle/libs.versions.toml | sed 's/sdkVersionName = "\(.*\)"/\1/')
git checkout "$CURRENT_BRANCH" -q

if [ -n "$MAIN_VERSION" ]; then
    print_info "Version on main: $MAIN_VERSION"

    # Parse versions for comparison
    if [[ $MAIN_VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        MAIN_MAJOR=${BASH_REMATCH[1]}
        MAIN_MINOR=${BASH_REMATCH[2]}
        MAIN_PATCH=${BASH_REMATCH[3]}
    else
        print_error "Could not parse version on main: $MAIN_VERSION"
        exit 1
    fi

    if [[ $VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        REL_MAJOR=${BASH_REMATCH[1]}
        REL_MINOR=${BASH_REMATCH[2]}
        REL_PATCH=${BASH_REMATCH[3]}
    else
        print_error "Could not parse release version: $VERSION"
        exit 1
    fi

    # Compare versions: major.minor.patch
    VERSION_IS_NEWER=false

    if [ "$REL_MAJOR" -gt "$MAIN_MAJOR" ]; then
        VERSION_IS_NEWER=true
    elif [ "$REL_MAJOR" -eq "$MAIN_MAJOR" ]; then
        if [ "$REL_MINOR" -gt "$MAIN_MINOR" ]; then
            VERSION_IS_NEWER=true
        elif [ "$REL_MINOR" -eq "$MAIN_MINOR" ]; then
            if [ "$REL_PATCH" -gt "$MAIN_PATCH" ]; then
                VERSION_IS_NEWER=true
            fi
        fi
    fi

    if [ "$VERSION_IS_NEWER" = false ]; then
        print_error "Release version $VERSION is not newer than main version $MAIN_VERSION"
        echo ""
        echo "Cannot promote a release with the same or older version."
        echo ""
        echo "Main version: $MAIN_VERSION"
        echo "Release version: $VERSION"
        echo ""
        echo "If you need to create a new release, bump the version first:"
        echo "  ./scripts/create-hotfix.sh"
        exit 1
    fi

    print_success "Release version $VERSION is newer than main version $MAIN_VERSION ✓"
else
    print_info "No version found on main (first release)"
fi

echo ""
print_step "Step 2: Checkout and update main branch"
git checkout main
git pull origin main
print_success "Main branch is up to date"

echo ""
print_step "Step 3: Squash merge release branch to main"
print_info "Squash merging $CURRENT_BRANCH into main..."
git merge "$CURRENT_BRANCH" --squash
git commit -m "Release $VERSION

Squash merge $CURRENT_BRANCH to main for stable release.

This release will be tagged as v$VERSION and published to Maven Central.

Modules published:
- io.cloudx:sdk:$VERSION
- io.cloudx:adapter-cloudx:$VERSION
- io.cloudx:adapter-meta:$VERSION

🤖 Generated with promote-release.sh"
print_success "Squash merged $CURRENT_BRANCH to main"

echo ""
print_step "Step 4: Create release tag"
RELEASE_TAG="v$VERSION"

print_info "Creating tag: $RELEASE_TAG"
git tag -a "$RELEASE_TAG" -m "Release $VERSION

Release $VERSION of CloudX Android SDK and adapters.

This tag triggers publishing SDK + all adapters to Maven Central.

Modules published:
- io.cloudx:sdk:$VERSION
- io.cloudx:adapter-cloudx:$VERSION
- io.cloudx:adapter-meta:$VERSION

🤖 Generated with promote-release.sh"

print_success "Created tag: $RELEASE_TAG"

echo ""
print_step "Step 5: Push to main and tags"

if [ "$DRY_RUN" = true ]; then
    echo ""
    print_info "DRY RUN: Would execute:"
    echo "  git push origin main"
    echo "  git push origin $RELEASE_TAG"
    echo ""
    print_info "To clean up this dry run:"
    echo "  git checkout $CURRENT_BRANCH"
    echo "  git checkout main"
    echo "  git reset --hard origin/main"
    echo "  git tag -d $RELEASE_TAG"
    echo "  git checkout $CURRENT_BRANCH"
else
    print_info "Pushing main branch..."
    git push origin main
    print_success "Pushed main branch"

    print_info "Pushing tag..."
    git push origin "$RELEASE_TAG"
    print_success "Pushed tag: $RELEASE_TAG"

    echo ""
    print_success "Release $VERSION promoted to main successfully!"
    echo ""
    echo "📦 Stable release publishing will trigger automatically"
    echo "   Tag: $RELEASE_TAG"
    echo "   Workflow: publish-release.yml"
    echo ""
    echo "Next steps:"
    echo "  1. Wait for GitHub Actions to publish SDK + adapters to Maven Central"
    echo "  2. Verify packages appear on Maven Central (may take ~30 min):"
    echo "     - https://central.sonatype.com/artifact/io.cloudx/sdk/$VERSION"
    echo "     - https://central.sonatype.com/artifact/io.cloudx/adapter-cloudx/$VERSION"
    echo "     - https://central.sonatype.com/artifact/io.cloudx/adapter-meta/$VERSION"
    echo ""
    echo "  3. Merge release branch back to develop:"
    echo "     git checkout develop"
    echo "     git merge $CURRENT_BRANCH"
    echo "     git push origin develop"
    echo ""
    echo "View release workflow:"
    echo "  https://github.com/cloudx-io/cloudx-android/actions/workflows/publish-release.yml"
    echo ""
    echo "View tag:"
    echo "  https://github.com/cloudx-io/cloudx-android/releases/tag/$RELEASE_TAG"

    # Switch back to release branch
    git checkout "$CURRENT_BRANCH"
    print_info "Switched back to $CURRENT_BRANCH"
fi
