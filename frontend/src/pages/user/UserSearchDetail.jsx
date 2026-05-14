import React, { useState, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import UserSidebar from "../../components/user/UserSidebar";
import { useAuth } from "../../contexts/AuthContext";
import { lawAPI } from "../../api/law";
import { feedbackAPI } from "../../api/feedback";
import { sendChatMessage } from "../../api/chatbotAPI";
import "../../styles/user/UserSearchDetail.css";

const UserSearchDetail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lawData, setLawData] = useState(null);
  const [articleData, setArticleData] = useState(null);
  const [relatedArticles, setRelatedArticles] = useState([]);
  const [simplifiedArticle, setSimplifiedArticle] = useState(null);

  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [feedbackContent, setFeedbackContent] = useState("");
  const [feedbackType, setFeedbackType] = useState("content_error");
  const [feedbackPriority, setFeedbackPriority] = useState("normal");
  const [sendToModerator, setSendToModerator] = useState(true);
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState("");
  const [chatInput, setChatInput] = useState("");
  const [chatLoading, setChatLoading] = useState(false);
  const [chatHistoryLocal, setChatHistoryLocal] = useState([]);

  const id = searchParams.get("id");
  const type = searchParams.get("type");

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!id || id === "undefined" || !type) {
      setError("Thiếu hoặc sai thông tin ID/loại dữ liệu");
      setLoading(false);
      return;
    }
    loadData();
  }, [id, type]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError("");

      // ====================== LAW ============================
      if (type === "law") {
        const lawResponse = await lawAPI.getLawById(id);

        if (!lawResponse.success || !lawResponse.data) {
          setError("Không tìm thấy thông tin luật");
          setLoading(false);
          return;
        }

        setLawData(lawResponse.data);

        const articlesResponse = await lawAPI.getArticlesByLawId(id);
        if (articlesResponse.success && articlesResponse.data) {
          setRelatedArticles(articlesResponse.data.slice(0, 5));
        }
      }

      // ===================== ARTICLE ============================
      else if (type === "article") {
        const articleResponse = await lawAPI.getArticleById(id);

        if (!articleResponse.success || !articleResponse.data) {
          setError("Không tìm thấy thông tin điều luật");
          setLoading(false);
          return;
        }

        setArticleData(articleResponse.data);

        const lawResponse = await lawAPI.getLawById(articleResponse.data.lawId);
        if (lawResponse.success) {
          setLawData(lawResponse.data);
        }

        const articlesResponse = await lawAPI.getArticlesByLawId(articleResponse.data.lawId);
        if (articlesResponse.success && Array.isArray(articlesResponse.data)) {
          setRelatedArticles(
            articlesResponse.data
              .filter((a) => a.articleId !== parseInt(id))
              .slice(0, 5)
          );
        }

        const simplifiedResponse = await lawAPI.getSimplifiedArticle(id);
        if (simplifiedResponse.success && simplifiedResponse.data) {
          setSimplifiedArticle(simplifiedResponse.data);
        }
      }
    } catch (error) {
      console.error("Error loading data:", error);
      const errorMessage =
        error?.message ||
        error?.error ||
        "Không thể tải dữ liệu. Vui lòng thử lại sau.";
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // ===================== FORMAT ===========================
  const formatDate = (dateString) =>
    dateString ? new Date(dateString).toLocaleDateString("vi-VN") : "";

  const formatContent = (content) => {
    if (!content) return "";
    const lines = content.split("\n").filter((line) => line.trim());
    return lines.map((line, i) =>
      /^\d+\./.test(line.trim()) ? (
        <div key={i} className="userdetail-content-item">
          {line}
        </div>
      ) : (
        <div key={i} className="userdetail-content-text">
          {line}
        </div>
      )
    );
  };

  // ===================== FEEDBACK ===========================
  const handleOpenFeedback = () => {
    setShowFeedbackModal(true);
    setFeedbackContent("");
    setFeedbackType("content_error");
    setFeedbackPriority("normal");
    setSendToModerator(true);
    setFeedbackMessage("");
  };

  const handleCloseFeedback = () => {
    setShowFeedbackModal(false);
    setFeedbackContent("");
    setFeedbackType("content_error");
    setFeedbackPriority("normal");
    setSendToModerator(true);
    setFeedbackMessage("");
  };

  const handleSubmitFeedback = async (e) => {
    e.preventDefault();

    if (!feedbackContent.trim()) {
      setFeedbackMessage("Vui lòng nhập nội dung phản hồi");
      return;
    }

    setFeedbackSubmitting(true);
    setFeedbackMessage("");

    try {
      let userId = user?.userId || parseInt(localStorage.getItem("userId"));

      const feedbackTypeMap = {
        content_error: "Sai hoặc thiếu nội dung",
        outdated: "Thông tin chưa cập nhật",
        unclear: "Diễn đạt chưa rõ",
        technical: "Lỗi hiển thị / kỹ thuật",
        other: "Khác",
      };

      const feedbackPriorityMap = {
        low: "Ít ảnh hưởng",
        normal: "Ảnh hưởng vừa",
        high: "Ảnh hưởng cao",
        urgent: "Ảnh hưởng nghiêm trọng",
      };

      const targetLabel = type === "article" ? "điều luật" : "văn bản";
      const targetId = parseInt(id);
      const decoratedContent = [
        sendToModerator ? "[GỬI MODERATOR]" : "[PHẢN HỒI CHUNG]",
        `[Loại sai sót] ${feedbackTypeMap[feedbackType] || "Khác"}`,
        `[Mức độ ưu tiên] ${feedbackPriorityMap[feedbackPriority] || "Trung bình"}`,
        `[Đối tượng] ${targetLabel} #${targetId}`,
        "",
        feedbackContent.trim(),
      ].join("\n");

      const feedbackData = {
        content: decoratedContent,
        userId,
      };

      if (type === "law") feedbackData.lawId = parseInt(id);
      if (type === "article") {
        feedbackData.articleId = parseInt(id);
        if (articleData?.lawId) feedbackData.lawId = articleData.lawId;
      }

      const response = await feedbackAPI.createFeedback(feedbackData);

      if (response.success) {
        setFeedbackMessage("Gửi phản hồi thành công! Cảm ơn bạn đã đóng góp.");
        setFeedbackContent("");
        setTimeout(() => handleCloseFeedback(), 2000);
      } else {
        setFeedbackMessage(response.error || "Không thể gửi phản hồi.");
      }
    } catch (err) {
      console.error("Error submitting feedback:", err);
      setFeedbackMessage("Có lỗi xảy ra. Vui lòng thử lại sau.");
    } finally {
      setFeedbackSubmitting(false);
    }
  };
 
  // ===================== CHATBOT ===========================
  const sendChat = async () => {
    const question = chatInput?.trim();
    if (!question) return;
    const uid = user?.userId || parseInt(localStorage.getItem("userId")) || null;
    // append user message immediately
    setChatHistoryLocal((h) => [...h, { sender: "user", text: question }]);
    setChatInput("");
    setChatLoading(true);
    try {
      // Read any existing conversationId (nullable) and send to backend; backend will return a UUID to persist
      let conversationId = sessionStorage.getItem("chat_conversation_id") || null;
      const data = await sendChatMessage(uid, question, true, conversationId);
      const answer = data?.answer || "Rất tiếc, hệ thống không trả về nội dung.";
      // If backend returned conversationId (UUID), persist it for future turns
      if (data?.conversationId) {
        sessionStorage.setItem("chat_conversation_id", data.conversationId);
      }
      setChatHistoryLocal((h) => [...h, { sender: "bot", text: answer, sources: data?.sources }]);
    } catch (err) {
      console.error("Chatbot ask failed", err);
      setChatHistoryLocal((h) => [...h, { sender: "bot", text: "Có lỗi khi kết nối tới chatbot." }]);
    } finally {
      setChatLoading(false);
    }
  };

  const handleChatKey = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendChat();
    }
  };

  // ===================== LOADING ===========================
  if (loading) {
    return (
      <div className="userdetail-page">
        <div className="userdetail-loading">
          <div className="loading-spinner"></div>
          <p>Đang tải dữ liệu...</p>
        </div>
      </div>
    );
  }

  // ===================== ERROR ===========================
  if (error) {
    return (
      <div className="userdetail-page">
        <div className="userdetail-error">
          <h3>Lỗi</h3>
          <p>{error}</p>
          <button
            className="userdetail-back-btn"
            onClick={() => navigate("/search")}
          >
            Quay lại tìm kiếm
          </button>
        </div>
      </div>
    );
  }

  // ===================== RENDER ===========================
  const title = type === "article" ? articleData?.articleTitle : lawData?.title;
  const subtitle =
    type === "article"
      ? `${lawData?.title} (Hiệu lực: ${formatDate(lawData?.effectiveDate)})`
      : `${lawData?.lawType} - ${lawData?.code} (Hiệu lực: ${formatDate(
          lawData?.effectiveDate
        )})`;

  return (
    <div className="userdetail-page">
      <UserSidebar active="search" />

      <main className="userdetail-main">
        <section className="userdetail-workspace">
          <div className="userdetail-center">
            <div className="userdetail-searchbox">
              <input placeholder="Ask a legal question..." readOnly />
              <button type="button" onClick={() => navigate("/search")}>➔</button>
            </div>

            <div className="userdetail-meta-row">
              <div>
                <strong>Original Legal Text</strong>
                <span>{subtitle}</span>
              </div>
              <div>
                <strong>AI-Generated Summary</strong>
                <span className="tag">Friendly Explanation</span>
              </div>
            </div>

            <div className="userdetail-panels">
              <article className="panel original">
                <h3>{title}</h3>
                {type === "article" ? (
                  <div className="userdetail-content-body">{formatContent(articleData?.content)}</div>
                ) : (
                  <div className="userdetail-law-info">
                    <p><strong>Loại văn bản:</strong> {lawData?.lawType}</p>
                    <p><strong>Số hiệu:</strong> {lawData?.code}</p>
                    <p><strong>Ngày ban hành:</strong> {formatDate(lawData?.issuedDate)}</p>
                    <p><strong>Ngày hiệu lực:</strong> {formatDate(lawData?.effectiveDate)}</p>
                  </div>
                )}

                {relatedArticles.length > 0 && (
                  <ul className="userdetail-related-list">
                    {relatedArticles.map((article) => (
                      <li key={article.articleId}>
                        <button
                          className="userdetail-related-link"
                          onClick={() => navigate(`/user/search/detail?id=${article.articleId}&type=article`)}
                        >
                          {article.articleTitle}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </article>

              <article className="panel summary">
                <div className="summary-header">
                  <h3>Bản luật rút gọn</h3>
                </div>

                <div className="userdetail-content-body">
                  {type === "article" && simplifiedArticle
                    ? formatContent(simplifiedArticle.contentSimplified)
                    : <p>Chưa có bản giải thích cho điều này.</p>}
                </div>

                <div className="userdetail-actions">
                  <button className="userdetail-feedback-btn" onClick={handleOpenFeedback}>
                    Gửi phản hồi
                  </button>
                </div>
              </article>
            </div>
          </div>

          <aside className="userdetail-chat-column">
            <div className="chat-column-header">Legal Assistant</div>
            <div className="chat-column-body">
              {chatHistoryLocal.map((m, idx) => (
                <div key={idx} className={`chat-msg ${m.sender}`}>
                  <div className="chat-msg-text">{m.text}</div>
                </div>
              ))}
              {chatHistoryLocal.length === 0 && (
                <div className="chat-msg bot">
                  <div className="chat-msg-text">Xin chào, tôi có thể giúp gì cho bạn.</div>
                </div>
              )}
            </div>

            <div className="userdetail-chatbot-form">
              <textarea
                className="userdetail-chatbot-input"
                placeholder="Type a message..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyDown={handleChatKey}
                rows={1}
              />
              <button
                className="userdetail-chatbot-send"
                onClick={sendChat}
                disabled={chatLoading}
              >
                {chatLoading ? "..." : "➤"}
              </button>
            </div>
          </aside>
        </section>

        {/* Feedback Modal */}
        {showFeedbackModal && (
          <div
            className="userdetail-feedback-modal-overlay"
            onClick={handleCloseFeedback}
          >
            <div
              className="userdetail-feedback-modal"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="userdetail-feedback-modal-header">
                <h3>💬 Gửi phản hồi</h3>
                <button
                  className="userdetail-feedback-modal-close"
                  onClick={handleCloseFeedback}
                >
                  ×
                </button>
              </div>

              <div className="userdetail-feedback-modal-body">
                <p className="userdetail-feedback-modal-desc">
                  Bạn muốn góp ý gì về{" "}
                  {type === "article" ? "điều luật" : "văn bản"} này? Hãy mô tả ngắn gọn và rõ ý.
                </p>

                <div className="userdetail-feedback-extra">
                  <div className="userdetail-feedback-row">
                    <div className="userdetail-feedback-field">
                      <label>Nhóm vấn đề</label>
                      <select
                        className="userdetail-feedback-select"
                        value={feedbackType}
                        onChange={(e) => setFeedbackType(e.target.value)}
                      >
                        <option value="content_error">Sai hoặc thiếu nội dung</option>
                        <option value="outdated">Thông tin chưa cập nhật</option>
                        <option value="unclear">Diễn đạt chưa rõ</option>
                        <option value="technical">Lỗi hiển thị / kỹ thuật</option>
                        <option value="other">Khác</option>
                      </select>
                    </div>

                    <div className="userdetail-feedback-field">
                      <label>Mức độ ảnh hưởng</label>
                      <select
                        className="userdetail-feedback-select"
                        value={feedbackPriority}
                        onChange={(e) => setFeedbackPriority(e.target.value)}
                      >
                        <option value="low">Ít ảnh hưởng</option>
                        <option value="normal">Ảnh hưởng vừa</option>
                        <option value="high">Ảnh hưởng cao</option>
                        <option value="urgent">Ảnh hưởng nghiêm trọng</option>
                      </select>
                    </div>
                  </div>

                  <label className="userdetail-feedback-moderator-check">
                    <input
                      type="checkbox"
                      checked={sendToModerator}
                      onChange={(e) => setSendToModerator(e.target.checked)}
                    />
                    <span>Chuyển góp ý này đến moderator để kiểm tra và cập nhật nếu cần</span>
                  </label>
                </div>

                <form onSubmit={handleSubmitFeedback}>
                  <textarea
                    className="userdetail-feedback-textarea"
                    placeholder="Nhập nội dung phản hồi..."
                    rows={6}
                    maxLength={5000}
                    value={feedbackContent}
                    onChange={(e) => setFeedbackContent(e.target.value)}
                  />

                  <div className="userdetail-feedback-char-count">
                    {feedbackContent.length}/5000 ký tự
                  </div>

                  {feedbackMessage && (
                    <div
                      className={`userdetail-feedback-message ${
                        feedbackMessage.includes("thành công")
                          ? "success"
                          : "error"
                      }`}
                    >
                      {feedbackMessage}
                    </div>
                  )}

                  <div className="userdetail-feedback-modal-actions">
                    <button
                      type="button"
                      className="userdetail-feedback-cancel-btn"
                      onClick={handleCloseFeedback}
                      disabled={feedbackSubmitting}
                    >
                      Hủy
                    </button>

                    <button
                      type="submit"
                      className="userdetail-feedback-submit-btn"
                      disabled={feedbackSubmitting || !feedbackContent.trim()}
                    >
                      {feedbackSubmitting ? "Đang gửi..." : "Gửi phản hồi"}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default UserSearchDetail;
