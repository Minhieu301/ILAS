import React, { useEffect, useState } from "react";
import axios from "axios";
import ModeratorWorkspace from "../../components/moderator/ModeratorWorkspace";

const API_BASE = "http://localhost:8080/api/moderator/diff";

function formatDateTime(value) {
  if (!value) {
    return "--";
  }
  const text = String(value);
  return text.length >= 10 ? text.slice(0, 10) : text;
}

function statusConfig(status) {
  if (status === "ADDED") {
    return { color: "#0F6E56", bg: "#E1F5EE", label: "ADDED", border: "#0F6E56" };
  }
  if (status === "DELETED") {
    return { color: "#993C1D", bg: "#FAECE7", label: "DELETED", border: "#993C1D" };
  }
  if (status === "MODIFIED") {
    return { color: "#854F0B", bg: "#FAEEDA", label: "MODIFIED", border: "#854F0B" };
  }
  return { color: "#666", bg: "#F3F4F6", label: status || "UNKNOWN", border: "#D1D5DB" };
}

function lineMeta(type) {
  if (type === "INSERT") {
    return { sign: "+", color: "#0F6E56", bg: "#e6ffed" };
  }
  if (type === "DELETE") {
    return { sign: "−", color: "#993C1D", bg: "#ffeef0" };
  }
  return { sign: " ", color: "#ccc", bg: "#fff" };
}

function DiffResult({ diff }) {
  const [openArticles, setOpenArticles] = useState({});

  useEffect(() => {
    setOpenArticles({});
  }, [diff]);

  if (!diff) {
    return null;
  }

  const modifiedCount = (diff.articleDiffs || []).filter((item) => item.diffStatus === "MODIFIED").length;

  const toggleArticle = (articleNumber) => {
    setOpenArticles((prev) => ({
      ...prev,
      [articleNumber]: !prev[articleNumber],
    }));
  };

  return (
    <section style={styles.resultSection}>
      <div style={styles.summaryCard}>
        <div>
          <div style={styles.kicker}>Tổng quan</div>
          <h2 style={styles.resultTitle}>{diff.lawTitle || "Không rõ tên luật"}</h2>
          <p style={styles.resultSubline}>{diff.lawCode || "Không có mã luật"}</p>
          <p style={styles.versionLine}>
            Phiên bản v{diff.oldVersion} → v{diff.newVersion}
          </p>
          <p style={styles.changeNote}>{diff.changeNote || "Không có ghi chú thay đổi"}</p>
        </div>

        <div style={styles.metricsGrid}>
          <MetricCard label="Thêm" value={diff.totalAdded || 0} color="#0F6E56" bg="#E1F5EE" />
          <MetricCard label="Xóa" value={diff.totalDeleted || 0} color="#993C1D" bg="#FAECE7" />
          <MetricCard label="Sửa" value={modifiedCount} color="#854F0B" bg="#FAEEDA" />
          <MetricCard label="Giữ nguyên" value={diff.totalEqual || 0} color="#5B6472" bg="#F3F4F6" />
        </div>
      </div>

      <div style={styles.articleList}>
        {(diff.articleDiffs || []).length === 0 ? (
          <div style={styles.emptyState}>Không có thay đổi giữa hai phiên bản.</div>
        ) : (
          diff.articleDiffs.map((article) => {
            const meta = statusConfig(article.diffStatus);
            const isOpen = Boolean(openArticles[article.articleNumber]);

            return (
              <article
                key={`${article.articleNumber}-${article.diffStatus}`}
                style={{ ...styles.articleCard, borderLeft: `3px solid ${meta.border}` }}
              >
                <button
                  type="button"
                  onClick={() => toggleArticle(article.articleNumber)}
                  style={styles.articleHeader}
                >
                  <div style={styles.headerLeft}>
                    <span style={{ ...styles.badge, color: meta.color, backgroundColor: meta.bg }}>
                      {meta.label}
                    </span>
                    <div style={styles.headerText}>
                      <strong style={styles.articleHeading}>Điều {article.articleNumber || "--"}</strong>
                      <span style={styles.articleTitle}>{article.articleTitle || "Không có tiêu đề"}</span>
                    </div>
                  </div>

                  <span style={styles.arrow}>{isOpen ? "▲" : "▼"}</span>
                </button>

                {isOpen ? (
                  <div style={styles.diffBody}>
                    {(article.lines || []).map((line, index) => {
                      const metaLine = lineMeta(line.type);
                      return (
                        <div key={`${article.articleNumber}-${index}`} style={{ ...styles.diffRow, backgroundColor: metaLine.bg }}>
                          <div style={{ ...styles.diffSign, color: metaLine.color }}>{metaLine.sign}</div>
                          <div style={styles.diffLineNumber}>{line.lineNumber}</div>
                          <div style={styles.diffContent}>{line.content}</div>
                        </div>
                      );
                    })}
                  </div>
                ) : null}
              </article>
            );
          })
        )}
      </div>
    </section>
  );
}

