#!/usr/bin/env bash
# Pull latest main, build Flake with a JDK 25 toolchain, copy the mod jar
# into a Prism Launcher instance's mods folder, and optionally upload it to
# a GitHub release asset.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Optional private env file; keep it out of the repo and chmod 600.
ENV_FILE="${FLAKE_ENV_FILE:-$HOME/.config/flake/env}"
if [[ -f "$ENV_FILE" ]]; then
	set -a
	# shellcheck disable=SC1090
	. "$ENV_FILE"
	set +a
fi

INSTANCE="${INSTANCE:-26.3}"
MODS_DIR="${MODS_DIR:-$HOME/.local/share/PrismLauncher/instances/$INSTANCE/minecraft/mods}"
PULL=1
PUBLISH=auto
RELEASE_TAG="${RELEASE_TAG:-}"
TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

usage() {
	cat <<'EOF'
Usage: tools/deploy.sh [options]

Pulls main (fast-forward, autostash), builds with Gradle, copies the
resulting flake-*.jar into the Prism Launcher mods folder, and uploads it
as a GitHub release asset when a token is available.

Options:
  --no-pull             Skip git pull (build the current checkout).
  --instance=NAME       Prism instance name (default: 26.3).
  --mods-dir=PATH       Explicit mods folder; overrides --instance.
  --no-publish          Skip the GitHub release upload.
  --publish             Require the GitHub upload (fail without a token).
  --release-tag=TAG     Release tag (default: v<version> from gradle.properties).
  -h, --help            Show this help.

Environment:
  FLAKE_JAVA_HOME       Preferred JDK 25+ home.
  JAVA_HOME             Fallback JDK home.
  MODS_DIR              Explicit mods folder.
  INSTANCE              Prism instance name.
  GITHUB_TOKEN/GH_TOKEN Token used to create/update the release asset.
  GITHUB_REPOSITORY     owner/repo override (default: parsed from origin).
  RELEASE_TAG           Release tag override.
  FLAKE_ENV_FILE        Env file to source (default: ~/.config/flake/env).
EOF
}

for arg in "$@"; do
	case "$arg" in
		--no-pull) PULL=0 ;;
		--instance=*) INSTANCE="${arg#*=}"; MODS_DIR="${MODS_DIR:-$HOME/.local/share/PrismLauncher/instances/$INSTANCE/minecraft/mods}" ;;
		--mods-dir=*) MODS_DIR="${arg#*=}" ;;
		--no-publish) PUBLISH=no ;;
		--publish) PUBLISH=yes ;;
		--release-tag=*) RELEASE_TAG="${arg#*=}" ;;
		-h|--help) usage; exit 0 ;;
		*) echo "Unknown option: $arg" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ "$PUBLISH" == "yes" && -z "$TOKEN" ]]; then
	echo "Error: --publish requires GITHUB_TOKEN or GH_TOKEN." >&2
	exit 1
fi

find_jdk() {
	local candidate major
	for candidate in \
		"${FLAKE_JAVA_HOME:-}" \
		"${JAVA_HOME:-}" \
		"$HOME/.local/share/jdks"/jdk-25* \
		"$HOME/.sdkman/candidates/java"/25* \
		/usr/lib/jvm/java-25*; do
		[[ -n "$candidate" && -x "$candidate/bin/java" ]] || continue
		major="$("$candidate/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"
		if [[ "${major:-0}" -ge 25 ]]; then
			printf '%s\n' "$candidate"
			return 0
		fi
	done
	return 1
}

detect_repo() {
	case "$1" in
		git@github.com:*) printf '%s\n' "${1#git@github.com:}" | sed 's/\.git$//' ;;
		ssh://git@github.com/*) printf '%s\n' "${1#ssh://git@github.com/}" | sed 's/\.git$//' ;;
		https://github.com/*) printf '%s\n' "${1#https://github.com/}" | sed 's/\.git$//' ;;
		*) return 1 ;;
	esac
}

github_message() {
	printf '%s' "$1" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("message", "unknown GitHub error"))' 2>/dev/null \
		|| printf '%s' "$1"
}

api_error() {
	echo "GitHub API error ($1): $(github_message "$2")" >&2
}

publish_release() {
	local token="$1" repo="$2" tag="$3" jar="$4"
	local version name asset_id="" release_id="" commit payload response="" status="" body=""
	local api="https://api.github.com/repos/$repo"
	local auth=(-H "Authorization: Bearer $token" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")
	version="$(sed -n 's/^version=//p' "$ROOT/gradle.properties" | head -n1)"
	name="$(basename "$jar")"
	commit="$(git -C "$ROOT" rev-parse --verify origin/main 2>/dev/null || git -C "$ROOT" rev-parse HEAD)"

	response="$(curl -sS -w $'\n%{http_code}' "${auth[@]}" "$api/releases/tags/$tag" || true)"
	status="${response##*$'\n'}"; body="${response%$'\n'*}"
	if [[ "$status" == "200" ]]; then
		release_id="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id", ""))')"
	elif [[ "$status" != "404" ]]; then
		api_error "${status:-network}" "$body"
		return 1
	fi

	if [[ -z "$release_id" ]]; then
		echo "Creating GitHub release $tag..."
		payload="$(python3 - "$tag" "Flake ${version:-unknown}" "$commit" <<'PY'
