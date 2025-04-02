# BörsVy Stock Analysis Platform

A web-based stock analysis platform built with React, Spring Boot, and Finnhub API, featuring AI analysis powered by Gemini 1.5 Pro.

## Project Structure

- **Frontend**: React with JavaScript (JSX) + Tailwind CSS
- **Backend**: Spring Boot (Java) REST API
- **Database**: PostgreSQL
- **External APIs**: Finnhub API for stock data, Gemini 1.5 Pro for AI analysis

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

3. Start the development server:
```bash
npm run dev
```

The frontend will be available at http://localhost:5173

### Backend

1. Navigate to the backend directory:
```bash
cd backend
```

2. Build the project:
```bash
./mvnw clean package
```

3. Run the application:
```bash
./mvnw spring-boot:run
```

The backend API will be available at http://localhost:8080

## Deployment

### Frontend (Vercel)

1. Install Vercel CLI:
```bash
npm install -g vercel
```

2. Deploy to Vercel:
```bash
cd frontend
vercel
```

### Backend (Fly.io)

1. Install Flyctl:
```bash
curl -L https://fly.io/install.sh | sh
```

2. Log in to Fly.io:
```bash
fly auth login
```

3. Create a fly.toml file in the backend directory
4. Deploy the backend:
```bash
cd backend
fly launch
```

## API Keys

To use the application with full functionality, you'll need:

1. A Finnhub API key (https://finnhub.io/)
2. A Gemini API key (https://ai.google.dev/)

Add these keys to the `application.properties` file in the backend.