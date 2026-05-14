import React, { useState, useEffect } from "react";
import {
  Save,
  RotateCcw,
  MessageSquare,
  Database,
  Power,
  BarChart3,
  Shield,
  Settings,
  AlertCircle,
  CheckCircle,
} from "lucide-react";
import api from "../../api/api";
import "../../styles/admin/chatbot.css";

export default function Chatbot() {
  // =====================
  // STATE
  // =====================
  const [settings, setSettings] = useState({
    enabled: true,
    welcomeMessage: "Xin chào! Tôi có thể giúp gì cho bạn?",
    responseDelay: 500,
    maxHistory: 50,
  });

  const [laws, setLaws] = useState([]);
  const [selectedLaws, setSelectedLaws] = useState({});

  const [limits, setLimits] = useState({
    queriesPerUser: 50,
    maxTokenLength: 2048,
  });

  const [alerts, setAlerts] = useState({
    systemDowntime: true,
    highErrorRate: true,
    securityBreach: true,
  });

  const [stats, setStats] = useState({
    totalConversations: 0,
    successfulResponses: 0,
    failedResponses: 0,
    averageResponseTime: "N/A",
  });

  const [chatHistory, setChatHistory] = useState([]);
  const [rebuilding, setRebuilding] = useState(false);
  const [savingToggle, setSavingToggle] = useState(false);
  const [chatbotRunning, setChatbotRunning] = useState(false);

  // 🔽 TOGGLE HISTORY
  const [showHistory, setShowHistory] = useState(false);

  // =====================
  // LOAD LAWS
  // =====================
  useEffect(() => {
    api.get("/chatbot/admin/laws")
      .then((res) => {
        const lawsData = res.data || [];
        setLaws(lawsData);
        const selected = {};
        lawsData.forEach((law, idx) => {
          selected[law.lawId] = idx === 0;
        });
        setSelectedLaws(selected);
      })
      .catch((err) => console.error("Load laws failed", err));
  }, []);

  // =====================
  // LOAD SETTINGS
  // =====================
  useEffect(() => {
    api.get("/chatbot/admin/settings")
      .then((res) => {
        const data = res.data;
        if (data && typeof data === "object") {
          setSettings((prev) => ({
            ...prev,
            ...data,
            responseDelay: data.responseDelay ?? prev.responseDelay ?? 500,
            maxHistory: data.maxHistory ?? prev.maxHistory ?? 50,
          }));
        }
      })
      .catch((err) => console.error("Load settings failed", err));
  }, []);

  // =====================
  // LOAD STATS
  // =====================
  useEffect(() => {
    api.get("/chatbot/admin/stats")
      .then((res) => {
        const data = res.data;
        if (data && typeof data === "object") {
          setStats(data);
        }
      })
      .catch((err) => console.error("Load stats failed", err));
  }, []);

  // =====================
  // LOAD CHAT LOGS
  // =====================
  useEffect(() => {
    api.get("/chatbot/admin/logs")
      .then((res) => {
        const data = res.data;
        if (Array.isArray(data)) {
          setChatHistory(data);
        }
      })
      .catch((err) => console.error("Load logs failed", err));
  }, []);

  // =====================
  // LOAD CHATBOT STATUS
  // =====================
  useEffect(() => {
    api.get("/admin/ai/status")
      .then((res) => setChatbotRunning(res.data?.isRunning || false))
      .catch((err) => {
        console.error("Load chatbot status failed", err);
        setChatbotRunning(false);
      });
  }, []);

  // =====================
  // TOGGLE ENABLE + START/STOP PROCESS
  // =====================
  const toggleChatbot = async () => {
    const newValue = !settings.enabled;
    setSavingToggle(true);

    setSettings((prev) => ({ ...prev, enabled: newValue }));

    try {
      // 1. Save settings to DB
      await api.post("/chatbot/admin/settings", { ...settings, enabled: newValue });

      // 2. Start or stop AI process
      if (newValue) {
        const res = await api.post("/admin/ai/start");
        if (res.status === 200) {
          setChatbotRunning(true);
        } else {
          alert("❌ " + (res.data?.message || "Không thể khởi động Chatbot AI"));
          setSettings((prev) => ({ ...prev, enabled: false }));
        }
      } else {
        const res = await api.delete("/admin/ai/stop");
        if (res.status === 200) {
          setChatbotRunning(false);
        } else {
          alert("❌ " + (res.data?.message || "Không thể dừng Chatbot AI"));
          setSettings((prev) => ({ ...prev, enabled: true }));
        }
      }
    } catch (err) {
      alert("Lỗi: " + (err.response?.data?.message || err.message));
      setSettings((prev) => ({ ...prev, enabled: !newValue }));
    } finally {
      setSavingToggle(false);
    }
  };

  // =====================
  // SAVE SETTINGS
  // =====================
  const handleSave = async () => {
    try {
      // Save settings + selected laws
      const payload = {
        ...settings,
        selectedLawIds: laws
          .filter((law) => selectedLaws[law.lawId])
          .map((law) => law.lawId),
      };
      await api.post("/chatbot/admin/settings", payload);
      alert("Đã lưu cấu hình chatbot!");
    } catch (err) {
      alert("Lỗi khi lưu: " + (err.response?.data?.message || err.message));
    }
  };

  // =====================
  // RESET
  // =====================
  const handleReset = () => {
    if (!window.confirm("Đặt lại toàn bộ cấu hình?")) return;

    setSettings({
      enabled: true,
      welcomeMessage: "Xin chào! Tôi có thể giúp gì cho bạn?",
      responseDelay: 500,
      maxHistory: 50,
      dataSource: "all",
      temperature: 0.7,
      maxTokens: 500,
    });
  };

  // =====================
  // REBUILD AI
  // =====================
  const handleRebuild = async () => {
    if (!window.confirm("Rebuild toàn bộ AI Engine? Thao tác này có thể mất vài phút.")) return;

    setRebuilding(true);
    try {
      const res = await api.post("/admin/ai/rebuild");
      alert(res.data?.message || "Đã kích hoạt rebuild AI!");
    } catch (err) {
      alert("Lỗi khi rebuild AI: " + (err.response?.data?.message || err.message));
    } finally {
      setRebuilding(false);
    }
  };



  // =====================
  // RENDER
  // =====================
  return (
    <div className="chatbot-container">
      {/* HEADER */}
      <header className="chatbot-page-header">
        <div>
          <p className="chatbot-kicker">Trợ lý pháp lý AI</p>
          <h1>Cấu hình Chatbot</h1>
          <p>Quản lý cấu hình, tham số và theo dõi hiệu suất hệ thống AI</p>
        </div>
        <div className="chatbot-header-status">
          <span className={`status-pill ${settings.enabled ? "online" : "offline"}`}>
            <Power size={14} />
            {settings.enabled ? "Đang hoạt động" : "Đang tạm dừng"}
          </span>
        </div>
      </header>

      {/* QUICK METRICS */}
      <div className="chatbot-status">
        <StatusBox
          label="Tổng hội thoại"
          value={stats.totalConversations}
          tone="primary"
          icon={<MessageSquare size={18} />}
        />
        <StatusBox
          label="Thành công"
          value={stats.successfulResponses}
          tone="success"
          icon={<CheckCircle size={18} />}
        />
        <StatusBox
          label="Lỗi"
          value={stats.failedResponses}
          tone="danger"
          icon={<AlertCircle size={18} />}
        />
        <StatusBox
          label="TG phản hồi TB"
          value={stats.averageResponseTime}
          tone="info"
          icon={<BarChart3 size={18} />}
        />
      </div>

      {/* POWER CONTROL */}
      <div className="chatbot-section power-section">
        <div className="power-control">
          <div>
            <h3 className="power-title">Trạng thái hệ thống</h3>
            <p className="power-desc">Bật hoặc tắt trợ lý AI</p>
          </div>
          <button
            className={`power-toggle ${settings.enabled ? "on" : "off"}`}
            onClick={toggleChatbot}
            disabled={savingToggle}
          >
            <Power size={18} />
            <span>{settings.enabled ? "Bật" : "Tắt"}</span>
          </button>
        </div>
        {savingToggle && <div className="power-saving">Đang cập nhật...</div>}
      </div>

      {/* GENERAL SETTINGS */}
      <div className="chatbot-section">
        <h3 className="chatbot-section-title">
          <Settings size={18} /> Cài đặt chung
        </h3>
        <p className="chatbot-section-desc">Tùy chỉnh các cài đặt cơ bản</p>

        <div className="chatbot-settings">
          <div className="setting-item setting-item-full">
            <label className="setting-label">Tin nhắn chào mừng</label>
            <input
              className="setting-input"
              placeholder="Nhập tin nhắn..."
              value={settings.welcomeMessage || ""}
              onChange={(e) =>
                setSettings({ ...settings, welcomeMessage: e.target.value })
              }
            />
          </div>

          <div className="setting-group">
            <Input
              label="Độ trễ phản hồi (ms)"
              value={settings.responseDelay}
              onChange={(v) => setSettings({ ...settings, responseDelay: v })}
            />
            <Input
              label="Lịch sử tối đa"
              value={settings.maxHistory}
              onChange={(v) => setSettings({ ...settings, maxHistory: v })}
            />
          </div>
        </div>
      </div>

      {/* DOMAIN CONFIGURATION */}
      <div className="chatbot-section">
        <h3 className="chatbot-section-title">
          <Shield size={18} /> Phạm vi lĩnh vực
        </h3>
        <p className="chatbot-section-desc">Chọn những lĩnh vực pháp lý mà chatbot có thể tư vấn</p>

        <div className="domains-grid">
          {laws.length === 0 ? (
            <p className="no-laws-msg">Đang tải danh sách luật...</p>
          ) : (
            laws.map((law) => (
              <DomainToggle
                key={law.lawId}
                label={law.title}
                enabled={selectedLaws[law.lawId] || false}
                onChange={(v) =>
                  setSelectedLaws({ ...selectedLaws, [law.lawId]: v })
                }
              />
            ))
          )}
        </div>
      </div>

      {/* SYSTEM LIMITS */}
      <div className="chatbot-section">
        <h3 className="chatbot-section-title">
          <BarChart3 size={18} /> Giới hạn hệ thống
        </h3>
        <p className="chatbot-section-desc">Cấu hình các ràng buộc về lưu lượng và khả năng xử lý</p>

        <div className="limits-grid">
          <LimitBox
            label="Truy vấn / người dùng / ngày"
            value={limits.queriesPerUser}
            onChange={(v) => setLimits({ ...limits, queriesPerUser: v })}
            hint="Khuyến nghị: 25-100"
          />
          <LimitBox
            label="Độ dài token tối đa"
            value={limits.maxTokenLength}
            onChange={(v) => setLimits({ ...limits, maxTokenLength: v })}
            hint="Giới hạn cho một phản hồi"
          />
        </div>

        <button className="apply-limits-btn" onClick={() => alert("Đã áp dụng giới hạn mới!")}>
          Áp dụng giới hạn
        </button>
      </div>

      {/* ALERTS & MONITORING */}
      <div className="chatbot-section">
        <h3 className="chatbot-section-title">
          <AlertCircle size={18} /> Cảnh báo & Giám sát
        </h3>
        <p className="chatbot-section-desc">Quản lý những loại cảnh báo hệ thống nên gửi</p>

        <div className="alerts-list">
          <AlertRow
            label="Cảnh báo ngừng hoạt động"
            desc="Thông báo qua Email và Slack khi dịch vụ gặp sự cố"
            checked={alerts.systemDowntime}
            onChange={(v) => setAlerts({ ...alerts, systemDowntime: v })}
          />
          <AlertRow
            label="Cảnh báo tỉ lệ lỗi cao"
            desc="Cảnh báo khi tỷ lệ lỗi vượt quá 5% trong 5 phút"
            checked={alerts.highErrorRate}
            onChange={(v) => setAlerts({ ...alerts, highErrorRate: v })}
          />
          <AlertRow
            label="Cảnh báo vi phạm bảo mật"
            desc="Phản ứng ngay lập tức cho truy cập trái phép"
            checked={alerts.securityBreach}
            onChange={(v) => setAlerts({ ...alerts, securityBreach: v })}
          />
        </div>
      </div>

      {/* ACTION BUTTONS */}
      <div className="chatbot-actions">
        <button className="action-btn save" onClick={handleSave}>
          <Save size={16} /> Lưu cấu hình
        </button>

        <button className="action-btn reset" onClick={handleReset}>
          <RotateCcw size={16} /> Đặt lại
        </button>

        <button
          className="action-btn rebuild"
          onClick={handleRebuild}
          disabled={rebuilding}
        >
          {rebuilding ? "Đang rebuild..." : "Rebuild AI Engine"}
        </button>
      </div>

      {/* CHAT HISTORY */}
      <div className="chatbot-section chatbot-history-section">
        <div
          className="chatbot-history-head"
          onClick={() => setShowHistory(!showHistory)}
        >
          <span className="chatbot-history-title">
            <MessageSquare size={18} />
            Lịch sử hội thoại
          </span>

          <button type="button" className="chatbot-history-toggle">
            {showHistory ? "Ẩn" : "Xem"}
          </button>
        </div>

        {showHistory && (
          <div className="chatbot-history-body">
            {chatHistory.length === 0 ? (
              <div className="chatbot-history-empty">
                Chưa có dữ liệu hội thoại
              </div>
            ) : (
              <div className="chatbot-history-list">
                {chatHistory.map((chat) => (
                  <div
                    key={chat.id}
                    className={`chat-log-item ${chat.status === "success" ? "success" : "failed"}`}
                  >
                    <div className="chat-log-header">
                      <strong>{chat.user}</strong>
                      <div className="chat-log-meta">
                        <span>{chat.timestamp}</span>
                        <span className={`chat-log-state ${chat.status === "success" ? "ok" : "error"}`}>
                          {chat.status === "success" ? "✓ Thành công" : "✗ Thất bại"}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/* ===== Helper components ===== */

function StatusBox({ label, value, tone = "primary", icon }) {
  return (
    <div className={`status-box tone-${tone}`}>
      <div className="status-box-head">
        <div className="status-label">{label}</div>
        <span className="status-icon" aria-hidden="true">
          {icon}
        </span>
      </div>
      <div className="status-value">
        {value}
      </div>
      <div className="status-caption">Cập nhật gần nhất: vừa xong</div>
    </div>
  );
}

function Input({ label, value, onChange }) {
  return (
    <div className="setting-item">
      <label className="setting-label">{label}</label>
      <input
        type="number"
        className="setting-input"
        value={value ?? 0}
        onChange={(e) => onChange(Number(e.target.value) || 0)}
      />
    </div>
  );
}

function DomainToggle({ label, enabled, onChange }) {
  return (
    <div className="domain-toggle-card">
      <div className="domain-label">{label}</div>
      <button
        className={`domain-toggle ${enabled ? "enabled" : "disabled"}`}
        onClick={() => onChange(!enabled)}
      >
        <span className="toggle-circle" />
      </button>
    </div>
  );
}

function LimitBox({ label, value, onChange, hint }) {
  return (
    <div className="limit-box">
      <label className="limit-label">{label}</label>
      <input
        type="number"
        className="limit-input"
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <p className="limit-hint">{hint}</p>
    </div>
  );
}

function AlertRow({ label, desc, checked, onChange }) {
  return (
    <div className="alert-row">
      <div className="alert-content">
        <div className="alert-label">{label}</div>
        <div className="alert-desc">{desc}</div>
      </div>
      <input
        type="checkbox"
        className="alert-checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
    </div>
  );
}
