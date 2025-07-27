FROM node:18-slim

# Installa le dipendenze di sistema necessarie
RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copia package files
COPY package*.json ./

# Installa dipendenze
RUN npm ci --only=production

# Copia il resto dei files
COPY . .

# Imposta variabili d'ambiente
ENV NODE_ENV=production
ENV PORT=8080

# Esponi la porta
EXPOSE 8080

# Usa un utente non-root
USER node

# Comando di avvio
CMD ["node", "server.js"]