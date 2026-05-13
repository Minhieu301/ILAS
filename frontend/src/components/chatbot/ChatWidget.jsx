import React, { useState, useEffect, useRef } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import "../../styles/chatbot/ChatWidget.css";
import { sendChatMessage } from "../../api/chatbotAPI";
import ReactMarkdown from "react-markdown";

export default function ChatWidget() {
  const location = useLocation();
  const navigate = useNavigate();
  const bottomRef = useRef(null);

  const isFullChat = location.pathname.startsWith("/chat/history");

  const [open, setOpen] = useState(
    () => localStorage.getItem("chat_open") === "true"
  );
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState(null);
  const [conversationId, setConversationId] = useState(
    () => sessionStorage.getItem("chat_conversation_id")
  );

  const quickQuestions = [
    "Nghỉ việc cần báo trước bao nhiêu ngày?",
    "Người lao động có bao nhiêu ngày nghỉ lễ?",
    "Điều kiện hưởng trợ cấp thôi việc là gì?",
    "Mức phạt vi phạm hành chính là bao nhiêu?",
    "Hợp đồng thử việc tối đa được bao lâu?",
  ];

  const createConversationId = () =>
    `widget-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const getWelcomeMessages = () => [
    {
      sender: "bot",
      text: "Xin chào! Mình là trợ lý pháp lý ILAS. Bạn có thể hỏi bất kỳ câu hỏi pháp luật nào, mình sẽ trả lời kèm điều luật liên quan.",
    },
  ];

  // =========================
  // PERSIST OPEN STATE
  // =========================
  useEffect(() => {
    localStorage.setItem("chat_open", open);
  }, [open]);

  // =========================
  // LOAD USER ID
  // =========================
  useEffect(() => {
    const id = localStorage.getItem("userId");
    if (id) setUserId(parseInt(id));
  }, []);

  useEffect(() => {
    setMessages(getWelcomeMessages());
  }, []);

  useEffect(() => {
    if (conversationId) {
      sessionStorage.setItem("chat_conversation_id", conversationId);
    } else {
      sessionStorage.removeItem("chat_conversation_id");
    }
  }, [conversationId]);

  // =========================
  // AUTO SCROLL
  // =========================
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, open]);

  // =========================
  // SUGGESTION EXTRACTION
  // =========================
  const extractSuggestions = (text) => {
    if (!text || typeof text !== "string") return [];

    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const suggestions = [];

    const bulletRe = /^[\-\*\u2022]\s*(.+)$/;
    const numberedRe = /^\d+[\.|\)]\s*(.+)$/;

    for (const line of lines) {
      let m = line.match(bulletRe) || line.match(numberedRe);
      if (m && m[1]) {
        suggestions.push(m[1].trim());
      }
    }

    // If no bullets/numbering found, try comma-separated list in first few lines
    if (suggestions.length === 0 && lines.length > 0) {
      for (const line of lines.slice(0, 3)) {
        if ((line.match(/,/g) || []).length >= 1) {
          const parts = line.split(",").map((p) => p.trim()).filter(Boolean);
          if (parts.length >= 2 && parts.length <= 8) {
            return parts;
          }
        }
      }
    }

    return suggestions;
  };

  // =========================
  // SEND MESSAGE
  // =========================
  const sendMessage = async () => {
    if (!input.trim() || loading) return;

    const text = input.trim();
    setInput("");
    setMessages((p) => [...p, { sender: "user", text }]);
    setLoading(true);

    try {
      const activeConversationId = conversationId || createConversationId();
      if (!conversationId) {
        setConversationId(activeConversationId);
      }

      const res = await sendChatMessage(userId, text, true, activeConversationId);
      const botText = res.answer || "";
      const suggestions = extractSuggestions(botText);
      setMessages((p) => [...p, { sender: "bot", text: botText, suggestions }]);
    } catch {
      setMessages((p) => [
        ...p,
        { sender: "bot", text: "❌ Lỗi hệ thống nội bộ. Vui lòng thử lại sau." },
      ]);
    } finally {
      setLoading(false);
    }
  };

  // Send an explicit text (used for suggestion chips)
  const sendMessageWithText = async (overrideText) => {
    if (!overrideText || loading) return;
    setInput("");
    setMessages((p) => [...p, { sender: "user", text: overrideText }]);
    setLoading(true);

    try {
      const activeConversationId = conversationId || createConversationId();
      if (!conversationId) {
        setConversationId(activeConversationId);
      }

      const res = await sendChatMessage(userId, overrideText, true, activeConversationId);
      const botText = res.answer || "";
      const suggestions = extractSuggestions(botText);
      setMessages((p) => [...p, { sender: "bot", text: botText, suggestions }]);
    } catch {
      setMessages((p) => [
        ...p,
        { sender: "bot", text: "❌ Lỗi hệ thống nội bộ. Vui lòng thử lại sau." },
      ]);
    } finally {
      setLoading(false);
    }
  };

  // =========================
  // OPEN FULL CHAT
  // =========================
  const handleExpand = () => {
    setOpen(false);
    navigate("/chat/history");
  };

  const handleToggleWidget = () => {
    setOpen((prev) => !prev);
  };

  const handleQuickQuestion = (question) => {
    setInput(question);
  };

  // =========================
  // RENDER
  // =========================
  if (isFullChat) return null;

  return (
    <>
      {/* CHAT BUBBLE */}
      <button className="chat-bubble" onClick={handleToggleWidget}>
        💬
      </button>

      {/* CHAT WINDOW */}
      <div className={`chat-window ${open ? "open" : ""}`}>
        {/* HEADER */}
        <div className="chat-header">
          <span>AI Legal Assistant</span>

          <div className="header-actions">
            <button className="icon-btn" onClick={handleExpand} title="Phóng to">
              ↗
            </button>
            <button
              className="icon-btn close"
              onClick={() => setOpen(false)}
              title="Đóng"
            >
              ✖
            </button>
          </div>
        </div>

        {/* BODY */}
        <div className="chat-body">
          {messages.length <= 1 && (
            <div className="chat-welcome-card">
              <p className="chat-welcome-title">Bắt đầu cuộc trò chuyện mới</p>
              <p className="chat-welcome-subtitle">
                Chọn nhanh một câu hỏi hoặc nhập nội dung của bạn bên dưới.
              </p>

              <div className="chat-quick-questions">
                {quickQuestions.map((question) => (
                  <button
                    key={question}
                    className="chat-quick-item"
                    onClick={() => handleQuickQuestion(question)}
                    type="button"
                  >
                    {question}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((m, i) => {
            const isError =
              m.sender === "bot" &&
              (m.text?.includes("❌") || m.text?.includes("Lỗi"));

            return (
              <div key={i} className={`message-wrapper ${m.sender}`}>
                <div className="avatar">
                  {m.sender === "user" ? "🧑" : "🤖"}
                </div>

                <div
                  className={`chat-message ${m.sender} ${
                    isError ? "error" : ""
                  }`}
                >
                  {m.sender === "bot" ? (
                    <>
                      <ReactMarkdown>{m.text}</ReactMarkdown>
                      {Array.isArray(m.suggestions) && m.suggestions.length > 0 && (
                        <div className="chat-suggestions">
                          {m.suggestions.map((s, idx) => (
                            <button
                              key={idx}
                              type="button"
                              className="chat-suggestion"
                              onClick={() => sendMessageWithText(s)}
                            >
                              {s}
                            </button>
                          ))}
                        </div>
                      )}
                    </>
                  ) : (
                    m.text
                  )}
                </div>
              </div>
            );
          })}

          {loading && (
            <div className="message-wrapper bot">
              <div className="avatar">🤖</div>
              <div className="chat-message bot">
                <div className="chat-typing" aria-label="AI đang trả lời">
                  <span className="chat-typing-dot" />
                  <span className="chat-typing-dot" />
                  <span className="chat-typing-dot" />
                </div>
              </div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>

        {/* INPUT */}
        <div className="chat-input">
          <input
            value={input}
            placeholder="Nhập câu hỏi..."
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                sendMessage();
              }
            }}
          />
          <button onClick={sendMessage} disabled={loading}>
            Gửi
          </button>
        </div>
      </div>
    </>
  );
}
