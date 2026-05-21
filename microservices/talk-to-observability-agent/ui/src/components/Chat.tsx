import { FormEvent, useRef, useState } from "react";
import { investigate } from "../api";
import type { ChatMessage } from "../types";
import AssistantMessage from "./AssistantMessage";

const SUGGESTIONS = [
  "Why is the ecommerce service slow in the last 15 minutes?",
  "Find reason for slowness for correlation id 7c3af190-6587-436c-a1df-d8b2f9d9ec4e",
  "Show error patterns for ecommerce-service",
];

function newId() {
  return crypto.randomUUID();
}

export default function Chat() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: newId(),
      role: "assistant",
      response: {
        investigationId: "welcome",
        correlationId: "welcome",
        summary:
          "Hi — I can investigate latency, errors, and resource usage using logs and metrics from your observability stack. Ask a question or pick a suggestion below.",
        probableRootCause: "—",
        evidence: [
          "Tip: paste a correlation ID from the traffic script for request-level tracing.",
        ],
      },
    },
  ]);
  const [query, setQuery] = useState("");
  const [correlationId, setCorrelationId] = useState("");
  const [loading, setLoading] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    requestAnimationFrame(() => {
      listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
    });
  };

  const send = async (text: string, corrId?: string) => {
    const trimmed = text.trim();
    if (!trimmed || loading) return;

    const userMessage: ChatMessage = {
      id: newId(),
      role: "user",
      text: trimmed,
      correlationId: corrId?.trim() || undefined,
    };
    setMessages((prev) => [...prev, userMessage]);
    setQuery("");
    setLoading(true);
    scrollToBottom();

    try {
      const response = await investigate({
        query: trimmed,
        correlationId: corrId?.trim() || undefined,
      });
      setMessages((prev) => [
        ...prev,
        { id: newId(), role: "assistant", response },
      ]);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Investigation failed";
      setMessages((prev) => [...prev, { id: newId(), role: "error", text: message }]);
    } finally {
      setLoading(false);
      scrollToBottom();
    }
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    void send(query, correlationId);
  };

  return (
    <div className="chat">
      <div className="messages" ref={listRef}>
        {messages.map((msg) => {
          if (msg.role === "user") {
            return (
              <div key={msg.id} className="bubble-row user">
                <div className="bubble user-bubble">
                  <p>{msg.text}</p>
                  {msg.correlationId && (
                    <span className="meta">Correlation: {msg.correlationId}</span>
                  )}
                </div>
              </div>
            );
          }
          if (msg.role === "error") {
            return (
              <div key={msg.id} className="bubble-row assistant">
                <div className="bubble error-bubble">{msg.text}</div>
              </div>
            );
          }
          return (
            <div key={msg.id} className="bubble-row assistant">
              <AssistantMessage response={msg.response} />
            </div>
          );
        })}
        {loading && (
          <div className="bubble-row assistant">
            <div className="bubble assistant-bubble loading-bubble">
              <span className="dot" />
              <span className="dot" />
              <span className="dot" />
              <span>Investigating logs and metrics…</span>
            </div>
          </div>
        )}
      </div>

      {messages.length <= 1 && (
        <div className="suggestions">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              className="suggestion"
              disabled={loading}
              onClick={() => void send(s)}
            >
              {s}
            </button>
          ))}
        </div>
      )}

      <form className="composer" onSubmit={onSubmit}>
        <label className="corr-field">
          <span>Correlation ID (optional)</span>
          <input
            type="text"
            placeholder="e.g. 7c3af190-6587-436c-a1df-d8b2f9d9ec4e"
            value={correlationId}
            onChange={(e) => setCorrelationId(e.target.value)}
            disabled={loading}
          />
        </label>
        <div className="composer-row">
          <textarea
            rows={2}
            placeholder="Ask about slowness, errors, heap, or a specific request…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            disabled={loading}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send(query, correlationId);
              }
            }}
          />
          <button type="submit" disabled={loading || !query.trim()}>
            Send
          </button>
        </div>
      </form>
    </div>
  );
}
