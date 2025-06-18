#!/bin/bash

# Exit on any error
set -e

# Step 1: Stage all changes
git add .

# Step 2: Ask for commit message
read -p "📝 Enter commit message: " message

# Step 2.5: Fallback message if none provided
if [[ -z "$message" ]]; then
  message="🐞 Probably a bug fix (commit message not provided)"
  echo "⚠️  No commit message entered. Using default: \"$message\""
fi

# Step 3: Commit the changes
git commit -m "$message"

# Step 4: Push the commit
git push origin HEAD

# Step 5: Get latest tag
latest_tag=$(git tag --sort=-v:refname | head -n 1)

# Step 6: Auto-increment patch version
if [[ $latest_tag =~ ^V([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  new_tag="v$major.$minor.$((patch + 1))"
else
  new_tag="v1.0.0"
fi

# Step 7: Ask to override tag
read -p "🏷️ Suggested tag is '$new_tag'. Enter custom tag or press Enter to accept: " custom_tag
tag_to_use="${custom_tag:-$new_tag}"

# Step 8: Create and push tag
git tag "$tag_to_use"
git push origin "$tag_to_use"

echo "✅ Pushed commit and tag '$tag_to_use'! GitHub Actions should start now 🚀"