import json, sys
tag, name, commit = sys.argv[1:4]
print(json.dumps({
    "tag_name": tag,
    "name": name,
    "target_commitish": commit,
    "generate_release_notes": True,
}))
PY
)"
		response="$(curl -sS -w $'\n%{http_code}' -X POST "${auth[@]}" -H "Content-Type: application/json" \
			"$api/releases" -d "$payload" || true)"
		status="${response##*$'\n'}"; body="${response%$'\n'*}"
		if [[ "$status" != 2* ]]; then
			api_error "${status:-network}" "$body"
			echo "Hint: the token needs Contents: Read and write for $repo." >&2
			echo "      Fine-grained tokens must use 'Only select repositories' (not 'Public repositories (read-only)')." >&2
			return 1
		fi
		release_id="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
	fi

	response="$(curl -sS -w $'\n%{http_code}' "${auth[@]}" "$api/releases/$release_id/assets" || true)"
	status="${response##*$'\n'}"; body="${response%$'\n'*}"
	if [[ "$status" != "200" ]]; then
		api_error "${status:-network}" "$body"
		return 1
	fi
	asset_id="$(printf '%s' "$body" | python3 -c 'import json,sys; name=sys.argv[1]; print(next((a["id"] for a in json.load(sys.stdin) if a["name"] == name), ""))' "$name")"
	if [[ -n "$asset_id" ]]; then
		echo "Replacing existing release asset $name..."
		response="$(curl -sS -w $'\n%{http_code}' -X DELETE "${auth[@]}" "$api/releases/assets/$asset_id" || true)"
		status="${response##*$'\n'}"; body="${response%$'\n'*}"
		if [[ "$status" != "204" ]]; then
			api_error "${status:-network}" "$body"
			return 1
		fi
	fi

	echo "Uploading $name to release $tag..."
	response="$(curl -sS -w $'\n%{http_code}' -X POST "${auth[@]}" -H "Content-Type: application/java-archive" \
		--data-binary "@$jar" \
		"https://uploads.github.com/repos/$repo/releases/$release_id/assets?name=$name" || true)"
	status="${response##*$'\n'}"; body="${response%$'\n'*}"
	if [[ "$status" != 2* ]]; then
		api_error "${status:-network}" "$body"
		return 1
	fi
}

if [[ "$PULL" -eq 1 ]]; then
	echo "[1/4] Pulling origin/main..."
	git -C "$ROOT" pull --ff-only --autostash origin main
else
	echo "[1/4] Skipping git pull."
fi

JDK="$(find_jdk || true)"
if [[ -z "$JDK" ]]; then
	echo "Error: JDK 25+ not found. Set FLAKE_JAVA_HOME to a JDK 25 home." >&2
	exit 1
fi

echo "[2/4] Building with $JDK..."
JAVA_HOME="$JDK" "$ROOT/gradlew" -p "$ROOT" build --console=plain

JAR="$(ls -t "$ROOT"/build/libs/*.jar 2>/dev/null | grep -v -- '-sources\.jar$' | head -n1 || true)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
	echo "Error: no built mod jar found in $ROOT/build/libs." >&2
	exit 1
fi

echo "[3/4] Copying $(basename "$JAR") to $MODS_DIR"
mkdir -p "$MODS_DIR"
DEST="$MODS_DIR/$(basename "$JAR")"
TMP_DEST="$DEST.tmp.$$"
cp "$JAR" "$TMP_DEST"
mv -f "$TMP_DEST" "$DEST"
echo "Done: $DEST"
sha256sum "$JAR" "$DEST"

if [[ "$PUBLISH" == "no" ]]; then
	echo "[4/4] Skipping GitHub release upload."
	exit 0
fi

if [[ -z "$TOKEN" ]]; then
	echo "[4/4] Skipping GitHub release upload: GITHUB_TOKEN/GH_TOKEN is not set."
	exit 0
fi

REPO="${GITHUB_REPOSITORY:-$(detect_repo "$(git -C "$ROOT" remote get-url origin)" || true)}"
if [[ -z "$REPO" ]]; then
	echo "Error: cannot detect the GitHub repository. Set GITHUB_REPOSITORY=owner/repo." >&2
	exit 1
fi

VERSION="$(sed -n 's/^version=//p' "$ROOT/gradle.properties" | head -n1)"
TAG="${RELEASE_TAG:-v${VERSION:-unknown}}"

echo "[4/4] Publishing $(basename "$JAR") to GitHub release $TAG..."
publish_release "$TOKEN" "$REPO" "$TAG" "$JAR"
echo "Release: https://github.com/$REPO/releases/tag/$TAG"
