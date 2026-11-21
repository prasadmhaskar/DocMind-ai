const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

let workspaceReady = false;
let workspaceRequest = null;

async function initializeWorkspace() {
  if (workspaceReady) return;
  if (workspaceRequest) return workspaceRequest;

  workspaceRequest = fetch(`${API_BASE_URL}/api/v1/workspace`, {
    method: "POST",
    credentials: "include"
  }).then(async (response) => {
    if (!response.ok) {
      throw await createApiError(response, "Could not initialize your workspace.");
    }
    workspaceReady = true;
  }).finally(() => {
    workspaceRequest = null;
  });

  return workspaceRequest;
}

async function createApiError(response, fallbackMessage = "The request failed.") {
  const body = await response.text().catch(() => "");
  let message = body || `${fallbackMessage} (${response.status})`;
  if (response.status === 429) {
    const retryAfterSeconds = Number(response.headers.get("Retry-After"));
    if (Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0) {
      const wait = retryAfterSeconds < 60
        ? `${retryAfterSeconds} second${retryAfterSeconds === 1 ? "" : "s"}`
        : `${Math.ceil(retryAfterSeconds / 60)} minute${Math.ceil(retryAfterSeconds / 60) === 1 ? "" : "s"}`;
      message = `${message} Try again in ${wait}.`;
    }
  }
  const error = new Error(message);
  error.status = response.status;
  return error;
}

async function fetchWithWorkspace(path, options = {}, retry = true) {
  await initializeWorkspace();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: "include"
  });

  if (response.status === 401 && retry) {
    workspaceReady = false;
    await initializeWorkspace();
    return fetchWithWorkspace(path, options, false);
  }

  return response;
}

async function request(path, options = {}) {
  const response = await fetchWithWorkspace(path, options);
  if (!response.ok) {
    throw await createApiError(response);
  }
  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  initializeWorkspace,

  getDocuments() {
    return request("/api/v1/documents");
  },

  getDocumentStatus(documentId) {
    return request(`/api/v1/documents/${documentId}`);
  },

  uploadDocument(file) {
    const form = new FormData();
    form.append("file", file);
    return request("/api/v1/documents", { method: "POST", body: form });
  },

  deleteDocument(documentId) {
    return request(`/api/v1/documents/${documentId}`, { method: "DELETE" });
  },

  getConversations(documentId) {
    return request(`/api/v1/documents/${documentId}/conversations`);
  },

  getMessages(documentId, conversationId) {
    return request(`/api/v1/documents/${documentId}/conversations/${conversationId}/messages`);
  },

  async *streamQuestion(documentId, question, conversationId, signal) {
    const options = {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream"
      },
      body: JSON.stringify({ question, conversationId: conversationId ?? null }),
      signal
    };

    const response = await fetchWithWorkspace(
      `/api/v1/questions/documents/${documentId}/ask/stream`, options);

    if (!response.ok) {
      throw await createApiError(response, "Could not send your question.");
    }
    if (!response.body) {
      throw new Error("The browser did not provide a readable response stream.");
    }

    yield* parseEventStream(response.body);
  }
};

async function* parseEventStream(stream) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  const parseFrame = (frame) => {
    const data = frame
      .split(/\r?\n/)
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).replace(/^ /, ""))
      .join("\n");
    if (!data) return null;
    try {
      return JSON.parse(data);
    } catch {
      return null;
    }
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

      let boundary;
      while ((boundary = buffer.search(/\r?\n\r?\n/)) !== -1) {
        const frame = buffer.slice(0, boundary);
        const separator = buffer.slice(boundary).match(/^\r?\n\r?\n/)[0];
        buffer = buffer.slice(boundary + separator.length);
        const event = parseFrame(frame);
        if (event) yield event;
      }

      if (done) break;
    }

    if (buffer.trim()) {
      const event = parseFrame(buffer);
      if (event) yield event;
    }
  } finally {
    reader.releaseLock();
  }
}
