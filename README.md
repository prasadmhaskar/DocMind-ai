# DocMind AI

[**Try DocMind AI live**](https://docmind-ai.duckdns.org)

DocMind answers questions about PDFs and shows the pages it used. Upload a PDF, try the trip-planning demo, or ask about Prasad’s background and projects.

## Features

- Upload PDFs up to 20 MB. Tesseract OCR handles scanned pages.
- Get streamed answers with page references. DocMind combines vector and keyword search to find relevant passages.
- Resume previous chats in the same browser. Private uploads and chats are tied to a workspace cookie.
- Uploaded documents expire after five days by default. The public demo and profile are exempt.

## Stack

React and Vite · Java 21 and Spring Boot · PostgreSQL 17, pgvector, and Flyway · Gemini embeddings · Groq chat with Gemini fallback

## Run locally

Requires Java 21, Node.js with npm, PostgreSQL 17 with pgvector, and API keys for Groq and Gemini. Install Tesseract with English language data if you want to process scanned PDFs.

1. Start PostgreSQL with `docker compose up -d postgres`, or use a local PostgreSQL server. Before starting the backend, enable the `vector` extension in `docmind_ai`. For a fresh database, put the extension in a separate schema and add that schema after `public` in the app role’s search path so Flyway can initialize the application schema.
2. Export `GROQ_API_KEY` and `GEMINI_API_KEY`. Review the database and storage settings in [application-dev.yml](src/main/resources/application-dev.yml), then start the backend:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Start the frontend and open [localhost:5173](http://localhost:5173):

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

## Production

DocMind runs on AWS EC2 with Nginx, Spring Boot, and native PostgreSQL. Nginx serves the frontend over HTTPS and forwards `/api/` to Spring Boot. The production settings are in [application-prod.yml](src/main/resources/application-prod.yml).

For a new deployment, use the `prod` profile, keep the PDF storage path on persistent disk, and use `VITE_API_BASE_URL=/` for the frontend build. Set the showcase IDs to the actual public document IDs in that database and keep the backend and Vite values aligned.

[API reference](API.md) · [Demo PDF source](docs/demo/how-to-plan-a-trip.md)
