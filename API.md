# Question API

## Browser setup and CORS

The API allows credentialed browser requests from the exact origin configured by `app.cors.frontend-url`. Set `FRONTEND_URL` in the backend environment (for example, `http://localhost:5173` for a local Vite frontend); the default is `http://localhost:5173`. Set it to the deployed frontend origin in production. Browser `fetch()` calls must use `credentials: "include"`, including the streaming request.

### Anonymous workspace

There is no login. First initialize a workspace with `POST /api/v1/workspace`, using `credentials: "include"`. The backend sets a random, persistent, `HttpOnly` cookie; the raw token is never returned to frontend JavaScript. Store the cookie in the browser and include it on every API request. The cookie lasts 30 days. Local HTTP uses `WORKSPACE_COOKIE_SECURE=false`; production HTTPS should set `WORKSPACE_COOKIE_SECURE=true`. `WORKSPACE_COOKIE_SAME_SITE` defaults to `Lax`; set it to `None` only when the frontend and API are on different sites, and use `Secure=true` with `None`.

```javascript
await fetch("http://localhost:8080/api/v1/workspace", {
  method: "POST",
  credentials: "include"
});
```

Uploaded documents are associated with the workspace. Private documents are visible only to their owner. Documents marked `is_public = true` are visible and can be queried from every workspace, but only the uploading workspace can delete them. Conversation history on public documents remains private to the workspace that created it. The document list includes a `publicDocument` boolean for the frontend. A private document ID from another workspace returns `404`, even if the ID is changed in the page URL. An API call without a valid workspace cookie returns `401`; initialize a workspace and retry. The server stores only a hash of the cookie token.

The workspace migration leaves pre-existing documents without an owner, so they remain private/inaccessible by default. Re-upload private documents after the migration to associate them with the current browser workspace. Existing documents intentionally meant for public showcase can instead be marked `is_public = true` after the migration.

## Document API

Upload a PDF with `POST /api/v1/documents` (`multipart/form-data`, field `file`). List documents with `GET /api/v1/documents`; each item includes its ID, filename, content type, size, processing status, and upload time. Use that ID for the document-specific question endpoints below.

`GET /api/v1/documents/{documentId}` returns the processing status for a document in the current workspace or a public document. `DELETE /api/v1/documents/{documentId}` deletes an owned document and its related chunks, conversations, and messages, then removes the stored PDF. Public documents are read-only to other workspaces. A successful delete returns `204 No Content`; an inaccessible or unknown ID returns `404`.

Documents are automatically removed after five days unless `retention_exempt` is set to `true` for that document. New documents default to `false` and expire normally. After uploading the permanent Demo and Portfolio documents, mark them public and exempt from retention by ID:

```sql
UPDATE documents
SET is_public = TRUE, retention_exempt = TRUE
WHERE id IN (123, 456); -- replace with the Demo and Portfolio document IDs
```

Retention runs once every 24 hours by default, and failed stored-file removals are retried on subsequent runs. These values can be changed with `app.document-retention.days`, `app.document-retention.scan-interval-ms`, and `app.document-retention.initial-delay-ms`.

### Restoring conversations

Conversations are scoped to both a document and the current workspace. On public documents, each visitor sees only their own chats. To show the saved chats for a document, call `GET /api/v1/documents/{documentId}/conversations`. It returns summaries ordered by most recently updated:

```json
[
  { "conversationId": 15, "createdAt": "2026-11-18T10:00:00", "updatedAt": "2026-11-18T10:05:00" }
]
```

To restore one chat, call `GET /api/v1/documents/{documentId}/conversations/{conversationId}/messages`. Messages are returned oldest-first with `role` (`USER` or `ASSISTANT`), `content`, and `createdAt`. The ask endpoints return the conversation ID in the normal JSON response or the stream's `complete` event. An unknown document or a conversation that does not belong to that document returns `404`.

Document processing is queued in the database with the upload, so pending work can resume after an application restart. Processing gets up to five attempts with backoff; after the final failure the document is marked `FAILED` before its stored PDF is cleaned up.

On upgrade, existing ready documents are reprocessed once so their stored chunks receive page numbers extracted from their PDFs. This uses the normal embedding pipeline and consumes embedding quota. Until each reprocess finishes, its current chunks remain available; if reprocessing fails, the existing chunks and ready status are kept.


Both question endpoints accept the same request body. `conversationId` is optional for a new conversation and should be the ID returned by a previous answer for follow-up questions.

```http
POST /api/v1/questions/documents/{documentId}/ask
Content-Type: application/json
```

```json
{
  "question": "Explain IPv4 simply",
  "conversationId": 15
}
```

For a new conversation, omit `conversationId` or send `null`. The JSON endpoint returns:

```json
{
  "conversationId": 15,
  "answer": "...",
  "sources": [
    { "pageNumber": 3, "chunkIndex": 4, "similarity": 0.82 }
  ]
}
```

## Streaming

```http
POST /api/v1/questions/documents/{documentId}/ask/stream
Content-Type: application/json
Accept: text/event-stream
```

This endpoint returns Server-Sent Events. Because it is a `POST` with a JSON body, use `fetch()` and read the response stream; the browser `EventSource` API only supports `GET`.

Events arrive in this order:

1. `status` indicates that document context is being retrieved.
2. `start` includes the retrieved `sources`.
3. If the primary chat provider fails with a retryable error before producing any text, another `status` event announces that the backup provider is being tried. This only happens before the first text fragment, so the answer is not mixed across providers.
4. Zero or more `delta` events each include a `delta` text fragment.
5. `complete` includes the full `answer`, `sources`, and `conversationId`.

Example event payloads:

```text
event: status
data: {"type":"status","conversationId":null,"delta":null,"answer":null,"sources":null,"message":"Retrieving document context..."}

event: status
data: {"type":"status","conversationId":null,"delta":null,"answer":null,"sources":null,"message":"Primary AI provider is unavailable; retrying with the backup provider..."}

event: start
data: {"type":"start","conversationId":null,"delta":null,"answer":null,"sources":[{"pageNumber":3,"chunkIndex":4,"similarity":0.82}],"message":null}

event: delta
data: {"type":"delta","conversationId":null,"delta":"IPv4 is ","answer":null,"sources":null,"message":null}

event: complete
data: {"type":"complete","conversationId":15,"delta":null,"answer":"IPv4 is ...","sources":[{"pageNumber":3,"chunkIndex":4,"similarity":0.82}],"message":null}
```

If context preparation or generation fails after the stream opens, an `error` event is sent and the stream ends. Request validation errors detected before the stream opens use the normal HTTP error response. The turn is saved only after a complete answer is generated and persisted.

## Demo and Portfolio document buttons

Store the selected document's ID in each Demo or Portfolio button. When the user selects a button and asks a question, send that question to the existing document-specific ask endpoint using the button's ID:

```http
POST /api/v1/questions/documents/{buttonDocumentId}/ask
```

For streamed answers, use `/ask/stream` on the same path. The backend retrieves chunks only for that document ID and returns its sources with the answer. Use `GET /api/v1/documents` to populate or validate available IDs; use `GET /api/v1/documents/{documentId}` to confirm the selected document has finished processing before enabling questions.
