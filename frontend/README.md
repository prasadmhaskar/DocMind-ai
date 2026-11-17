# DocMind frontend

React + Vite interface for the DocMind Spring Boot API.

## Run locally

1. Start the backend on `http://localhost:8080` and make sure `FRONTEND_URL=http://localhost:5173`.
2. Copy `.env.example` to `.env` if the API uses a different base URL.
3. Run `npm ci`, then `npm run dev`.
4. Open `http://localhost:5173`.

The app initializes an anonymous workspace on startup. All API calls include browser credentials so the backend's HttpOnly workspace cookie is sent automatically.
