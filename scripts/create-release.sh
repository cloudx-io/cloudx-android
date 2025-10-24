#!/bin/bash
set -e

# CloudX SDK Release Branch Creator
#
# This script creates a new release branch from develop.
# Must be run from the develop branch.
#
# Usage: ./scripts/create-release.sh <major|minor> [--dry-run]
# Examples:
#   ./scripts/create-release.sh minor           # 0.1.0 → 0.2.0
#   ./scripts/create-release.sh major           # 0.1.0 → 1.0.0
#   ./scripts/create-release.sh minor --dry-run # Test without pushing
#
# Note: Patch releases are for hotfixes only and should be done
#       from an existing release branch, not from develop.

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Check if parameter is provided
if [ -z "$1" ]; then
    print_error "Version bump type not provided"
    echo "Usage: ./scripts/create-release.sh <major|minor> [--dry-run]"
    echo ""
    echo "Examples:"
    echo "  ./scripts/create-release.sh minor           # 0.1.0 → 0.2.0"
    echo "  ./scripts/create-release.sh major           # 0.1.0 → 1.0.0"
    echo "  ./scripts/create-release.sh minor --dry-run # Test without pushing"
    echo ""
    echo "Note: For patch releases (hotfixes), use the release branch directly."
    exit 1
fi

BUMP_TYPE=$1
DRY_RUN=false

# Check for --dry-run flag
if [ "$2" = "--dry-run" ]; then
    DRY_RUN=true
    print_info "DRY RUN MODE - No changes will be pushed"
fi

# Validate bump type
if [[ "$BUMP_TYPE" != "major" && "$BUMP_TYPE" != "minor" ]]; then
    print_error "Invalid bump type: $BUMP_TYPE"
    echo "Must be either 'major' or 'minor'"
    exit 1
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
    echo "Please commit or stash your changes before creating a release branch"
    exit 1
fi

# Get current branch
CURRENT_BRANCH=$(git branch --show-current)

# Ensure we're on develop branch
if [ "$CURRENT_BRANCH" != "develop" ]; then
    print_error "Must be on develop branch to create a release"
    echo "Current branch: $CURRENT_BRANCH"
    echo ""
    echo "To switch to develop:"
    echo "  git checkout develop"
    echo "  git pull origin develop"
    exit 1
fi

print_info "On develop branch ✓"

# Read current version from libs.versions.toml
CURRENT_VERSION=$(grep '^sdkVersionName = ' gradle/libs.versions.toml | sed 's/sdkVersionName = "\(.*\)"/\1/')

if [ -z "$CURRENT_VERSION" ]; then
    print_error "Could not read current version from gradle/libs.versions.toml"
    exit 1
fi

print_info "Current version: $CURRENT_VERSION"

# Parse current version into components
if ! [[ $CURRENT_VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    print_error "Current version format is invalid: $CURRENT_VERSION"
    exit 1
fi

MAJOR=${BASH_REMATCH[1]}
MINOR=${BASH_REMATCH[2]}
PATCH=${BASH_REMATCH[3]}

# Determine new version based on bump type
if [ "$BUMP_TYPE" = "major" ]; then
    VERSION="$((MAJOR + 1)).0.0"
    print_info "Bumping major version: $CURRENT_VERSION → $VERSION"
elif [ "$BUMP_TYPE" = "minor" ]; then
    VERSION="$MAJOR.$((MINOR + 1)).0"
    print_info "Bumping minor version: $CURRENT_VERSION → $VERSION"
fi

# Fetch latest changes
print_info "Fetching latest changes from remote..."
git fetch origin

# Pull latest develop
print_info "Pulling latest develop..."
git pull origin develop
print_success "Up to date with origin/develop"

# Check if release branch already exists
RELEASE_BRANCH="release/$VERSION"
if git show-ref --verify --quiet "refs/heads/$RELEASE_BRANCH"; then
    print_error "Release branch $RELEASE_BRANCH already exists locally"
    exit 1
fi

if git show-ref --verify --quiet "refs/remotes/origin/$RELEASE_BRANCH"; then
    print_error "Release branch $RELEASE_BRANCH already exists on remote"
    exit 1
fi

# Create release branch
print_info "Creating release branch: $RELEASE_BRANCH"
git checkout -b "$RELEASE_BRANCH"
print_success "Created branch $RELEASE_BRANCH"

# Update version in libs.versions.toml
print_info "Updating version to $VERSION in gradle/libs.versions.toml..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^sdkVersionName = \".*\"/sdkVersionName = \"$VERSION\"/" gradle/libs.versions.toml
else
    # Linux
    sed -i "s/^sdkVersionName = \".*\"/sdkVersionName = \"$VERSION\"/" gradle/libs.versions.toml
fi
print_success "Updated version in gradle/libs.versions.toml"

# Show the diff
print_info "Changes:"
git diff gradle/libs.versions.toml

# Commit the version bump
print_info "Committing version bump..."
git add gradle/libs.versions.toml
git commit -m "Bump version to $VERSION for release

Prepare release branch for version $VERSION.
This will trigger RC builds: $VERSION-rc.N+SHA

🤖 Generated with create-release.sh"
print_success "Committed version bump"

# Push the release branch (or show what would be pushed in dry-run)
if [ "$DRY_RUN" = true ]; then
    echo ""
    print_info "DRY RUN: Would push branch with:"
    echo "  git push -u origin $RELEASE_BRANCH"
    echo ""
    print_info "To clean up this dry run:"
    echo "  git checkout develop"
    echo "  git branch -D $RELEASE_BRANCH"
    echo "  git checkout gradle/libs.versions.toml"
else
    print_info "Pushing release branch to origin..."
    git push -u origin "$RELEASE_BRANCH"
    print_success "Pushed $RELEASE_BRANCH to origin"

    echo ""
    print_success "Release branch created successfully!"
    echo ""
    echo "Next steps:"
    echo "  1. CI will run tests on $RELEASE_BRANCH"
    echo "  2. After CI passes, RC build will publish: $VERSION-rc.N+SHA"
    echo "  3. Test the RC build in your apps"
    echo "  4. When ready, tag for stable release:"
    echo "     git tag v-sdk-$VERSION"
    echo "     git tag v-adapter-all-$VERSION"
    echo "     git push origin v-sdk-$VERSION v-adapter-all-$VERSION"
    echo ""
    echo "View the release branch: https://github.com/cloudx-io/cloudx-android/tree/$RELEASE_BRANCH"
fi
