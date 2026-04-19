#!/usr/bin/env bash
# test-panic.sh — End-to-end test of all PictureServer panic mode triggers.
#
# For each trigger type the script:
#   1. Starts the server
#   2. Simulates the attack with curl
#   3. Verifies the server process exited (panic triggered)
#
# Credentials and port are read from settings.properties automatically.
# Run from the project root or from any directory — the script always operates
# in the directory where it lives.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_LOG="$SCRIPT_DIR/test-panic-server.log"
SERVER_PID=""
BASE_URL=""
PORT=""
USERNAME=""
PASSWORD=""
JAR=""
PASS_COUNT=0
FAIL_COUNT=0
RESULTS=()

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

build_server() {
  echo "Building fat JAR ..."
  (cd "$SCRIPT_DIR" && ./gradlew fatJar -q)
  JAR=$(ls "$SCRIPT_DIR/build/libs/"*-all.jar 2>/dev/null | head -1)
  if [[ -z "$JAR" ]]; then
    echo "ERROR: No fat JAR found in build/libs/ after build" >&2
    exit 1
  fi
  echo "JAR: $JAR"
}

load_settings() {
  local file="$SCRIPT_DIR/settings.properties"
  if [[ ! -f "$file" ]]; then
    echo "ERROR: settings.properties not found in $SCRIPT_DIR" >&2
    exit 1
  fi
  # Parse "key = value" or "key=value"; trim surrounding whitespace from value.
  PORT=$(grep -E '^port\s*=' "$file" | head -1 | sed 's/^[^=]*=[[:space:]]*//' | sed 's/[[:space:]]*$//')
  USERNAME=$(grep -E '^username\s*=' "$file" | head -1 | sed 's/^[^=]*=[[:space:]]*//' | sed 's/[[:space:]]*$//')
  PASSWORD=$(grep -E '^password\s*=' "$file" | head -1 | sed 's/^[^=]*=[[:space:]]*//' | sed 's/[[:space:]]*$//')
  BASE_URL="http://localhost:${PORT}"
  echo "Settings: port=$PORT, username=$USERNAME, base_url=$BASE_URL"
}

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -f "$SERVER_LOG"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Server lifecycle
# ---------------------------------------------------------------------------

start_server() {
  : > "$SERVER_LOG"
  # -Duser.dir sets the working directory the server uses to find settings.properties.
  java -Duser.dir="$SCRIPT_DIR" -jar "$JAR" > "$SERVER_LOG" 2>&1 &
  SERVER_PID=$!
  echo "  Starting server (PID $SERVER_PID) ..."
  local max_wait=15
  for ((i = 1; i <= max_wait; i++)); do
    sleep 1
    if grep -q "Picture server listening on" "$SERVER_LOG" 2>/dev/null; then
      echo "  Server is ready."
      return 0
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "ERROR: Server process exited before becoming ready:" >&2
      cat "$SERVER_LOG" >&2
      exit 1
    fi
  done
  echo "ERROR: Server did not become ready within ${max_wait}s:" >&2
  cat "$SERVER_LOG" >&2
  kill "$SERVER_PID" 2>/dev/null || true
  exit 1
}

wait_for_server_down() {
  local max_wait=10
  printf "  Waiting up to %ds for the server process to exit ...\n" "$max_wait"
  for ((i = 1; i <= max_wait; i++)); do
    sleep 1
    if ! kill -0 "${SERVER_PID:-}" 2>/dev/null; then
      printf "  Server exited after %ds.\n" "$i"
      SERVER_PID=""
      return 0
    fi
    printf "  %2ds elapsed — still running ...\n" "$i"
  done
  printf "  Server still running after %ds.\n" "$max_wait"
  return 1
}

# ---------------------------------------------------------------------------
# Login helper (for triggers that need an authenticated session)
# ---------------------------------------------------------------------------

login() {
  local cookie_jar="$1"
  echo "  Logging in as '${USERNAME}' ..."
  local http_code exit_code=0
  http_code=$(curl -s --max-time 5 \
    -X POST "$BASE_URL/login" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}" \
    --data-urlencode "next=/" \
    -c "$cookie_jar" -b "$cookie_jar" \
    -L -o /dev/null -w "%{http_code}") || exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    echo "ERROR: curl failed (exit $exit_code) during login" >&2
    exit 1
  fi
  if [[ "$http_code" == "401" ]]; then
    echo "ERROR: Login failed (HTTP 401) — credentials in settings.properties are wrong?" >&2
    exit 1
  fi
  echo "  Logged in (HTTP $http_code)."
}

