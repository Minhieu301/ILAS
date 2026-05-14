import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { ChevronDown, FileText, Gauge, AlertTriangle, NotebookText } from "lucide-react";
import { useNavigate } from "react-router-dom";
import ModeratorWorkspace from "../../components/moderator/ModeratorWorkspace";
import "../../styles/moderator/ContentQualityPage.css";

const API_BASE = "http://localhost:8080/api/moderator/content-quality";

const LEVEL_CONFIG = {
  GOOD: { color: "#0F6E56", bg: "#E1F5EE", label: "Tốt" },
  WARNING: { color: "#854F0B", bg: "#FAEEDA", label: "Cần xem" },
  CRITICAL: { color: "#993C1D", bg: "#FAECE7", label: "Khẩn cấp" },
};

export default function ContentQualityPage() {
  const [data, setData] = useState([]);
  const [filter, setFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState(null);
  const navigate = useNavigate();

  const token = localStorage.getItem("token");

  const authHeaders = useMemo(() => {
    const headers = { "Content-Type": "application/json" };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    return headers;
  }, [token]);

  useEffect(() => {
    let active = true;

    const loadData = async () => {
      setLoading(true);
      try {
        const endpoint = filter === "missing" ? "/missing-simplified" : "";
        const response = await axios.get(`${API_BASE}${endpoint}`, {
          headers: authHeaders,
          validateStatus: () => true,
        });

        const payload = response?.data?.data;
        if (active) {
          setData(Array.isArray(payload) ? payload : []);
          setExpanded(null);
        }
      } catch (error) {
        if (active) {
          setData([]);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    loadData();

    return () => {
      active = false;
    };
  }, [authHeaders, filter]);

  const displayed = useMemo(() => {
    if (filter === "ALL" || filter === "missing") {
      return data;
    }
    return data.filter((item) => item.qualityLevel === filter);
  }, [data, filter]);

  const toggleExpanded = (articleId) => {
    setExpanded((prev) => (prev === articleId ? null : articleId));
  };

  const qualityMeta = (level) => LEVEL_CONFIG[level] || LEVEL_CONFIG.CRITICAL;

  return (
    <ModeratorWorkspace
      active="content-quality"
      title="Content Quality Rating"
      description="Theo dõi mức độ phản hồi trên từng bài viết, xác định nội dung đang có vấn đề và ưu tiên xử lý cho Moderator."
    >
      <section className="moderator-workspace-panel content-quality-page">
        <div className="content-quality-hero">
          <div>
            <p className="content-quality-kicker">Moderator insights</p>
            <h2>Đánh giá chất lượng nội dung theo phản hồi người dùng</h2>
            <p>
              Bảng này tổng hợp độ nóng của từng bài viết dựa trên phản hồi chưa xử lý, số lượng góp ý gần đây và tình trạng bài đơn giản hóa.
            </p>
          </div>
          <div className="content-quality-stats">
            <div className="content-quality-stat-card">
              <Gauge size={18} />
              <span>{displayed.length}</span>
              <small>Bài viết đang hiển thị</small>
            </div>
            <div className="content-quality-stat-card">
              <AlertTriangle size={18} />
              <span>{displayed.filter((item) => item.qualityLevel === "CRITICAL").length}</span>
              <small>Khẩn cấp</small>
            </div>
          </div>
        </div>

        <div className="content-quality-filters" role="tablist" aria-label="Lọc chất lượng nội dung">
          <button type="button" className={filter === "ALL" ? "active" : ""} onClick={() => setFilter("ALL")}>Tất cả</button>
          <button type="button" className={filter === "CRITICAL" ? "active" : ""} onClick={() => setFilter("CRITICAL")}>🔴 Khẩn cấp</button>
          <button type="button" className={filter === "WARNING" ? "active" : ""} onClick={() => setFilter("WARNING")}>🟡 Cần xem</button>
          <button type="button" className={filter === "missing" ? "active" : ""} onClick={() => setFilter("missing")}>📝 Chưa có bài đơn giản hóa</button>
        </div>

        {loading ? (
          <div className="content-quality-empty-state">Đang tải báo cáo chất lượng nội dung...</div>
        ) : displayed.length === 0 ? (
          <div className="content-quality-empty-state">Không có dữ liệu phù hợp với bộ lọc hiện tại.</div>
        ) : (
          <div className="content-quality-list">
            {displayed.map((item) => {
              const meta = qualityMeta(item.qualityLevel);
              const isExpanded = expanded === item.articleId;

              return (
                <article
                  key={item.articleId}
                  className={`content-quality-card ${isExpanded ? "expanded" : ""}`}
                  style={{ borderLeft: `3px solid ${meta.color}` }}
                >
                  <button
                    type="button"
                    className="content-quality-card-header"
                    onClick={() => toggleExpanded(item.articleId)}
                  >
                    <div className="content-quality-header-main">
                      <span className="content-quality-level-badge" style={{ color: meta.color, backgroundColor: meta.bg }}>
                        {item.qualityLevel}
                      </span>
                      <div className="content-quality-title-block">
                        <h3>
                          {item.articleNumber ? `${item.articleNumber} - ` : ""}
                          {item.articleTitle || "Không có tiêu đề"}
                        </h3>
                        <p>{item.lawTitle || "Không rõ điều luật"}</p>
                      </div>
                    </div>

                    <div className="content-quality-header-meta">
                      <div className="content-quality-score">
                        <strong>{item.qualityScore?.toFixed ? item.qualityScore.toFixed(1) : Number(item.qualityScore || 0).toFixed(1)}</strong>
                        <span>Quality score</span>
                      </div>
                      <div className="content-quality-count">
                        <strong>{item.totalFeedbacks}</strong>
                        <span>Phản hồi</span>
                      </div>
                      {!item.hasSimplified ? (
                        <span className="content-quality-missing-badge">Chưa có bài đơn giản</span>
                      ) : null}
                      <ChevronDown size={18} className={`content-quality-chevron ${isExpanded ? "open" : ""}`} />
                    </div>
                  </button>

                  {isExpanded ? (
                    <div className="content-quality-card-body">
                      <div className="content-quality-body-summary">
                        <div>
                          <span>Phản hồi chưa xử lý</span>
                          <strong>{item.unresolvedCount}</strong>
                        </div>
                        <div>
                          <span>Trạng thái bài đơn giản gần nhất</span>
                          <strong>{item.simplifiedStatus || "Chưa có"}</strong>
                        </div>
                      </div>

                      <div className="content-quality-feedback-section">
                        <h4>
                          <NotebookText size={16} />
                          3 phản hồi gần nhất
                        </h4>

                        <div className="content-quality-feedback-grid">
                          {(item.recentFeedbacks || []).length > 0 ? (
                            item.recentFeedbacks.map((feedback, index) => (
                              <div key={`${item.articleId}-feedback-${index}`} className="content-quality-feedback-card">
                                <span>#{index + 1}</span>
                                <p>{feedback}</p>
                              </div>
                            ))
                          ) : (
                            <div className="content-quality-empty-feedback">Chưa có phản hồi.</div>
                          )}
                        </div>
                      </div>

                      <div className="content-quality-actions">
                        <button
                          type="button"
                          className="content-quality-action primary"
                          onClick={() => navigate(`/moderator/simplify?articleId=${item.articleId}`)}
                        >
                          <FileText size={16} />
                          {item.hasSimplified ? "Chỉnh sửa bài đơn giản" : "Tạo bài đơn giản hóa"}
                        </button>

                        <button
                          type="button"
                          className="content-quality-action secondary"
                          onClick={() => navigate(`/moderator/feedback?articleId=${item.articleId}`)}
                        >
                          Xem toàn bộ phản hồi
                        </button>
                      </div>
                    </div>
                  ) : null}
                </article>
              );
            })}
          </div>
        )}
      </section>
    </ModeratorWorkspace>
  );
}