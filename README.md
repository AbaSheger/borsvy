# BörsVy Stock Analysis Platform

A web-based stock analysis platform built with React, Spring Boot, and multiple financial APIs, featuring AI analysis powered by Hugging Face.

## Project Structure

- **Frontend**: 
  - React with JavaScript (JSX) + Vite
  - UI: Ant Design (antd) + Tailwind CSS
  - Charts: Chart.js + Recharts
  - Routing: React Router
  - Build: Vite with optimized chunk splitting
  - Server: Nginx with gzip compression and caching
- **Backend**: Spring Boot 3.2.4 (Java 17) REST API
  - Tomcat server with optimized thread pool
  - Hikari connection pool for database connections
  - Detailed logging configuration
- **Database**: 
  - Development: H2 Database (file-based)
  - Production: PostgreSQL with SSL disabled
- **External APIs**: 
  - Finnhub API for real-time stock data
  - Polygon.io for historical data
  - SerpAPI for news analysis
  - Hugging Face for AI analysis
- **Deployment**: Docker containers deployed on Koyeb

## Prerequisites

- Node.js (v18 or higher)
- Java 17 or higher
- Docker
- PostgreSQL (for production)

## Running the Application

### Frontend

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Create a `.env` file with the following variables:
```
VITE_API_URL=https://borsvy-backend-borsvy-295875c6.koyeb.app
```

4. Start the development server:
```bash
npm run dev
```

The frontend will be available at http://localhost:3000

### Backend

1. Navigate to the backend directory:
```bash
cd backend
```

2. For development, the H2 database is automatically configured. For production, create an `application.properties` file with the following configuration:
```properties
# Development (H2) Database settings
spring.datasource.url=jdbc:h2:file:./data/borsvy;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=sa
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Production (PostgreSQL) Database settings
# spring.datasource.driver-class-name=org.postgresql.Driver
# spring.datasource.url=${DATABASE_URL}
# spring.datasource.username=${DATABASE_USERNAME}
# spring.datasource.password=${DATABASE_PASSWORD}
# spring.datasource.hikari.ssl-mode=disable
# spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
# spring.jpa.hibernate.ddl-auto=update

# Server settings
# server.tomcat.max-threads=200
# server.tomcat.min-spare-threads=20
# server.tomcat.uri-encoding=UTF-8

# Logging settings
# logging.level.com.borsvy=DEBUG
# logging.level.org.springframework.web=DEBUG
# logging.level.org.hibernate.SQL=DEBUG
# logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# API Keys
finnhub.api.key=${FINNHUB_API_KEY}
serpapi.api.key=${SERPAPI_API_KEY}
polygon.api.key=${POLYGON_API_KEY}
huggingface.api.key=${HUGGINGFACE_API_KEY}
```

3. Build and run the application:
```bash
./mvnw spring-boot:run
```

The backend API will be available at http://localhost:8080

## Docker Deployment

Both frontend and backend can be deployed using Docker:

### Frontend
```bash
cd frontend
docker build -t borsvy-frontend .
docker run -p 3000:3000 borsvy-frontend
```

The frontend is served using Nginx with:
- Gzip compression for better performance
- Static file caching
- React router support
- Security headers

### Backend
```bash
cd backend
docker build -t borsvy-backend .
docker run -p 8080:8080 borsvy-backend
```

## API Keys

To use the application with full functionality, you'll need:

1. A Finnhub API key (https://finnhub.io/)
2. A SerpAPI key (https://serpapi.com/)
3. A Polygon.io API key (https://polygon.io/)
4. A Hugging Face API key (https://huggingface.co/)

Add these keys to the `application.properties` file in the backend.

## Environment Variables

### Frontend
- `VITE_API_URL`: The URL of the backend API (default: https://borsvy-backend-borsvy-295875c6.koyeb.app)

### Backend
- `spring.datasource.url`: PostgreSQL connection URL (production)
- `spring.datasource.username`: Database username (production)
- `spring.datasource.password`: Database password (production)
- `finnhub.api.key`: Finnhub API key
- `serpapi.api.key`: SerpAPI key
- `polygon.api.key`: Polygon.io API key
- `huggingface.api.key`: Hugging Face API key
- `PORT`: Server port (default: 8080)

## License

MIT License