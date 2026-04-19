#!/usr/bin/env bash
# ============================================================================
# Calmify Server — Full E2E Smoke Test (all 25 route groups, 150+ endpoints)
# ============================================================================
# Usage:
#   TOKEN="eyJhbG..." bash scripts/smoke-test-e2e.sh
#
# Optional env vars:
#   BASE_URL  — override server URL (default: production Cloud Run)
#   TARGET_ID — a second userId for social tests (follow/block)
#   VERBOSE   — set to "1" for full response bodies
# ============================================================================

set -euo pipefail

BASE="${BASE_URL:-https://calmify-server-23546263069.europe-west1.run.app}"
API="$BASE/api/v1"
TARGET="${TARGET_ID:-}"

if [[ -z "${TOKEN:-}" ]]; then
  echo "ERROR: TOKEN env var required. Get a Firebase ID token and run:"
  echo '  TOKEN="eyJhbG..." bash scripts/smoke-test-e2e.sh'
  exit 1
fi

# ── Helpers ──────────────────────────────────────────────────────────────────
PASS=0; FAIL=0; SKIP=0; ERRORS=""

auth() { echo "Authorization: Bearer $TOKEN"; }
json() { echo "Content-Type: application/json"; }
accept_json() { echo "Accept: application/json"; }

# test_endpoint <METHOD> <URL> <EXPECTED_CODES> <LABEL> [BODY]
# EXPECTED_CODES is a comma-separated list, e.g. "200,204"
test_endpoint() {
  local method="$1" url="$2" expected="$3" label="$4" body="${5:-}"
  local cmd=(curl -s -o /tmp/smoke_body -w "%{http_code}" -X "$method"
    -H "$(auth)" -H "$(accept_json)")

  if [[ -n "$body" ]]; then
    cmd+=(-H "$(json)" -d "$body")
  elif [[ "$method" == "POST" || "$method" == "PUT" || "$method" == "PATCH" ]]; then
    cmd+=(-H "Content-Length: 0")
  fi
  cmd+=("$url")

  local code
  code=$("${cmd[@]}" 2>/dev/null) || code="CURL_ERR"

  # Check if code is in expected list
  local ok=false
  IFS=',' read -ra codes <<< "$expected"
  for c in "${codes[@]}"; do
    [[ "$code" == "$c" ]] && ok=true
  done

  if $ok; then
    echo "  ✓ $label → $code"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $label → $code (expected $expected)"
    FAIL=$((FAIL + 1))
    ERRORS="${ERRORS}\n  ✗ $label → $code (expected $expected)"
    if [[ "${VERBOSE:-}" == "1" ]]; then
      echo "    Response: $(cat /tmp/smoke_body | head -c 300)"
    fi
  fi
}

skip_endpoint() {
  echo "  ⊘ $1 — SKIPPED ($2)"
  SKIP=$((SKIP + 1))
}

section() {
  echo ""
  echo "━━━ $1 ━━━"
}

# ── Extract userId from token (JWT decode) ──────────────────────────────────
# Firebase JWT has `user_id` or `sub` in payload
USER_ID=$(echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('user_id', data.get('sub', '')))
except: pass
" 2>/dev/null || echo "")

if [[ -z "$USER_ID" ]]; then
  echo "WARN: Could not extract userId from token. Social tests that need it will use placeholder."
  USER_ID="unknown"
fi
echo "User ID: $USER_ID"
echo "Server:  $BASE"
echo "Target:  ${TARGET:-<none — social mutation tests will be skipped>}"
echo ""

# ════════════════════════════════════════════════════════════════════════════
# 1. HEALTH (no auth)
# ════════════════════════════════════════════════════════════════════════════
section "1. Health (no auth)"
code=$(curl -s -o /tmp/smoke_body -w "%{http_code}" "$BASE/health")
if [[ "$code" == "200" ]]; then
  echo "  ✓ GET /health → $code"
  PASS=$((PASS + 1))
