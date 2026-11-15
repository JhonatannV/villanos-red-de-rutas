# =========================================================================
# Etapa 1: Compilacion y optimizacion del Frontend Angular
# =========================================================================
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci || npm install

COPY . .
RUN npm run build -- --configuration production

# =========================================================================
# Etapa 2: Servidor Web Nginx ultraligero (< 30 MB)
# =========================================================================
FROM nginx:1.27-alpine

COPY --from=builder /app/dist/red-rutas-web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD wget --spider -q http://localhost:80 || exit 1

CMD ["nginx", "-g", "daemon off;"]