function MetricCard({ label, value, color, bg }) {
  return (
    <div style={{ ...styles.metricCard, color, backgroundColor: bg }}>
      <strong style={styles.metricValue}>{value}</strong>
      <span style={styles.metricLabel}>{label}</span>
    </div>
  );
}

export default function LawDiffPage() {
  const [lawId, setLawId] = useState("");
  const [history, setHistory] = useState([]);
  const [v1, setV1] = useState("");
  const [v2, setV2] = useState("");
  const [diff, setDiff] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const token = localStorage.getItem("token");

  const authHeaders = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const loadHistory = async () => {
    if (!lawId.trim()) {
      setError("Vui lòng nhập Law ID hợp lệ.");
      return;
    }

    setLoading(true);
    setError("");
    try {
      const response = await axios.get(`${API_BASE}/law/${lawId}/history`, {
        headers: authHeaders,
      });
      const list = Array.isArray(response.data?.data) ? response.data.data : [];
      setHistory(list);
      setDiff(null);

      if (list.length >= 2) {
        setV2(String(list[0].versionNumber ?? ""));
        setV1(String(list[1].versionNumber ?? ""));
      } else {
        setV1("");
        setV2("");
      }
    } catch (e) {
      setHistory([]);
      setDiff(null);
      setError(e?.response?.data?.message || "Không thể tải lịch sử phiên bản.");
    } finally {
      setLoading(false);
    }
  };

  const loadDiff = async () => {
    if (!lawId.trim()) {
      setError("Vui lòng nhập Law ID hợp lệ.");
      return;
    }

    setLoading(true);
    setError("");
    try {
      const hasSelectedVersions = v1.trim() && v2.trim();
      const url = hasSelectedVersions
        ? `${API_BASE}/law/${lawId}/compare`
        : `${API_BASE}/law/${lawId}`;

      const response = await axios.get(url, {
        headers: authHeaders,
        params: hasSelectedVersions ? { v1, v2 } : undefined,
      });

      setDiff(response.data?.data || null);
    } catch (e) {
      setDiff(null);
      setError(e?.response?.data?.message || "Không thể tải dữ liệu so sánh.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <ModeratorWorkspace
      active="diff"
      title="So sánh phiên bản văn bản luật"
      description="Chọn một văn bản luật, tải lịch sử phiên bản và xem diff giữa hai bản snapshot gần nhất hoặc bất kỳ cặp phiên bản nào."
    >
      <section style={styles.pageShell}>
        <div style={styles.controlCard}>
          <div style={styles.controlRow}>
            <label style={styles.fieldGroup}>
              <span style={styles.fieldLabel}>Law ID</span>
              <input
                type="number"
                min="1"
                value={lawId}
                onChange={(event) => setLawId(event.target.value)}
                placeholder="Nhập ID văn bản"
                style={styles.input}
              />
            </label>

            <button type="button" onClick={loadHistory} style={styles.primaryButton}>
              Tải lịch sử
            </button>
          </div>

          {history.length >= 2 ? (
            <div style={styles.compareRow}>
              <label style={styles.fieldGroup}>
                <span style={styles.fieldLabel}>Phiên bản cũ hơn</span>
                <select value={v1} onChange={(event) => setV1(event.target.value)} style={styles.select}>
                  {history.slice().reverse().map((item) => (
                    <option key={`v1-${item.versionId}`} value={item.versionNumber}>
                      v{item.versionNumber} — {formatDateTime(item.createdAt)}
                    </option>
                  ))}
                </select>
              </label>

              <label style={styles.fieldGroup}>
                <span style={styles.fieldLabel}>Phiên bản mới hơn</span>
                <select value={v2} onChange={(event) => setV2(event.target.value)} style={styles.select}>
                  {history.map((item) => (
                    <option key={`v2-${item.versionId}`} value={item.versionNumber}>
                      v{item.versionNumber} — {formatDateTime(item.createdAt)}
                    </option>
                  ))}
                </select>
              </label>

              <button type="button" onClick={loadDiff} style={styles.secondaryButton}>
                So sánh
              </button>
            </div>
          ) : null}

          {error ? <div style={styles.errorBox}>{error}</div> : null}
          {loading ? <div style={styles.loadingBox}>Đang tính toán diff...</div> : null}
        </div>

        {diff ? <DiffResult diff={diff} /> : null}
      </section>
    </ModeratorWorkspace>
  );
}

const styles = {
  pageShell: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  controlCard: {
    background: "#fff",
    border: "1px solid #E6E8EC",
    borderRadius: "18px",
    padding: "20px",
    boxShadow: "0 10px 30px rgba(16, 24, 40, 0.06)",
  },
  controlRow: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
    gap: "12px",
    alignItems: "end",
  },
  compareRow: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
    gap: "12px",
    alignItems: "end",
    marginTop: "14px",
  },
  fieldGroup: {
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  fieldLabel: {
    fontSize: "13px",
    fontWeight: 600,
    color: "#475467",
  },
  input: {
    border: "1px solid #D0D5DD",
    borderRadius: "12px",
    padding: "12px 14px",
    fontSize: "14px",
    outline: "none",
    background: "#fff",
  },
  select: {
    border: "1px solid #D0D5DD",
    borderRadius: "12px",
    padding: "12px 14px",
    fontSize: "14px",
    outline: "none",
    background: "#fff",
  },
  primaryButton: {
    border: "none",
    borderRadius: "12px",
    padding: "12px 18px",
    background: "linear-gradient(135deg, #0F6E56, #0B5B47)",
    color: "#fff",
    fontWeight: 700,
    cursor: "pointer",
  },
  secondaryButton: {
    border: "none",
    borderRadius: "12px",
    padding: "12px 18px",
    background: "#111827",
    color: "#fff",
    fontWeight: 700,
    cursor: "pointer",
  },
  errorBox: {
    marginTop: "14px",
    color: "#993C1D",
    background: "#FAECE7",
    border: "1px solid #F3C5B7",
    borderRadius: "12px",
    padding: "12px 14px",
    fontWeight: 600,
  },
  loadingBox: {
    marginTop: "14px",
    color: "#0F172A",
    background: "#F8FAFC",
    border: "1px solid #E2E8F0",
    borderRadius: "12px",
    padding: "12px 14px",
    fontWeight: 600,
  },
  resultSection: {
    display: "flex",
    flexDirection: "column",
    gap: "16px",
  },
  summaryCard: {
    background: "linear-gradient(180deg, #FFFFFF 0%, #FBFCFD 100%)",
    border: "1px solid #E6E8EC",
    borderRadius: "18px",
    padding: "22px",
    boxShadow: "0 10px 28px rgba(16, 24, 40, 0.05)",
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
    gap: "18px",
  },
  kicker: {
    fontSize: "12px",
    letterSpacing: "0.08em",
    textTransform: "uppercase",
    color: "#667085",
    fontWeight: 700,
    marginBottom: "8px",
  },
  resultTitle: {
    margin: 0,
    fontSize: "26px",
    lineHeight: 1.2,
    color: "#101828",
  },
  resultSubline: {
    margin: "8px 0 0",
    color: "#667085",
    fontWeight: 600,
  },
  versionLine: {
    margin: "10px 0 0",
    color: "#0F6E56",
    fontWeight: 700,
  },
  changeNote: {
    margin: "8px 0 0",
    color: "#475467",
  },
  metricsGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))",
    gap: "12px",
    alignSelf: "start",
  },
  metricCard: {
    borderRadius: "14px",
    padding: "16px",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
    minHeight: "92px",
  },
  metricValue: {
    fontSize: "28px",
    lineHeight: 1,
  },
  metricLabel: {
    fontSize: "13px",
    fontWeight: 700,
  },
  articleList: {
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  articleCard: {
    background: "#fff",
    border: "1px solid #E6E8EC",
    borderRadius: "16px",
    overflow: "hidden",
    boxShadow: "0 10px 26px rgba(16, 24, 40, 0.04)",
  },
  articleHeader: {
    width: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: "16px",
    padding: "14px 16px",
    border: "none",
    background: "transparent",
    cursor: "pointer",
    textAlign: "left",
    flexWrap: "wrap",
  },
  headerLeft: {
    display: "flex",
    alignItems: "center",
    gap: "12px",
    minWidth: 0,
    flex: 1,
  },
  badge: {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    padding: "5px 10px",
    borderRadius: "999px",
    fontSize: "12px",
    fontWeight: 800,
    flexShrink: 0,
  },
  headerText: {
    display: "flex",
    flexDirection: "column",
    gap: "4px",
    minWidth: 0,
  },
  articleHeading: {
    fontSize: "15px",
    color: "#101828",
  },
  articleTitle: {
    color: "#667085",
    fontSize: "13px",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  arrow: {
    color: "#667085",
    fontSize: "14px",
    flexShrink: 0,
  },
  diffBody: {
    maxHeight: "400px",
    overflowY: "auto",
    borderTop: "1px solid #EEF2F6",
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace",
    fontSize: "12px",
  },
  diffRow: {
    display: "flex",
    alignItems: "flex-start",
    minHeight: "28px",
    borderBottom: "1px solid #F2F4F7",
  },
  diffSign: {
    width: "28px",
    textAlign: "center",
    paddingTop: "6px",
    borderRight: "0.5px solid #eee",
    userSelect: "none",
    flexShrink: 0,
    fontWeight: 700,
  },
  diffLineNumber: {
    width: "36px",
    textAlign: "right",
    padding: "6px 8px 6px 0",
    borderRight: "0.5px solid #eee",
    color: "#A3A3A3",
    userSelect: "none",
    flexShrink: 0,
  },
  diffContent: {
    flex: 1,
    padding: "6px 12px",
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
    color: "#101828",
  },
  emptyState: {
    border: "1px dashed #D0D5DD",
    borderRadius: "14px",
    padding: "20px",
    textAlign: "center",
    color: "#667085",
    background: "#fff",
  },
};