#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

version="${VERSION:?JitPack must supply VERSION (a release tag)}"
python3 scripts/jitpack.py version "$version"
commit="$(git rev-parse HEAD)"
archive_dir="$(mktemp -d)"
trap 'rm -rf "$archive_dir"' EXIT
release_url="https://github.com/SparrowAndSnow/mosaic/releases/download/$version"

# Wait until the release workflow has completed before requesting this tag from JitPack.
# Branch snapshots and commit-only builds cannot supply the Apple C interop artifacts.
for asset in mosaic-maven.tar.gz mosaic-maven.tar.gz.sha256; do
  if ! curl --fail --location --retry 3 --connect-timeout 15 --max-time 180 \
    "$release_url/$asset" --output "$archive_dir/$asset"; then
    echo "Missing release asset: finish the GitHub release workflow for $version before requesting JitPack." >&2
    exit 1
  fi
done
python3 scripts/jitpack.py prepare "$archive_dir/mosaic-maven.tar.gz" "$version" "$commit" "$PWD" "$HOME/.m2/repository"
