#!/bin/bash

# Variabili
PROJECT_ID="calmify-388723"
SERVICE_NAME="audio-streaming-api"
REGION="europe-west1"
IMAGE_NAME="gcr.io/${PROJECT_ID}/${SERVICE_NAME}"

echo "=== Step 1: Configurazione ==="
gcloud config set project ${PROJECT_ID}
gcloud auth configure-docker

echo "=== Step 2: Build locale dell'immagine ==="
docker build -t ${SERVICE_NAME} .

# Verifica che il build sia andato a buon fine
if [ $? -ne 0 ]; then
    echo "Errore nel build dell'immagine"
    exit 1
fi

echo "=== Step 3: Tag dell'immagine per GCR ==="
docker tag ${SERVICE_NAME} ${IMAGE_NAME}:latest
docker tag ${SERVICE_NAME} ${IMAGE_NAME}:v1

echo "=== Step 4: Push dell'immagine ==="
docker push ${IMAGE_NAME}:latest
docker push ${IMAGE_NAME}:v1

# Verifica che il push sia andato a buon fine
if [ $? -ne 0 ]; then
    echo "Errore nel push dell'immagine"
    exit 1
fi

echo "=== Step 5: Verifica che l'immagine sia presente ==="
gcloud container images list --repository=gcr.io/${PROJECT_ID}

echo "=== Step 6: Deploy su Cloud Run ==="
gcloud run deploy ${SERVICE_NAME} \
  --image ${IMAGE_NAME}:latest \
  --platform managed \
  --region ${REGION} \
  --allow-unauthenticated \
  --port 8080 \
  --memory 1Gi \
  --timeout 300 \
  --max-instances 10 \
  --min-instances 0 \
  --service-account ${PROJECT_ID}@appspot.gserviceaccount.com

echo "=== Deploy completato! ==="