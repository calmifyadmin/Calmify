# 1. Pulisci tutto
docker system prune -a

# 2. Configura autenticazione
gcloud auth configure-docker

# 3. Build con un nome semplice
docker build -t audio-api .

# 4. Tag per GCR
docker tag audio-api gcr.io/calmify-388723/audio-streaming-api:latest

# 5. Push
docker push gcr.io/calmify-388723/audio-streaming-api:latest

# 6. Verifica che sia presente
gcloud container images describe gcr.io/calmify-388723/audio-streaming-api:latest

gcloud run deploy audio-streaming-api --image=gcr.io/calmify-388723/audio-streaming-api:latest --region=europe-west1 --platform=managed --allow-unauthenticated