# ---------------------------------------------------------------------------
# Attack implementations
# ---------------------------------------------------------------------------

attack_known_attack_probe() {
  echo "  Requesting '/.env' (a well-known attack path) ..."
  local exit_code=0
  curl -s --max-time 5 -o /dev/null "$BASE_URL/.env" 2>/dev/null || exit_code=$?
  echo "  curl exit code: $exit_code"
}

attack_failed_login() {
  # Threshold: 5 failures in 60 s per source IP (default-settings.properties).
  local threshold=5
  printf "  Sending %d POST /login requests with wrong credentials ...\n" "$threshold"
  for ((i = 1; i <= threshold; i++)); do
    printf "  Attempt %d/%d ...\n" "$i" "$threshold"
    local exit_code=0
    curl -s --max-time 5 \
      -X POST "$BASE_URL/login" \
      --data-urlencode "username=attacker" \
      --data-urlencode "password=wrongpassword${i}" \
      --data-urlencode "next=/" \
      -o /dev/null 2>/dev/null || exit_code=$?
    echo "  curl exit code: $exit_code"
  done
}

attack_invalid_session() {
  # Threshold: 5 invalid tokens in 60 s per source IP.
  local threshold=5
  printf "  Sending %d GET requests with a forged PSSESSION cookie ...\n" "$threshold"
  for ((i = 1; i <= threshold; i++)); do
    printf "  Request %d/%d ...\n" "$i" "$threshold"
    local exit_code=0
    curl -s --max-time 5 \
      -H "Cookie: PSSESSION=forged-token-${i}-$$" \
      -o /dev/null "$BASE_URL/" 2>/dev/null || exit_code=$?
    echo "  curl exit code: $exit_code"
  done
}

attack_path_traversal() {
  local cookie_jar
  cookie_jar=$(mktemp)
  login "$cookie_jar"
  # --path-as-is prevents curl from normalizing the ../.. sequences before sending.
  echo "  Sending path traversal request: /photos/../../../etc/passwd"
  local exit_code=0
  curl -s --max-time 5 --path-as-is \
    -b "$cookie_jar" \
    -o /dev/null \
    "$BASE_URL/photos/../../../etc/passwd" 2>/dev/null || exit_code=$?
  echo "  curl exit code: $exit_code"
  rm -f "$cookie_jar"
}

attack_excessive_404() {
  # Threshold: 10 not-found responses in 60 s per source IP.
  local threshold=10
  local cookie_jar
  cookie_jar=$(mktemp)
  login "$cookie_jar"
  printf "  Sending %d GET requests to non-existent paths ...\n" "$threshold"
  for ((i = 1; i <= threshold; i++)); do
    printf "  Request %d/%d ...\n" "$i" "$threshold"
    local exit_code=0
    curl -s --max-time 5 \
      -b "$cookie_jar" \
      -o /dev/null \
      "$BASE_URL/nonexistent-album-path-${i}" 2>/dev/null || exit_code=$?
    echo "  curl exit code: $exit_code"
  done
  rm -f "$cookie_jar"
}

# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

run_test() {
  local name="$1" attack_fn="$2"

  printf "\n%s\n" "========================================================"
  printf "Test: %s\n" "$name"
  printf "%s\n\n" "========================================================"

  start_server
  echo ""

  "$attack_fn"

  echo ""
  if wait_for_server_down; then
    printf "PASS: %s\n" "$name"
    PASS_COUNT=$((PASS_COUNT + 1))
    RESULTS+=("PASS  $name")
  else
    printf "FAIL: %s — panic was not triggered\n" "$name"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    RESULTS+=("FAIL  $name")
    kill "${SERVER_PID:-}" 2>/dev/null || true
    wait "${SERVER_PID:-}" 2>/dev/null || true
    SERVER_PID=""
  fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

build_server
echo ""
load_settings

run_test "known-attack-probe" attack_known_attack_probe
run_test "failed-login"       attack_failed_login
run_test "invalid-session"    attack_invalid_session
run_test "path-traversal"     attack_path_traversal
run_test "excessive-404"      attack_excessive_404

printf "\n%s\n" "========================================================"
printf "Summary: %d passed, %d failed\n" "$PASS_COUNT" "$FAIL_COUNT"
printf "%s\n" "========================================================"
for result in "${RESULTS[@]}"; do
  printf "  %s\n" "$result"
done
printf "%s\n\n" "========================================================"

[[ $FAIL_COUNT -eq 0 ]]
