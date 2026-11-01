#!/bin/sh
set -eu

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 FIREBASE_AND_GCP_PROJECT_ID [REGION]" >&2
    exit 2
fi

PROJECT_ID=$1
REGION=${2:-us-central1}
SERVICE_ACCOUNT="factorybill-bill-scan@${PROJECT_ID}.iam.gserviceaccount.com"
: "${FACTORYBILL_FIREBASE_APP_ID:?Set FACTORYBILL_FIREBASE_APP_ID to the registered Android Firebase App ID}"

gcloud config set project "$PROJECT_ID"
gcloud services enable \
    run.googleapis.com \
    cloudbuild.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com

if ! gcloud iam service-accounts describe "$SERVICE_ACCOUNT" --project "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam service-accounts create factorybill-bill-scan \
        --project "$PROJECT_ID" \
        --display-name "FactoryBill bill scanner"
fi

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${SERVICE_ACCOUNT}" \
    --role roles/secretmanager.secretAccessor \
    --quiet

gcloud run deploy factorybill-bill-scan \
    --source . \
    --project "$PROJECT_ID" \
    --region "$REGION" \
    --service-account "$SERVICE_ACCOUNT" \
    --allow-unauthenticated \
    --set-secrets "GEMINI_API_KEY=gemini-api-key:latest" \
    --set-env-vars "FIREBASE_PROJECT_ID=${PROJECT_ID},FIREBASE_APP_ID=${FACTORYBILL_FIREBASE_APP_ID},GEMINI_MODEL=gemini-2.5-flash" \
    --max-instances 1 \
    --concurrency 1 \
    --timeout 60 \
    --memory 512Mi \
    --cpu 1

gcloud run services describe factorybill-bill-scan \
    --project "$PROJECT_ID" \
    --region "$REGION" \
    --format "value(status.url)"
