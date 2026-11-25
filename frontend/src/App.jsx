import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api.js";
import Icon from "./Icon.jsx";

const suggestions = [
  { icon: "book", title: "Give me a quick overview", prompt: "Give me a quick overview of this document." },
  { icon: "search", title: "Find the key ideas", prompt: "What are the key ideas in this document?" },
  { icon: "spark", title: "Explain something simply", prompt: "Explain the most important concept in simple terms." }
];
const DOCUMENT_PROCESSING_TIMEOUT_MS = 2 * 60 * 1000;
const NO_SAMPLE_QUESTIONS = [];
const DEMO_DOCUMENT_ID = Number(import.meta.env.VITE_DEMO_DOCUMENT_ID || 50);
const PROFILE_DOCUMENT_ID = Number(import.meta.env.VITE_PROFILE_DOCUMENT_ID || 49);
const HOMEPAGE_SHOWCASE_IDS = [PROFILE_DOCUMENT_ID, DEMO_DOCUMENT_ID];
const SHOWCASE_DOCUMENT_PRESENTATION = {
  [PROFILE_DOCUMENT_ID]: {
    title: "Ask about Prasad",
    headerTitle: "Prasad’s Profile",
    chatHeading: "Ask questions about",
    chatEmphasis: "Prasad",
    chatDescription: "Explore Prasad’s education, experience, technical skills, projects, and career goals.",
    sampleQuestions: [
      "What did Prasad study?",
      "What did Prasad do before pursuing software development?",
      "Which projects has Prasad built?",
      "What technologies did Prasad use to build DocMind AI?"
    ]
  },
  [DEMO_DOCUMENT_ID]: {
    title: "Use demo PDF",
    headerTitle: "Trip Planning Guide",
    chatHeading: "Plan your next trip",
    chatEmphasis: "one step at a time",
    chatDescription: "Explore a friendly guide to choosing dates, budgeting, transport, accommodation, packing, and simple itineraries.",
    sampleQuestions: [
      "What is a simple way to plan a trip?",
      "How should I set a trip budget?",
      "What should I compare before booking transport?",
      "What is a simple three-day trip itinerary?",
      "What should I check the day before departure?"
    ]
  }
};

function showcasePresentation(document) {
  return SHOWCASE_DOCUMENT_PRESENTATION[document.documentId] || {
    title: document.originalFilename
  };
}

function showcasePriority(document) {
  return Number(document.documentId) === PROFILE_DOCUMENT_ID ? 0
    : Number(document.documentId) === DEMO_DOCUMENT_ID ? 1
      : 2;
}

function readRoute() {
  const match = window.location.pathname.match(/^\/chat\/(\d+)\/?$/);
  return {
    documentId: match ? Number(match[1]) : null,
    conversationId: Number(new URLSearchParams(window.location.search).get("conversationId")) || null
  };
}

function writeRoute(documentId, conversationId, replace = false) {
  const path = documentId ? `/chat/${documentId}` : "/";
  const query = conversationId ? `?conversationId=${conversationId}` : "";
  window.history[replace ? "replaceState" : "pushState"]({}, "", `${path}${query}`);
}

function formatInline(text) {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, index) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith("`") && part.endsWith("`")) {
      return <code key={index}>{part.slice(1, -1)}</code>;
    }
    return part;
  });
}

function AnswerText({ text }) {
  const blocks = [];
  let listItems = [];
  const flushList = (key) => {
    if (listItems.length) {
      blocks.push(<ul key={`list-${key}`}>{listItems}</ul>);
      listItems = [];
    }
  };

  text.split("\n").forEach((line, index) => {
    const bullet = line.match(/^\s*(?:[-*•])\s+(.+)/);
    const numbered = line.match(/^\s*\d+[.)]\s+(.+)/);
    const content = (bullet || numbered)?.[1];
    if (content) {
      listItems.push(<li key={`item-${index}`}>{formatInline(content)}</li>);
      return;
    }

    flushList(index);
    const trimmed = line.trim();
    if (!trimmed) return;
    const heading = trimmed.match(/^#{1,4}\s+(.+)/);
    blocks.push(heading
      ? <h3 key={`heading-${index}`}>{formatInline(heading[1])}</h3>
      : <p key={`paragraph-${index}`}>{formatInline(trimmed)}</p>);
  });
  flushList("end");
  return <div className="answer-text">{blocks}</div>;
}

