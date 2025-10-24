#!/bin/bash
set -e

# CloudX SDK Hotfix Creator
#
# This script creates a hotfix by bumping the patch version on a release branch.
# Must be run from a release/* branch.
#
# Usage: ./scripts/create-hotfix.sh [--dry-run]
# Examples:
#   ./scripts/create-hotfix.sh           # 0.1.0 → 0.1.1
#   ./scripts/create-hotfix.sh --dry-run # Test without pushing

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
    echo "Please commit or stash your changes before creating a hotfix"
    exit 1
fi

# Get current branch
CURRENT_BRANCH=$(git branch --show-current)

# Ensure we're on a release branch
if [[ ! "$CURRENT_BRANCH" =~ ^release/ ]]; then
    print_error "Must be on a release/* branch to create a hotfix"
    echo "Current branch: $CURRENT_BRANCH"
    echo ""
    echo "Hotfixes (patch releases) must be done from release branches."
    echo "For new major/minor releases, use:"
    echo "  ./scripts/create-release.sh major|minor"
    exit 1
fi

print_info "On release branch: $CURRENT_BRANCH ✓"

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

# Increment patch version
NEW_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
print_info "Bumping patch version: $CURRENT_VERSION → $NEW_VERSION"

# Fetch latest changes
print_info "Fetching latest changes from remote..."
git fetch origin

# Pull latest from current release branch (if it exists on remote)
if git ls-remote --exit-code --heads origin "$CURRENT_BRANCH" >/dev/null 2>&1; then
    print_info "Pulling latest changes from $CURRENT_BRANCH..."
    git pull origin "$CURRENT_BRANCH"
    print_success "Up to date with origin/$CURRENT_BRANCH"
else
    print_info "Branch $CURRENT_BRANCH does not exist on remote yet (local only)"
fi

# Update version in libs.versions.toml
print_info "Updating version to $NEW_VERSION in gradle/libs.versions.toml..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^sdkVersionName = \".*\"/sdkVersionName = \"$NEW_VERSION\"/" gradle/libs.versions.toml
else
    # Linux
    sed -i "s/^sdkVersionName = \".*\"/sdkVersionName = \"$NEW_VERSION\"/" gradle/libs.versions.toml
fi
print_success "Updated version in gradle/libs.versions.toml"

# Show the diff
print_info "Changes:"
git diff gradle/libs.versions.toml

# Commit the version bump
print_info "Committing version bump..."
git add gradle/libs.versions.toml
git commit -m "Bump version to $NEW_VERSION for hotfix

Prepare hotfix for version $NEW_VERSION.
This will trigger RC builds: $NEW_VERSION-rc.N+SHA

🤖 Generated with create-hotfix.sh"
print_success "Committed version bump"

# Push the changes (or show what would be pushed in dry-run)
if [ "$DRY_RUN" = true ]; then
    echo ""
    print_info "DRY RUN: Would push with:"
    echo "  git push origin $CURRENT_BRANCH"
    echo ""
    print_info "To clean up this dry run:"
    echo "  git reset --hard HEAD~1"
else
    print_info "Pushing to origin/$CURRENT_BRANCH..."
    git push origin "$CURRENT_BRANCH"
    print_success "Pushed to origin/$CURRENT_BRANCH"

    echo ""
    print_success "Hotfix version bump completed successfully!"
    echo ""
    echo "Next steps:"
    echo "  1. CI will run tests on $CURRENT_BRANCH"
    echo "  2. After CI passes, RC build will publish: $NEW_VERSION-rc.N+SHA"
    echo "  3. Test the RC build in your apps"
    echo "  4. When ready, tag for stable release:"
    echo "     git tag v-sdk-$NEW_VERSION"
    echo "     git tag v-adapter-all-$NEW_VERSION"
    echo "     git push origin v-sdk-$NEW_VERSION v-adapter-all-$NEW_VERSION"
    echo ""
    echo "  5. After stable release, merge back to main and develop:"
    echo "     git checkout main && git merge $CURRENT_BRANCH && git push origin main"
    echo "     git checkout develop && git merge $CURRENT_BRANCH && git push origin develop"
fi