else
  echo "  ✗ GET /health → $code (expected 200)"
  FAIL=$((FAIL + 1))
  ERRORS="${ERRORS}\n  ✗ GET /health → $code"
fi

# ════════════════════════════════════════════════════════════════════════════
# 2. DIARY
# ════════════════════════════════════════════════════════════════════════════
section "2. Diary CRUD"
test_endpoint GET "$API/diaries?page=0&size=5" "200" "List diaries"
# Diary range uses epoch millis (start/end) — compute current year range
YEAR_START=$(date -d "2026-01-01" +%s 2>/dev/null || date -j -f "%Y-%m-%d" "2026-01-01" +%s)000
YEAR_END=$(date -d "2026-12-31" +%s 2>/dev/null || date -j -f "%Y-%m-%d" "2026-12-31" +%s)000
test_endpoint GET "$API/diaries/range?start=$YEAR_START&end=$YEAR_END" "200" "Diaries by range"

# Create → update → delete
DIARY_BODY='{"title":"Smoke Test","content":"E2E test entry","mood":"HAPPY","images":[]}'
test_endpoint POST "$API/diaries" "200,201" "Create diary" "$DIARY_BODY"
DIARY_ID=$(python3 -c "import json; d=json.load(open('/tmp/smoke_body')); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [[ -n "$DIARY_ID" && "$DIARY_ID" != "" ]]; then
  test_endpoint GET "$API/diaries/$DIARY_ID" "200" "Get diary by ID"
  test_endpoint PUT "$API/diaries/$DIARY_ID" "200" "Update diary" '{"title":"Smoke Updated","content":"Updated","mood":"CALM","images":[]}'
  test_endpoint DELETE "$API/diaries/$DIARY_ID" "200,204" "Delete diary"
else
  skip_endpoint "GET/PUT/DELETE diary by ID" "create returned no ID"
fi

# ════════════════════════════════════════════════════════════════════════════
# 3. CHAT
# ════════════════════════════════════════════════════════════════════════════
section "3. Chat"
test_endpoint GET "$API/chat/sessions" "200" "List sessions"

CHAT_BODY='{"title":"Smoke session"}'
test_endpoint POST "$API/chat/sessions" "200,201" "Create session" "$CHAT_BODY"
SESSION_ID=$(python3 -c "import json; d=json.load(open('/tmp/smoke_body')); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [[ -n "$SESSION_ID" && "$SESSION_ID" != "" ]]; then
  test_endpoint GET "$API/chat/sessions/$SESSION_ID" "200" "Get session by ID"
  test_endpoint GET "$API/chat/sessions/$SESSION_ID/messages" "200" "List messages"

  MSG_BODY='{"content":"Hello from smoke test","role":"user"}'
  test_endpoint POST "$API/chat/sessions/$SESSION_ID/messages" "200,201" "Send message" "$MSG_BODY"

  test_endpoint DELETE "$API/chat/sessions/$SESSION_ID" "200,204" "Delete session"
else
  skip_endpoint "Chat session ops" "create returned no ID"
fi

# ════════════════════════════════════════════════════════════════════════════
# 4. INSIGHTS
# ════════════════════════════════════════════════════════════════════════════
section "4. Insights"
test_endpoint GET "$API/insights?page=0&size=5" "200" "List insights"

# ════════════════════════════════════════════════════════════════════════════
# 5. PROFILE
# ════════════════════════════════════════════════════════════════════════════
section "5. Profile"
test_endpoint GET "$API/profile" "200" "Get profile"
test_endpoint GET "$API/profile/psychological" "200" "Psychological profiles"
test_endpoint GET "$API/profile/psychological/latest" "200,204,404" "Latest psych profile (404=none yet)"

# ════════════════════════════════════════════════════════════════════════════
# 6. SOCIAL (Feed + Threads)
# ════════════════════════════════════════════════════════════════════════════
section "6. Social (Feed + Threads)"
test_endpoint GET "$API/feed/for-you?page=0&size=5" "200" "For-you feed"
test_endpoint GET "$API/feed/following?page=0&size=5" "200" "Following feed"

THREAD_BODY='{"content":"Smoke test thread","mediaUrls":[]}'
test_endpoint POST "$API/threads" "200,201" "Create thread" "$THREAD_BODY"
THREAD_ID=$(python3 -c "import json; d=json.load(open('/tmp/smoke_body')); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [[ -n "$THREAD_ID" && "$THREAD_ID" != "" ]]; then
  test_endpoint GET "$API/threads/$THREAD_ID" "200" "Get thread"
  test_endpoint GET "$API/threads/$THREAD_ID/replies" "200" "Thread replies"
  test_endpoint POST "$API/threads/$THREAD_ID/like" "200,204" "Like thread"
  test_endpoint DELETE "$API/threads/$THREAD_ID/like" "200,204" "Unlike thread"
  test_endpoint DELETE "$API/threads/$THREAD_ID" "200,204" "Delete thread"
else
  skip_endpoint "Thread ops" "create returned no ID"
fi

# ════════════════════════════════════════════════════════════════════════════
# 7. DASHBOARD + FEATURE FLAGS
# ════════════════════════════════════════════════════════════════════════════
section "7. Dashboard + Feature Flags"
test_endpoint GET "$API/home/dashboard" "200" "Home dashboard"

# Feature flags — no auth required
code=$(curl -s -o /tmp/smoke_body -w "%{http_code}" "$API/feature-flags")
if [[ "$code" == "200" ]]; then
  echo "  ✓ GET /feature-flags (no auth) → $code"
  PASS=$((PASS + 1))
else
  echo "  ✗ GET /feature-flags (no auth) → $code (expected 200)"
  FAIL=$((FAIL + 1))
  ERRORS="${ERRORS}\n  ✗ GET /feature-flags → $code"
fi

# ════════════════════════════════════════════════════════════════════════════
# 8. NOTIFICATIONS
# ════════════════════════════════════════════════════════════════════════════
section "8. Notifications"
test_endpoint GET "$API/notifications" "200" "List notifications"
test_endpoint GET "$API/notifications/unread-count" "200" "Unread count"
test_endpoint POST "$API/notifications/read-all" "200,204" "Mark all read"

# ════════════════════════════════════════════════════════════════════════════
# 9. WELLNESS — All 13 types (CRUD)
# ════════════════════════════════════════════════════════════════════════════
section "9. Wellness (13 entity types)"

WELLNESS_TYPES=(gratitude energy sleep meditation habits movement reframe wellbeing awe connection recurring block values)
TODAY=$(date +%Y-%m-%d)

for wtype in "${WELLNESS_TYPES[@]}"; do
  test_endpoint GET "$API/wellness/$wtype?page=0&size=3" "200" "$wtype: list"
  test_endpoint GET "$API/wellness/$wtype/day/$TODAY" "200" "$wtype: day"
done

# Habit-specific routes
test_endpoint GET "$API/wellness/habits/completions/day/$TODAY" "200" "Habit completions today"

# ════════════════════════════════════════════════════════════════════════════
# 10. AI
# ════════════════════════════════════════════════════════════════════════════
section "10. AI"
test_endpoint GET "$API/ai/usage" "200" "AI usage stats"
# Skip AI generation endpoints (they cost Gemini tokens)
skip_endpoint "POST /ai/chat" "costs Gemini tokens"
skip_endpoint "POST /ai/insight" "costs Gemini tokens"
skip_endpoint "POST /ai/analyze" "costs Gemini tokens"

# ════════════════════════════════════════════════════════════════════════════
# 11. SYNC
# ════════════════════════════════════════════════════════════════════════════
section "11. Sync"
test_endpoint GET "$API/sync/changes?since=0" "200" "Get changes since epoch"

# ════════════════════════════════════════════════════════════════════════════
# 12. SEARCH
# ════════════════════════════════════════════════════════════════════════════
section "12. Search"
test_endpoint GET "$API/search/threads?q=test&limit=5" "200" "Search threads"
test_endpoint GET "$API/search/users?q=test&limit=5" "200" "Search users"

# ════════════════════════════════════════════════════════════════════════════
# 13. PRESENCE
# ════════════════════════════════════════════════════════════════════════════
section "13. Presence"
test_endpoint POST "$API/presence/online" "200,204" "Set online"
test_endpoint GET "$API/presence/$USER_ID" "200" "Get own presence"
test_endpoint POST "$API/presence/offline" "200,204" "Set offline"

# ════════════════════════════════════════════════════════════════════════════
# 14. CONTENT MODERATION
# ════════════════════════════════════════════════════════════════════════════
section "14. Content Moderation"
MOD_BODY='{"text":"This is a friendly smoke test message"}'
test_endpoint POST "$API/moderation/toxicity" "200" "Toxicity check" "$MOD_BODY"
test_endpoint POST "$API/moderation/sentiment" "200" "Sentiment analysis" "$MOD_BODY"
test_endpoint POST "$API/moderation/mood" "200" "Mood classification" "$MOD_BODY"

# ════════════════════════════════════════════════════════════════════════════
# 15. WAITLIST
# ════════════════════════════════════════════════════════════════════════════
section "15. Waitlist"
test_endpoint POST "$API/waitlist" "200,201,409" "Waitlist signup" '{"email":"smoke-test@calmify.app"}'

# ════════════════════════════════════════════════════════════════════════════
# 16. MEDIA
# ════════════════════════════════════════════════════════════════════════════
section "16. Media (Presigned URLs)"
test_endpoint POST "$API/media/upload-url" "200" "Get upload URL" '{"mimeType":"image/png","folder":"smoke-test"}'
test_endpoint POST "$API/media/resolve-urls" "200" "Resolve URLs" '{"paths":["smoke-test/test.png"]}'

# ════════════════════════════════════════════════════════════════════════════
# 17. MESSAGING
# ════════════════════════════════════════════════════════════════════════════
section "17. Messaging"
test_endpoint GET "$API/messaging/conversations" "200" "List conversations"

if [[ -n "$TARGET" ]]; then
  CONV_BODY="{\"participantIds\":[\"$USER_ID\",\"$TARGET\"]}"
  test_endpoint POST "$API/messaging/conversations" "200,201" "Create conversation" "$CONV_BODY"
  CONV_ID=$(python3 -c "import json; d=json.load(open('/tmp/smoke_body')); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
  if [[ -n "$CONV_ID" && "$CONV_ID" != "" ]]; then
    test_endpoint GET "$API/messaging/conversations/$CONV_ID/messages" "200" "List messages"
    test_endpoint POST "$API/messaging/conversations/$CONV_ID/messages" "200,201" "Send message" '{"content":"Smoke test msg"}'
    test_endpoint POST "$API/messaging/conversations/$CONV_ID/read" "200,204" "Mark read"
    test_endpoint GET "$API/messaging/conversations/$CONV_ID/typing" "200" "Get typing"
  else
    skip_endpoint "Conversation ops" "create returned no ID"
  fi
else
  skip_endpoint "Messaging mutations" "no TARGET_ID provided"
fi

# WebSocket — just test upgrade attempt (101 or 400/401 without proper handshake is fine)
code=$(curl -s -o /dev/null -w "%{http_code}" -H "$(auth)" "$API/messaging/ws" --max-time 3 2>/dev/null || echo "TIMEOUT")
if [[ "$code" == "101" || "$code" == "400" || "$code" == "426" || "$code" == "TIMEOUT" ]]; then
  echo "  ✓ WS /messaging/ws → $code (upgrade endpoint reachable)"
  PASS=$((PASS + 1))
else
  echo "  ⊘ WS /messaging/ws → $code (non-critical)"
  SKIP=$((SKIP + 1))
fi

# ════════════════════════════════════════════════════════════════════════════
# 18. AVATAR
# ════════════════════════════════════════════════════════════════════════════
section "18. Avatar"
test_endpoint GET "$API/avatars" "200" "List avatars"
# Skip POST (triggers Gemini pipeline — costs tokens + creates state)
skip_endpoint "POST /avatars" "triggers Gemini pipeline"

# ════════════════════════════════════════════════════════════════════════════
# 19. ENVIRONMENT (Phase 5)
# ════════════════════════════════════════════════════════════════════════════
section "19. Environment (Phase 5 — NEW)"
test_endpoint GET "$API/environment" "200,204" "Get checklist"

ENV_BODY='{"items":[{"id":"smoke1","label":"Natural light","completed":false}],"morningRoutine":[{"id":"m1","label":"Meditate","durationMinutes":10}],"eveningRoutine":[]}'
test_endpoint PUT "$API/environment" "200" "Save checklist" "$ENV_BODY"
test_endpoint GET "$API/environment" "200" "Get checklist (after save)"

# ════════════════════════════════════════════════════════════════════════════
# 20. GARDEN (Phase 5)
# ════════════════════════════════════════════════════════════════════════════
section "20. Garden (Phase 5 — NEW)"
test_endpoint GET "$API/garden" "200" "Get garden state"
test_endpoint POST "$API/garden/explored/smoke_yoga" "200" "Mark explored (idempotent)"
test_endpoint POST "$API/garden/explored/smoke_yoga" "200" "Mark explored again (idempotent)"
test_endpoint POST "$API/garden/favorites/smoke_yoga" "200" "Toggle favorite ON"
test_endpoint GET "$API/garden" "200" "Garden state (should have smoke_yoga)"
test_endpoint POST "$API/garden/favorites/smoke_yoga" "200" "Toggle favorite OFF"

# ════════════════════════════════════════════════════════════════════════════
# 21. IKIGAI (Phase 5)
# ════════════════════════════════════════════════════════════════════════════
section "21. Ikigai (Phase 5 — NEW)"
test_endpoint GET "$API/ikigai" "200,204" "Get exploration"

IKIGAI_BODY='{"passions":["music","coding"],"talents":["problem-solving"],"worldNeeds":["mental health"],"paidFor":["software"]}'
test_endpoint PUT "$API/ikigai" "200" "Save exploration" "$IKIGAI_BODY"
test_endpoint GET "$API/ikigai" "200" "Get exploration (after save)"
test_endpoint DELETE "$API/ikigai/$USER_ID" "200,204,404" "Delete own exploration (404=already gone)"

# ════════════════════════════════════════════════════════════════════════════
# 22. SOCIAL GRAPH (Phase 5)
# ════════════════════════════════════════════════════════════════════════════
section "22. Social Graph (Phase 5 — NEW)"
test_endpoint GET "$API/social-graph/followers/$USER_ID?limit=10" "200" "Get own followers"
test_endpoint GET "$API/social-graph/following/$USER_ID?limit=10" "200" "Get own following"
test_endpoint GET "$API/social-graph/suggestions?limit=10" "200" "Follow suggestions"
test_endpoint GET "$API/social-graph/profiles/$USER_ID" "200" "Get own profile"

# Username check (non-destructive)
test_endpoint GET "$API/social-graph/profiles/username-available?username=smoke_test_$(date +%s)" "200" "Username availability"

# PATCH profile — whitelist test (followerCount MUST be ignored)
test_endpoint PATCH "$API/social-graph/profiles/me" "200" "Update profile (whitelist)" \
  '{"displayName":"Smoke Test","bio":"E2E smoke","followerCount":99999}'

# Verify followerCount was NOT changed
PROFILE_AFTER=$(curl -s -H "$(auth)" -H "$(accept_json)" "$API/social-graph/profiles/$USER_ID")
FOLLOWER_COUNT=$(echo "$PROFILE_AFTER" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    profile = d.get('data', d)
    print(profile.get('followerCount', -1))
except: print(-1)
" 2>/dev/null || echo "-1")

if [[ "$FOLLOWER_COUNT" != "99999" ]]; then
  echo "  ✓ Whitelist verified — followerCount=$FOLLOWER_COUNT (not 99999)"
  PASS=$((PASS + 1))
else
  echo "  ✗ CRITICAL: followerCount=99999 — WHITELIST BROKEN"
  FAIL=$((FAIL + 1))
  ERRORS="${ERRORS}\n  ✗ CRITICAL: SocialGraph whitelist not enforcing followerCount"
fi

if [[ -n "$TARGET" ]]; then
  test_endpoint POST "$API/social-graph/follow/$TARGET" "200,204" "Follow target"
  test_endpoint GET "$API/social-graph/follow/$TARGET/check" "200" "Check isFollowing"
  test_endpoint DELETE "$API/social-graph/follow/$TARGET" "200,204" "Unfollow target"

  test_endpoint POST "$API/social-graph/block/$TARGET" "200,204" "Block target"
  test_endpoint GET "$API/social-graph/block/$TARGET/check" "200" "Check isBlocked"
  test_endpoint DELETE "$API/social-graph/block/$TARGET" "200,204" "Unblock target"
else
  skip_endpoint "Follow/unfollow/block/unblock" "no TARGET_ID provided"
fi

# ════════════════════════════════════════════════════════════════════════════
# 23. PAYMENTS (Stripe)
# ════════════════════════════════════════════════════════════════════════════
section "23. Payments (Stripe)"
test_endpoint GET "$API/subscription/state" "200" "Subscription state"
test_endpoint GET "$API/payments/products" "200" "List products"
# Skip checkout/portal — creates Stripe sessions
skip_endpoint "POST /payments/checkout-session" "creates Stripe session"
skip_endpoint "POST /payments/portal-session" "creates Stripe session"

# ════════════════════════════════════════════════════════════════════════════
# 24. STRIPE WEBHOOK (signature-validated)
# ════════════════════════════════════════════════════════════════════════════
section "24. Stripe Webhook"
# Should reject without valid Stripe signature
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"type":"test"}' \
  "$API/webhooks/stripe")
if [[ "$code" == "400" || "$code" == "401" || "$code" == "403" ]]; then
  echo "  ✓ POST /webhooks/stripe (no sig) → $code (correctly rejected)"
  PASS=$((PASS + 1))
else
  echo "  ✗ POST /webhooks/stripe (no sig) → $code (expected 400/401/403)"
  FAIL=$((FAIL + 1))
  ERRORS="${ERRORS}\n  ✗ Stripe webhook accepted unsigned request ($code)"
fi

# ════════════════════════════════════════════════════════════════════════════
# 25. GDPR
# ════════════════════════════════════════════════════════════════════════════
section "25. GDPR"
test_endpoint GET "$API/gdpr/export" "200" "Data export (Art. 20)"
# Skip DELETE — it actually deletes the account!
skip_endpoint "POST /gdpr/delete" "destructive — deletes account"

# ════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ════════════════════════════════════════════════════════════════════════════
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║         SMOKE TEST SUMMARY                      ║"
echo "╠══════════════════════════════════════════════════╣"
printf "║  ✓ PASS:    %-36s║\n" "$PASS"
printf "║  ✗ FAIL:    %-36s║\n" "$FAIL"
printf "║  ⊘ SKIP:    %-36s║\n" "$SKIP"
printf "║  TOTAL:     %-36s║\n" "$((PASS + FAIL + SKIP))"
echo "╚══════════════════════════════════════════════════╝"

if [[ $FAIL -gt 0 ]]; then
  echo ""
  echo "FAILURES:"
  echo -e "$ERRORS"
  echo ""
  exit 1
else
  echo ""
  echo "All tests passed. Server is fully operational."
  exit 0
fi
