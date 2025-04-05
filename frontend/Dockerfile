FROM node:18-alpine as builder
WORKDIR /app
COPY package*.json ./
# Use npm ci for more efficient and reproducible builds
RUN npm ci --only=production
COPY . .
# Environment variable to reduce memory usage during build
ENV NODE_OPTIONS="--max-old-space-size=512"
RUN npm run build

FROM nginx:alpine
# Copy only the built files, not the entire app
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# Add environment variables to help troubleshoot
ENV NGINX_ENTRYPOINT_QUIET_LOGS=1
CMD ["nginx", "-g", "daemon off;"]