#!/usr/bin/env pwsh
#
# Exit on any error
$ErrorActionPreference = "Stop"

# Step 1: Stage all changes
git add .

# Step 2: Ask for commit message
$message = Read-Host -Prompt "📝 Enter commit message"

# Step 3: Commit the changes
git commit -m "$message"

# Step 4: Get latest tag
$latest_tag = git tag --sort=-v:refname | Select-Object -First 1

# Step 5: Auto-increment patch version
$new_tag = ""
if ($latest_tag -match '^v(\d+)\.(\d+)\.(\d+)$') {
    $major = $matches[1]
    $minor = $matches[2]
    $patch = [int]$matches[3] + 1
    $new_tag = "v$major.$minor.$patch"
} else {
    $new_tag = "v1.0.0"
}

# Step 6: Ask to override tag
$custom_tag = Read-Host -Prompt "🏷️  Suggested tag is '$new_tag'. Enter custom tag or press Enter to accept"

$tag_to_use = if ([string]::IsNullOrEmpty($custom_tag)) { $new_tag } else { $custom_tag }

# Step 7: Create and push tag
git tag "$tag_to_use"
git push origin --tags

Write-Host "✅ Pushed commit and tag '$tag_to_use'! GitHub Actions should start now 🚀" 