function StatusBadge({ status, timedOut = false }) {
  const value = (status || "").toUpperCase();
  const label = timedOut ? "Delayed" : value === "READY" ? "Ready" : value === "FAILED" ? "Failed" : "Processing";
  return <span className={`status-badge status-${timedOut ? "delayed" : value.toLowerCase()}`}>
    {!timedOut && (value === "PROCESSING" || value === "UPLOADING")
      ? <span className="status-spinner" />
      : <span className="status-dot" />}
    {label}
  </span>;
}

function ProfileLinks() {
  return (
    <div className="topbar-profile" aria-label="Developer profile">
      <div className="profile-copy">
        <strong>Prasad</strong>
        <span>Java Full Stack Developer</span>
      </div>
      <span className="profile-divider" aria-hidden="true" />
      <a className="profile-link linkedin-link" href="https://www.linkedin.com/in/prasadmhaskar/"
        target="_blank" rel="noopener noreferrer" aria-label="Prasad on LinkedIn" title="LinkedIn">in</a>
      <a className="profile-link" href="https://github.com/prasadmhaskar"
        target="_blank" rel="noopener noreferrer" aria-label="Prasad on GitHub" title="GitHub">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 .9a11.1 11.1 0 0 0-3.51 21.63c.55.1.76-.24.76-.53v-2.07c-3.1.68-3.76-1.32-3.76-1.32-.5-1.29-1.24-1.63-1.24-1.63-1.01-.69.08-.68.08-.68 1.12.08 1.71 1.15 1.71 1.15 1 1.71 2.63 1.22 3.27.93.1-.72.39-1.22.71-1.5-2.48-.28-5.09-1.24-5.09-5.52 0-1.22.44-2.22 1.15-3-.12-.28-.5-1.42.11-2.96 0 0 .94-.3 3.05 1.15a10.6 10.6 0 0 1 5.55 0c2.11-1.45 3.05-1.15 3.05-1.15.61 1.54.23 2.68.11 2.96.72.78 1.15 1.78 1.15 3 0 4.29-2.61 5.24-5.1 5.51.4.35.76 1.03.76 2.08V22c0 .29.2.63.76.52A11.1 11.1 0 0 0 12 .9Z" /></svg>
      </a>
    </div>
  );
}

function timeLabel(timestamp) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return "";
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  return date.toLocaleDateString([], { month: "short", day: "numeric" });
}

