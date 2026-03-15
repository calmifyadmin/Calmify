#!/bin/bash
# ═══════════════════════════════════════════════════════
# Calmify Cloud Backend — Deploy Script
# Deploys Cloud Functions + Cloud Run + Cloud Tasks queue
# ═══════════════════════════════════════════════════════

set -euo pipefail

PROJECT_ID="${GCP_PROJECT:-calmify-app}"
REGION="${GCP_REGION:-europe-west1}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT_EMAIL:-}"

echo "═══════════════════════════════════════════"
echo "  Calmify Cloud Backend Deploy"
echo "  Project: ${PROJECT_ID}"
echo "  Region:  ${REGION}"
echo "═══════════════════════════════════════════"

# ─── 1. Deploy Cloud Function: createAvatarPipeline ───
echo ""
echo "[1/4] Deploying createAvatarPipeline Cloud Function..."
gcloud functions deploy createAvatarPipeline \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --runtime=python311 \
    --trigger-http \
    --allow-unauthenticated \
    --entry-point=create_avatar_pipeline \
    --source=functions/create_avatar \
    --memory=256MB \
    --timeout=60s \
    --set-env-vars="GCP_PROJECT=${PROJECT_ID},FUNCTION_REGION=${REGION}"

# ─── 2. Deploy Cloud Function: generateSystemPrompt ───
echo ""
echo "[2/4] Deploying generateSystemPrompt Cloud Function..."
gcloud functions deploy generateSystemPrompt \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --runtime=python311 \
    --trigger-http \
    --no-allow-unauthenticated \
    --entry-point=generate_system_prompt \
    --source=functions/generate_prompt \
    --memory=256MB \
    --timeout=120s \
    --set-env-vars="GEMINI_API_KEY=${GEMINI_API_KEY:-}"

# ─── 3. Create Cloud Tasks queue ───
echo ""
echo "[3/4] Creating Cloud Tasks queue: vrm-generation-queue..."
gcloud tasks queues create vrm-generation-queue \
    --project="${PROJECT_ID}" \
    --location="${REGION}" \
    --max-dispatches-per-second=2 \
    --max-concurrent-dispatches=5 \
    --max-attempts=3 \
    --min-backoff=10s \
    --max-backoff=300s \
    2>/dev/null || echo "  Queue already exists, updating..."

gcloud tasks queues update vrm-generation-queue \
    --project="${PROJECT_ID}" \
    --location="${REGION}" \
    --max-dispatches-per-second=2 \
    --max-concurrent-dispatches=5 \
    --max-attempts=3 \
    --min-backoff=10s \
    --max-backoff=300s

# ─── 4. Deploy Cloud Run: vrm-generator ───
echo ""
echo "[4/4] Building and deploying VRM Generator on Cloud Run..."
gcloud run deploy vrm-generator \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --source=vrm-generator \
    --platform=managed \
    --memory=2Gi \
    --cpu=2 \
    --timeout=300 \
    --concurrency=1 \
    --min-instances=0 \
    --max-instances=5 \
    --no-allow-unauthenticated \
    --set-env-vars="GCS_BUCKET=${PROJECT_ID}.appspot.com"

# Get Cloud Run URL for the createAvatar function
VRM_URL=$(gcloud run services describe vrm-generator \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --format="value(status.url)")

echo ""
echo "═══════════════════════════════════════════"
echo "  Deploy complete!"
echo "  VRM Generator URL: ${VRM_URL}"
echo ""
echo "  Next: Update createAvatarPipeline env var:"
echo "  gcloud functions deploy createAvatarPipeline \\"
echo "    --update-env-vars=VRM_GENERATOR_URL=${VRM_URL}"
echo "═══════════════════════════════════════════"