export default function App() {
  const [workspaceState, setWorkspaceState] = useState("loading");
  const [documents, setDocuments] = useState([]);
  const [selectedDocumentId, setSelectedDocumentId] = useState(null);
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [chatLoading, setChatLoading] = useState(false);
  const [documentsLoading, setDocumentsLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [downloadingDemo, setDownloadingDemo] = useState(false);
  const [question, setQuestion] = useState("");
  const [sampleQuestionIndex, setSampleQuestionIndex] = useState(0);
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sidebarSearch, setSidebarSearch] = useState("");
  const inputRef = useRef(null);
  const fileRef = useRef(null);
  const searchRef = useRef(null);
  const startNewChatRef = useRef(null);
  const bottomRef = useRef(null);
  const streamControllerRef = useRef(null);

  const selectedDocument = useMemo(
    () => documents.find((document) => document.documentId === selectedDocumentId) || null,
    [documents, selectedDocumentId]
  );
  const showcaseDocuments = useMemo(() => {
    return documents.filter((document) => document.publicDocument && showcasePriority(document) < 2)
      .sort((left, right) => showcasePriority(left) - showcasePriority(right));
  }, [documents]);
  const activeShowcaseDocumentId = [DEMO_DOCUMENT_ID, PROFILE_DOCUMENT_ID].includes(Number(selectedDocumentId))
    ? Number(selectedDocumentId)
    : null;
  const activeShowcasePresentation = activeShowcaseDocumentId
    ? SHOWCASE_DOCUMENT_PRESENTATION[activeShowcaseDocumentId]
    : null;
  const activeSampleQuestions = activeShowcasePresentation?.sampleQuestions || NO_SAMPLE_QUESTIONS;
  const activeSampleQuestion = activeSampleQuestions.length
    ? activeSampleQuestions[sampleQuestionIndex % activeSampleQuestions.length]
    : null;
  const selectedDocumentProcessingTimedOut = useMemo(() => {
    if (!selectedDocument || !["PROCESSING", "UPLOADING"].includes(selectedDocument.documentStatus)) return false;
    const uploadedAt = new Date(selectedDocument.uploadedAt).getTime();
    return Number.isFinite(uploadedAt) && Date.now() - uploadedAt >= DOCUMENT_PROCESSING_TIMEOUT_MS;
  }, [selectedDocument]);

  const refreshDocuments = useCallback(async () => {
    const list = await api.getDocuments();
    setDocuments(Array.isArray(list) ? list : []);
    return Array.isArray(list) ? list : [];
  }, []);

  const openDocument = useCallback(async (documentId, conversationId = null, { replace = false, resumeLatest = false } = {}) => {
    setSelectedDocumentId(documentId);
    setActiveConversationId(conversationId);
    setMessages([]);
    setChatLoading(Boolean(conversationId) || resumeLatest);
    setSidebarOpen(false);
    if (!resumeLatest) writeRoute(documentId, conversationId, replace);

    try {
      const list = await api.getConversations(documentId);
      const conversationList = Array.isArray(list) ? list : [];
      const targetConversationId = resumeLatest
        ? conversationList[0]?.conversationId ?? null
        : conversationId;
      setConversations(conversationList);
      setActiveConversationId(targetConversationId);
      writeRoute(documentId, targetConversationId, replace);
      if (targetConversationId) {
        const savedMessages = await api.getMessages(documentId, targetConversationId);
        setMessages((savedMessages || []).map((message) => ({
          id: `saved-${message.messageId}`,
          role: String(message.role).toLowerCase(),
          content: message.content,
          sources: [],
          isStreaming: false
        })));
      } else {
        setMessages([]);
      }
    } catch (error) {
      setNotice(error.message || "Could not load this document.");
      setActiveConversationId(null);
      setMessages([]);
      writeRoute(documentId, null, true);
    } finally {
      setChatLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        setWorkspaceState("loading");
        await api.initializeWorkspace();
        const list = await refreshDocuments();
        if (!active) return;
        setWorkspaceState("ready");
        const route = readRoute();
        const documentExists = list.some((document) => document.documentId === route.documentId);
        if (documentExists) {
          await openDocument(route.documentId, route.conversationId, { replace: true });
        } else if (route.documentId) {
          writeRoute(null, null, true);
        }
      } catch {
        if (!active) return;
        setWorkspaceState("error");
      } finally {
        if (active) setDocumentsLoading(false);
      }
    })();
    return () => { active = false; };
  }, [openDocument, refreshDocuments]);

  useEffect(() => {
    const handlePopState = () => {
      const route = readRoute();
      if (!route.documentId) {
        setSelectedDocumentId(null);
        setActiveConversationId(null);
        setConversations([]);
        setMessages([]);
        return;
      }
      openDocument(route.documentId, route.conversationId, { replace: true });
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [openDocument]);

  useEffect(() => {
    if (!selectedDocument || !["PROCESSING", "UPLOADING"].includes(selectedDocument.documentStatus)) return undefined;
    let cancelled = false;
    const poll = async () => {
      try {
        const status = await api.getDocumentStatus(selectedDocument.documentId);
        if (cancelled) return;
        setDocuments((current) => current.map((document) => document.documentId === status.documentId
          ? { ...document, documentStatus: status.documentStatus }
          : document));
        if (status.documentStatus === "FAILED") {
          setNotice(`Processing failed for ${selectedDocument.originalFilename}. You can remove it and try again.`);
        }
      } catch (error) {
        if (!cancelled) setNotice(error.message || "Could not refresh document status.");
      }
    };
    const timer = window.setInterval(poll, 2200);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [selectedDocument]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, sending]);

  useEffect(() => {
    setSampleQuestionIndex(0);
  }, [selectedDocumentId]);

  useEffect(() => {
    if (!activeSampleQuestions.length || messages.length > 0 || chatLoading) return undefined;
    const timer = window.setInterval(() => {
      setSampleQuestionIndex((current) => (current + 1) % activeSampleQuestions.length);
    }, 4600);
    return () => window.clearInterval(timer);
  }, [activeSampleQuestions, chatLoading, messages.length]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(""), 6000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    const handleShortcut = (event) => {
      if (!(event.metaKey || event.ctrlKey) || event.altKey) return;
      if (event.key.toLowerCase() === "k") {
        event.preventDefault();
        startNewChatRef.current?.();
      } else if (event.key.toLowerCase() === "f") {
        event.preventDefault();
        setSidebarCollapsed(false);
        setSidebarOpen(true);
        window.setTimeout(() => searchRef.current?.focus(), 0);
      }
    };
    const handleEscape = (event) => {
      if (event.key === "Escape") setSidebarOpen(false);
    };
    window.addEventListener("keydown", handleShortcut);
    window.addEventListener("keydown", handleEscape);
    return () => {
      window.removeEventListener("keydown", handleShortcut);
      window.removeEventListener("keydown", handleEscape);
    };
  }, []);

  const filteredDocuments = useMemo(() => {
    const value = sidebarSearch.trim().toLowerCase();
    const matchingDocuments = value
      ? documents.filter((document) => document.originalFilename.toLowerCase().includes(value))
      : documents;
    return matchingDocuments.slice()
      .sort((left, right) => showcasePriority(left) - showcasePriority(right));
  }, [documents, sidebarSearch]);

  async function startNewChat() {
    if (!selectedDocument) {
      setSelectedDocumentId(null);
      setActiveConversationId(null);
      setMessages([]);
      setConversations([]);
      writeRoute(null, null);
      return;
    }
    await openDocument(selectedDocument.documentId);
    inputRef.current?.focus();
  }
  startNewChatRef.current = startNewChat;

  async function toggleShowcaseDocument(documentId) {
    if (sending) return;
    if (Number(selectedDocumentId) === documentId) {
      setSelectedDocumentId(null);
      setActiveConversationId(null);
      setConversations([]);
      setMessages([]);
      setChatLoading(false);
      writeRoute(null, null);
      return;
    }
    await openDocument(documentId, null, { resumeLatest: true });
  }

  async function selectDocument(documentId) {
    await openDocument(documentId, null, { resumeLatest: true });
  }

  async function openHomepageShowcase(documentId) {
    const showcaseDocument = showcaseDocuments.find((document) => Number(document.documentId) === documentId);
    if (!showcaseDocument) {
      const name = SHOWCASE_DOCUMENT_PRESENTATION[documentId].headerTitle;
      setNotice(`${name} is currently unavailable. Please try again later.`);
      return;
    }
    await selectDocument(showcaseDocument.documentId);
  }

  async function downloadDemoDocument() {
    if (downloadingDemo) return;
    setDownloadingDemo(true);
    try {
      await api.downloadDocument(DEMO_DOCUMENT_ID);
    } catch (error) {
      setNotice(error.message || "Could not download the demo PDF.");
    } finally {
      setDownloadingDemo(false);
    }
  }

  async function handleUpload(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setNotice("Choose a PDF file to upload.");
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      setNotice("This file is larger than the 20 MB upload limit.");
      return;
    }

    setUploading(true);
    setNotice("");
    try {
      const result = await api.uploadDocument(file);
      const newDocument = {
        documentId: result.documentId,
        originalFilename: file.name,
        contentType: "application/pdf",
        fileSize: file.size,
        documentStatus: result.documentStatus,
        uploadedAt: new Date().toISOString(),
        publicDocument: false
      };
      setDocuments((current) => [newDocument, ...current.filter((doc) => doc.documentId !== result.documentId)]);
      await openDocument(result.documentId);
      if (result.documentStatus === "READY") setNotice("Your PDF is ready. Ask it anything.");
    } catch (error) {
      setNotice(error.message || "Upload failed. Please try again.");
    } finally {
      setUploading(false);
    }
  }

  async function handleDelete(document, event) {
    event.stopPropagation();
    if (!window.confirm(`Delete “${document.originalFilename}” and its saved conversations?`)) return;
    try {
      await api.deleteDocument(document.documentId);
      const remaining = documents.filter((item) => item.documentId !== document.documentId);
      setDocuments(remaining);
      setNotice("Document deleted.");
      if (selectedDocumentId === document.documentId) {
        setSelectedDocumentId(null);
        setActiveConversationId(null);
        setConversations([]);
        setMessages([]);
        writeRoute(null, null);
      }
    } catch (error) {
      setNotice(error.message || "Could not delete this document.");
    }
  }

  async function chooseConversation(conversationId) {
    if (!selectedDocument) return;
    await openDocument(selectedDocument.documentId, conversationId);
  }

  async function sendQuestion(rawQuestion = question) {
    const text = rawQuestion.trim();
    if (!text || !selectedDocument || selectedDocument.documentStatus !== "READY" || sending) return;
    setQuestion("");
    setSending(true);
    const userMessage = { id: crypto.randomUUID(), role: "user", content: text, sources: [] };
    const assistantId = crypto.randomUUID();
    setMessages((current) => [...current, userMessage, {
      id: assistantId, role: "assistant", content: "", sources: [], isStreaming: true, status: "Searching your document…"
    }]);

    const controller = new AbortController();
    streamControllerRef.current = controller;
    let completed = false;
    try {
      for await (const event of api.streamQuestion(
        selectedDocument.documentId, text, activeConversationId, controller.signal)) {
        if (event.type === "status") {
          setMessages((current) => current.map((message) => message.id === assistantId
            ? { ...message, status: event.message || "Working…" }
            : message));
        } else if (event.type === "start") {
          setMessages((current) => current.map((message) => message.id === assistantId
            ? { ...message, sources: event.sources || [], status: "Writing an answer…" }
            : message));
        } else if (event.type === "delta") {
          setMessages((current) => current.map((message) => message.id === assistantId
            ? { ...message, content: message.content + (event.delta || ""), status: "" }
            : message));
        } else if (event.type === "complete") {
          completed = true;
          setMessages((current) => current.map((message) => message.id === assistantId
            ? { ...message, content: event.answer || message.content, sources: event.sources || message.sources, isStreaming: false, status: "" }
            : message));
          if (!activeConversationId && event.conversationId) {
            setActiveConversationId(event.conversationId);
            writeRoute(selectedDocument.documentId, event.conversationId);
          }
          const list = await api.getConversations(selectedDocument.documentId).catch(() => null);
          if (list) setConversations(list);
        } else if (event.type === "error") {
          throw new Error(event.message || "The answer could not be completed.");
        }
      }
      if (!completed && !controller.signal.aborted) {
        throw new Error("The stream ended before the answer was completed. Please retry.");
      }
      if (controller.signal.aborted) {
        setMessages((current) => current.map((message) => message.id === assistantId
          ? { ...message, isStreaming: false, status: "Stopped. This turn was not saved." }
          : message));
      }
    } catch (error) {
      if (error.name === "AbortError") {
        setMessages((current) => current.map((message) => message.id === assistantId
          ? { ...message, isStreaming: false, status: "Stopped. This turn was not saved." }
          : message));
      } else {
        setMessages((current) => current.map((message) => message.id === assistantId
          ? { ...message, isStreaming: false, status: "", error: error.message || "Could not get an answer." }
          : message));
      }
    } finally {
      streamControllerRef.current = null;
      setSending(false);
      inputRef.current?.focus();
    }
  }

  function stopAnswer() {
    streamControllerRef.current?.abort();
  }

  const activeIsReady = selectedDocument?.documentStatus === "READY";
  const selectedConversation = conversations.find((item) => item.conversationId === activeConversationId);

  return (
    <div className={`app-shell ${sidebarOpen ? "sidebar-open" : ""} ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setSidebarOpen(false)} />
      <aside className="sidebar">
        <div className="sidebar-topline">
          <a className="brand" href="/" onClick={(event) => { event.preventDefault(); startNewChat(); }}>
            <span className="brand-mark"><Icon name="spark" size={19} /></span>
            <span>docmind<span className="brand-period">.</span></span>
          </a>
          <button className="icon-button sidebar-close" aria-label="Close sidebar" onClick={() => setSidebarOpen(false)}>
            <Icon name="panel" />
          </button>
        </div>

        <button className="new-chat-button" onClick={startNewChat}>
          <Icon name="plus" size={18} /> <span>New chat</span><kbd>⌘ K</kbd>
        </button>

        <button className="upload-button" onClick={() => fileRef.current?.click()} disabled={uploading}>
          {uploading ? <span className="button-spinner" /> : <Icon name="upload" size={17} />}
          <span>{uploading ? "Uploading PDF…" : "Upload a PDF"}</span>
        </button>
        <input ref={fileRef} className="visually-hidden" type="file" accept="application/pdf,.pdf" onChange={handleUpload} />

        <div className="sidebar-search">
          <Icon name="search" size={16} />
          <input ref={searchRef} value={sidebarSearch} onChange={(event) => setSidebarSearch(event.target.value)} placeholder="Find a document" />
          <kbd>⌘ F</kbd>
        </div>

        <div className="sidebar-scroll">
          <div className="nav-section-heading"><span>Your library</span><span className="count-pill">{documents.length}</span></div>
          {documentsLoading ? <div className="sidebar-loading"><span className="button-spinner" /> Loading documents</div> : null}
          {!documentsLoading && filteredDocuments.length === 0 ? (
            <div className="sidebar-empty">{documents.length ? "No matching PDFs" : "Your PDFs will appear here"}</div>
          ) : null}
          <div className="document-list">
            {filteredDocuments.map((document) => (
              <div key={document.documentId} className={`document-row ${selectedDocumentId === document.documentId ? "is-selected" : ""}`}>
                <button className="document-select" onClick={() => selectDocument(document.documentId)}>
                  <span className={`document-icon ${document.publicDocument ? "is-public" : ""}`}><Icon name={document.publicDocument ? "book" : "file"} size={16} /></span>
                  <span className="document-labels">
                    <span className="document-name">{document.publicDocument ? showcasePresentation(document).title : document.originalFilename}</span>
                    <span className="document-subline">
                      {document.publicDocument ? (Number(document.documentId) === DEMO_DOCUMENT_ID ? "Demo guide" : Number(document.documentId) === PROFILE_DOCUMENT_ID ? "Prasad’s profile" : "Showcase") : "Private"}
                      {document.documentStatus !== "READY" ? <><span className="subline-separator">·</span>{document.documentStatus === "FAILED" ? "Failed" : document.documentStatus === "PROCESSING" && Number.isFinite(new Date(document.uploadedAt).getTime()) && Date.now() - new Date(document.uploadedAt).getTime() >= DOCUMENT_PROCESSING_TIMEOUT_MS ? "Delayed" : "Processing"}</> : null}
                    </span>
                  </span>
                  {(document.documentStatus === "PROCESSING" || document.documentStatus === "UPLOADING") && !(Number.isFinite(new Date(document.uploadedAt).getTime()) && Date.now() - new Date(document.uploadedAt).getTime() >= DOCUMENT_PROCESSING_TIMEOUT_MS)
                    ? <span className="mini-spinner" /> : null}
                </button>
                {!document.publicDocument ? (
                  <button className="row-action" aria-label={`Delete ${document.originalFilename}`} title="Delete document" onClick={(event) => handleDelete(document, event)}>
                    <Icon name="trash" size={15} />
                  </button>
                ) : null}
              </div>
            ))}
          </div>

          {selectedDocument ? (
            <div className="history-section">
              <div className="nav-section-heading history-heading"><span>Recent chats</span></div>
              {conversations.length === 0 ? <div className="sidebar-empty compact">No saved chats yet</div> : null}
              {conversations.map((conversation) => (
                <button key={conversation.conversationId}
                  className={`history-row ${activeConversationId === conversation.conversationId ? "is-active" : ""}`}
                  onClick={() => chooseConversation(conversation.conversationId)}>
                  <Icon name="chat" size={15} />
                  <span>Conversation {conversation.conversationId}</span>
                  <time>{timeLabel(conversation.updatedAt)}</time>
                </button>
              ))}
            </div>
          ) : null}
        </div>

        <div className="sidebar-footer">
          <div className="privacy-mark"><span className="privacy-icon"><Icon name="lock" size={15} /></span><span><strong>Private by design</strong><small>Your uploads stay in this workspace</small></span></div>
          <div className="workspace-indicator"><span className={`workspace-dot ${workspaceState}`} />{workspaceState === "ready" ? "Workspace ready" : workspaceState === "loading" ? "Connecting…" : "Connection needed"}</div>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-left">
            <button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setSidebarOpen(true)}><Icon name="menu" /></button>
            <button className="icon-button desktop-panel-toggle" aria-label="Toggle sidebar" onClick={() => setSidebarCollapsed((value) => !value)}><Icon name="panel" /></button>
            {selectedDocument ? (
              <div className="active-document-topbar">
                <span className="topbar-doc-icon"><Icon name={selectedDocument.publicDocument ? "book" : "file"} size={16} /></span>
                <span className="topbar-doc-name">
                  {(selectedDocument.publicDocument && activeShowcasePresentation?.headerTitle) || selectedDocument.originalFilename}
                </span>
                <StatusBadge status={selectedDocument.documentStatus} timedOut={selectedDocumentProcessingTimedOut} />
                {Number(selectedDocument.documentId) === DEMO_DOCUMENT_ID && selectedDocument.publicDocument ? (
                  <button className="demo-download-button" onClick={downloadDemoDocument} disabled={downloadingDemo}
                    aria-label="Download the How to Plan a Trip demo PDF" title="Download the demo PDF to read it">
                    <Icon name="download" size={15} />
                    <span>{downloadingDemo ? "Preparing…" : "Download demo PDF"}</span>
                  </button>
                ) : null}
              </div>
            ) : <div className="topbar-title">Document chat</div>}
          </div>
          <div className="topbar-right"><ProfileLinks /></div>
        </header>

        {workspaceState === "error" ? (
          <div className="connection-banner">
            <span role="status">We couldn’t connect to DocMind. Please try again in a moment.</span>
            <button type="button" onClick={() => window.location.reload()}>Retry</button>
          </div>
        ) : null}

        {!selectedDocument ? (
          <section className="welcome-view">
            <div className="welcome-content">
              {/*<div className="welcome-orb"><Icon name="spark" size={26} /></div>*/}
              <div className="eyebrow"><span className="eyebrow-line" /> YOUR DOCUMENTS, IN CONVERSATION <span className="eyebrow-line" /></div>
              <h1 className="welcome-heading">
                <span className="welcome-heading-intro">Would you like to ask questions about</span>{" "}
                <span className="welcome-heading-topics">
                  <span className="your-document">your document</span> or <span className="your-prasad">Prasad</span>
                </span>
              </h1>
              <div className="welcome-actions">
                <button type="button" className="welcome-action" onClick={() => fileRef.current?.click()} disabled={uploading}>
                  <Icon name="upload" size={18} />
                  <span>{uploading ? "Uploading…" : "Upload a PDF"}</span>
                  <span className="welcome-action-description">up to 20 MB</span>
                </button>
                {HOMEPAGE_SHOWCASE_IDS.map((documentId) => {
                  const { title } = SHOWCASE_DOCUMENT_PRESENTATION[documentId];
                  const isProfile = documentId === PROFILE_DOCUMENT_ID;
                  return (
                    <button type="button" key={documentId}
                      className={`welcome-action ${isProfile ? "welcome-action-profile" : "welcome-action-demo"}`}
                      disabled={documentsLoading}
                      onClick={() => openHomepageShowcase(documentId)}>
                      <Icon name={isProfile ? "chat" : "book"} size={18} />
                      <span>{title}</span>
                    </button>
                  );
                })}
              </div>
              <div className="welcome-guidance">
                <div className="welcome-guidance-content">
                  <p className="welcome-description">
                    <strong>DocMind</strong> is an AI assistant for PDFs. Ask questions in your own words and get answers drawn from the document, with page references.
                  </p>
                  <h2>What you can do</h2>
                  <ul>
                    <li><strong>Upload a PDF</strong> and ask questions about its contents.</li>
                    <li><strong>Ask about Prasad’s</strong> background, skills, and projects.</li>
                    <li><strong>Use the demo PDF</strong> if you don’t have one to upload.</li>
                  </ul>
                </div>
                {showcaseDocuments.length === 0 && documents.length > 0 ? (
                  <div className="welcome-library"><Icon name="file" size={16} /> Select a document from your library to start a chat.</div>
                ) : null}
              </div>
            </div>
          </section>
        ) : (
          <>
            <section className={`conversation-view ${messages.length === 0 ? "conversation-empty" : ""}`}>
              {chatLoading ? <div className="chat-loading"><span className="button-spinner" /> Loading conversation…</div> : null}
              {!chatLoading && messages.length === 0 ? (
                <div className="empty-chat">
                  <div className="empty-chat-icon"><Icon name="spark" size={22} /></div>
                  <span className="empty-eyebrow">{selectedDocument.publicDocument ? "PUBLIC SHOWCASE DOCUMENT" : "YOUR PRIVATE DOCUMENT"}</span>
                  {activeShowcasePresentation ? (
                    <h1>{activeShowcasePresentation.chatHeading}<br /><em>{activeShowcasePresentation.chatEmphasis}</em></h1>
                  ) : <h1>Ask anything about<br /><em>{selectedDocument.originalFilename}</em></h1>}
                  {activeIsReady ? <p>{activeShowcasePresentation?.chatDescription || "Answers are grounded in this PDF, with page references to help you explore further."}</p>
                    : selectedDocument.documentStatus === "FAILED" ? <p>This PDF could not be processed. Delete it and upload it again.</p>
                      : <p>Your PDF is being prepared. You can ask questions as soon as it’s ready.</p>}
                  {activeIsReady && activeSampleQuestion ? (
                    <div className="sample-question-rotator" aria-label="Sample questions">
                      <span className="sample-question-label">TRY ASKING</span>
                      <button key={`${selectedDocumentId}-${sampleQuestionIndex}`} className="sample-question-card"
                        onClick={() => sendQuestion(activeSampleQuestion)}>
                        <Icon name="spark" size={17} />
                        <span>{activeSampleQuestion}</span>
                        <Icon name="chevron" size={15} className="suggestion-arrow" />
                      </button>
                    </div>
                  ) : null}
                  {activeIsReady && !activeSampleQuestion ? <div className="suggestion-grid">
                    {suggestions.map((item) => <button key={item.title} className="suggestion-card" onClick={() => sendQuestion(item.prompt)}>
                      <Icon name={item.icon} size={17} /><span>{item.title}</span><Icon name="chevron" size={15} className="suggestion-arrow" />
                    </button>)}
                  </div> : null}
                </div>
              ) : null}

              {!chatLoading && messages.length > 0 ? (
                <div className="message-list">
                  {messages.map((message) => (
                    <article key={message.id} className={`message message-${message.role}`}>
                      {message.role === "user" ? (
                        <div className="user-message-bubble">{message.content}</div>
                      ) : (
                        <>
                          <div className="assistant-avatar"><Icon name="spark" size={16} /></div>
                          <div className="assistant-message-content">
                            {message.content ? <AnswerText text={message.content} /> : null}
                            {message.isStreaming && !message.content ? <div className="thinking-label"><span className="thinking-dots"><i /><i /><i /></span>{message.status || "Thinking…"}</div> : null}
                            {message.isStreaming && message.content && message.status ? <div className="stream-status">{message.status}</div> : null}
                            {message.status && !message.isStreaming ? <div className="message-warning">{message.status}</div> : null}
                            {message.error ? <div className="message-error">{message.error}<button onClick={() => sendQuestion(messages.at(-2)?.content || "")}>Try again</button></div> : null}
                            {message.sources?.length > 0 ? <div className="sources-block">
                              <div className="sources-title"><span className="source-title-line" /> REFERENCED PAGES <span className="source-title-line" /></div>
                              <div className="source-list">
                                {[...new Set(message.sources.map((source) => source.pageNumber).filter((pageNumber) => pageNumber != null))].map((pageNumber) => <span key={pageNumber} className="source-chip">
                                  <Icon name="file" size={13} /><span>Page {pageNumber}</span>
                                </span>)}
                              </div>
                            </div> : null}
                          </div>
                        </>
                      )}
                    </article>
                  ))}
                  <div ref={bottomRef} />
                </div>
              ) : null}
            </section>

            <div className="composer-area">
              {!activeIsReady && selectedDocument.documentStatus !== "FAILED" && !selectedDocumentProcessingTimedOut ? (
                <div className="processing-note"><span className="mini-spinner" /> Preparing your document. This usually takes a little while.</div>
              ) : null}
              {selectedDocumentProcessingTimedOut && selectedDocument.documentStatus !== "FAILED" ? (
                <div className="processing-note failed-note">This document is taking longer than 2 minutes to process. We’ll keep checking; if it fails, delete it and upload it again.</div>
              ) : null}
              {selectedDocument.documentStatus === "FAILED" ? (
                <div className="processing-note failed-note">This document failed to process. Try uploading it again.</div>
              ) : null}
              <form className={`composer ${!activeIsReady ? "composer-disabled" : ""}`} onSubmit={(event) => { event.preventDefault(); sendQuestion(); }}>
                <textarea ref={inputRef} value={question} onChange={(event) => setQuestion(event.target.value)}
                  onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); sendQuestion(); } }}
                  placeholder={activeIsReady ? "Ask a question about this document…" : "Your chat will be ready when processing finishes"}
                  disabled={!activeIsReady || sending} rows={1} aria-label="Ask a question" />
                <div className="composer-bottom">
                  <div className="composer-footer-controls">
                    <div className="source-toggle-group" aria-label="Choose a showcase document">
                      <button type="button" className={`source-toggle ${activeShowcaseDocumentId === DEMO_DOCUMENT_ID ? "is-selected" : ""}`}
                        aria-pressed={activeShowcaseDocumentId === DEMO_DOCUMENT_ID} disabled={sending}
                        data-tooltip="You can dowload demo pdf to get context. Example questions - What is a simple way to plan a trip? What is a simple three-day trip itinerary?"
                        onClick={() => toggleShowcaseDocument(DEMO_DOCUMENT_ID)}>Use demo pdf</button>
                      <button type="button" className={`source-toggle ${activeShowcaseDocumentId === PROFILE_DOCUMENT_ID ? "is-selected" : ""}`}
                        aria-pressed={activeShowcaseDocumentId === PROFILE_DOCUMENT_ID} disabled={sending}
                        data-tooltip="Ask about Prasad’s background, skills, projects, and career goals. Example questions- Tell me about you? Tell your skills? Explain your projects?"
                        onClick={() => toggleShowcaseDocument(PROFILE_DOCUMENT_ID)}>Ask about Prasad</button>
                    </div>
                  </div>
                  {sending ? <button type="button" className="send-button stop-button" onClick={stopAnswer} aria-label="Stop generating"><Icon name="stop" size={16} /></button>
                    : <button type="submit" className="send-button" disabled={!question.trim() || !activeIsReady} aria-label="Send question"><Icon name="send" size={16} /></button>}
                </div>
              </form>
              <div className="composer-disclaimer">DocMind can make mistakes. Check important details against the cited pages.</div>
            </div>
          </>
        )}
      </main>

      {notice ? <div className="toast" role="status"><span className="toast-mark"><Icon name="spark" size={15} /></span><span>{notice}</span><button aria-label="Dismiss" onClick={() => setNotice("")}><Icon name="close" size={16} /></button></div> : null}
    </div>
  );
}
