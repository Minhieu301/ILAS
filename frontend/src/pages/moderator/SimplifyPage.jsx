import React, { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import { useSearchParams } from "react-router-dom";
import {
  FiCheckCircle,
  FiChevronDown,
  FiClipboard,
  FiEdit3,
  FiRefreshCw,
  FiSearch,
} from "react-icons/fi";
import ModeratorWorkspace from "../../components/moderator/ModeratorWorkspace";
import Laws from "../admin/Laws";
import "../../styles/moderator/SimplifyPage.css";

export default function SimplifyPage() {
  const [articles, setArticles] = useState([]);
  const [selectedLawId, setSelectedLawId] = useState("");
  const [articleId, setArticleId] = useState("");
  const [articleTitle, setArticleTitle] = useState("");
  const [originalText, setOriginalText] = useState("");
  const [simplifiedText, setSimplifiedText] = useState("");
  const [message, setMessage] = useState("");
  const [moderatorId, setModeratorId] = useState(null);
  const [mySimplified, setMySimplified] = useState([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [currentStatus, setCurrentStatus] = useState(null);
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [activeSection, setActiveSection] = useState("simplify");
  const [pendingQueueCount, setPendingQueueCount] = useState(0);
  const [approvingAll, setApprovingAll] = useState(false);
  const [bulkGenerating, setBulkGenerating] = useState(false);
  const [searchParams] = useSearchParams();

  const API_ARTICLE = "http://localhost:8080/api/articles";
  const API_SIMPLIFIED = "http://localhost:8080/api/moderator/simplified";
  const API_SIMPLIFIED_MANAGEMENT = "http://localhost:8080/api/moderator/simplified-management";
  const API_AI = "http://localhost:8080/api/ai/summarize-law";

  const token = localStorage.getItem("token");
  const authHeader = useMemo(
    () => ({ Authorization: `Bearer ${token}` }),
    [token]
  );

  // Decode JWT
  useEffect(() => {
    if (!token) return;
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      setModeratorId(Number(payload.userId ?? payload.id ?? payload.sub ?? null));
    } catch (e) {
      console.error("[JWT decode error]", e);
    }
  }, [token]);

  // Load danh sách điều luật
  useEffect(() => {
    axios
      .get(API_ARTICLE)
      .then((res) => {
        setArticles(Array.isArray(res.data) ? res.data : []);
      })
      .catch((err) => console.error("Load articles failed", err));
  }, []);

  // Load danh sách bài của Moderator
  const refreshMine = useCallback(async () => {
    if (!moderatorId) return;

    const res = await axios.get(`${API_SIMPLIFIED}/mine/${moderatorId}`, {
      headers: authHeader,
      validateStatus: () => true,
    });
    setMySimplified(Array.isArray(res.data) ? res.data : []);
  }, [API_SIMPLIFIED, authHeader, moderatorId]);

  useEffect(() => {
    refreshMine();
  }, [refreshMine]);

  const refreshPendingQueueCount = useCallback(async () => {
    try {
      const res = await axios.get(API_SIMPLIFIED_MANAGEMENT, {
        headers: authHeader,
        params: { status: "PENDING", page: 0, size: 1 },
        validateStatus: () => true,
      });

      if (res.status === 200) {
        const total = Number(res.data?.data?.totalElements || 0);
        setPendingQueueCount(total);
      } else {
        setPendingQueueCount(0);
      }
    } catch {
      setPendingQueueCount(0);
    }
  }, [API_SIMPLIFIED_MANAGEMENT, authHeader]);

  useEffect(() => {
    refreshPendingQueueCount();
  }, [refreshPendingQueueCount]);

  useEffect(() => {
    const queryArticleId = searchParams.get("articleId");
    if (queryArticleId && queryArticleId !== articleId) {
      setArticleId(queryArticleId);
    }
  }, [articleId, searchParams]);

  // Hàm gọi AI
  const generateAI = useCallback(async () => {
    if (!originalText || !originalText.trim()) return;
    if (!articleTitle) return;

    setMessage("🤖 AI đang tạo bản rút gọn gợi ý...");

    try {
      const res = await axios.post(
        API_AI,
        {
          lawContent: originalText,
          articleTitle,
          articleId,
        },
        { headers: { "Content-Type": "application/json" } }
      );

      setSimplifiedText(res.data.summary || "");
      setMessage("✨ AI đã tạo bản rút gọn — bạn có thể chỉnh sửa!");
    } catch (err) {
      console.error(err);
      const backendMessage =
        err?.response?.data?.message ||
        (typeof err?.response?.data === "string" ? err.response.data : "");
      setMessage(`⚠️ AI không thể sinh bản rút gọn.${backendMessage ? ` ${backendMessage}` : ""}`);
    }
  }, [API_AI, articleId, articleTitle, originalText]);

  // Khi chọn điều luật
  useEffect(() => {
      if (!articleId || !moderatorId) return;

      let active = true;

      const loadContent = async () => {
        setMessage("Đang tải dữ liệu...");
        setSimplifiedText("");
        setCurrentStatus(null);

        try {
          // 1. Bản gốc
          const resOriginal = await axios.get(`${API_ARTICLE}/${articleId}`);
          if (!active) return;

          const art = resOriginal.data || {};
          if (art.lawId) {
            setSelectedLawId(Number(art.lawId));
          }
          setArticleTitle(art.articleTitle || "");
          setOriginalText(art.content || "");

          // 2. Bản của Moderator
          const resMine = await axios.get(
            `${API_SIMPLIFIED}/mine/${moderatorId}`,
            { headers: authHeader }
          );
          if (!active) return;

          const mine = resMine.data || [];
          const match = mine.find(m => Number(m.articleId) === Number(articleId));

          if (match) {
            setSimplifiedText(match.contentSimplified);
            setCurrentStatus(match.status);
            setMessage(`📄 Bản của bạn (${match.status})`);
            return;
          }

          // 3. Bản đã duyệt chung
          const resApproved = await axios.get(
            `${API_SIMPLIFIED}/by-article/${articleId}`,
            { headers: authHeader, validateStatus: () => true }
          );
          if (!active) return;

          if (resApproved.status === 200) {
            const simplified = resApproved.data;
            setSimplifiedText(simplified.contentSimplified);
            setCurrentStatus(simplified.status);
            setMessage(`📄 Bản đã duyệt (${simplified.status})`);
            return;
          }

          // 4. NEW
          setSimplifiedText("");
          setCurrentStatus("NEW");
          setMessage("🆕 Không có bản rút gọn — AI sẽ tạo gợi ý...");

        } catch (err) {
          if (active) setMessage("⚠️ Lỗi tải dữ liệu.");
        }
      };

      loadContent();

      return () => {
        active = false;
      };
  }, [articleId, authHeader, moderatorId]);


  // Gọi AI đúng thời điểm (sau khi dữ liệu đã load xong)
  useEffect(() => {
    if (
      articleId &&
      currentStatus === "NEW" &&
      originalText.trim() !== "" &&
      articleTitle.trim() !== ""
    ) {
      console.log("🔥 CALLING AI FOR:", articleId, articleTitle);
      generateAI();
    }
  }, [articleId, articleTitle, currentStatus, generateAI, originalText]);



  // Submit
  const handleSubmit = async () => {
    if (!articleId || !simplifiedText.trim()) {
      return setMessage("⚠️ Vui lòng chọn điều luật và nhập nội dung!");
    }

    try {
      const payload = {
        articleId: Number(articleId),
        moderatorId: Number(moderatorId),
        category: articleTitle,
        contentSimplified: simplifiedText,
      };

      const res = await axios.post(`${API_SIMPLIFIED}/create`, payload, {
        headers: authHeader,
      });

      if (res.status === 200) {
        setMessage("✅ Đã lưu và duyệt ngay.");
        refreshMine();
        setCurrentStatus("APPROVED");
      }
    } catch (err) {
      setMessage("❌ Lỗi gửi bài");
    }
  };

  const handleReset = () => {
    setSimplifiedText("");
    setMessage("🔄 Đã làm mới.");
    setCurrentStatus("NEW");
  };

  const statusMap = {
    PENDING: "⏳ Chờ duyệt",
    APPROVED: "✅ Đã duyệt",
    REJECTED: "❌ Bị từ chối",
    ARCHIVED: "🗑️ Đã ẩn khỏi user",
    NEW: "🆕 Chưa có",
  };

  const articleStatusMap = {};
  mySimplified.forEach((s) => {
    articleStatusMap[s.articleId] = s.status;
  });

  const lawOptions = useMemo(() => {
    const map = new Map();

    articles.forEach((article) => {
      if (!article?.lawId) return;
      if (!map.has(article.lawId)) {
        map.set(article.lawId, {
          lawId: article.lawId,
          lawTitle: article.lawTitle || `Bộ luật #${article.lawId}`,
        });
      }
    });

    return Array.from(map.values()).sort((a, b) =>
      String(a.lawTitle).localeCompare(String(b.lawTitle), "vi", {
        sensitivity: "base",
      })
    );
  }, [articles]);

  const filteredArticles = useMemo(() => {
    if (!selectedLawId) return articles;
    return articles.filter(
      (article) => Number(article.lawId) === Number(selectedLawId)
    );
  }, [articles, selectedLawId]);

  useEffect(() => {
    if (!articleId) return;

    const selectedArticle = articles.find(
      (item) => Number(item.articleId) === Number(articleId)
    );

    if (selectedArticle?.lawId && Number(selectedLawId) !== Number(selectedArticle.lawId)) {
      setSelectedLawId(Number(selectedArticle.lawId));
    }
  }, [articleId, articles, selectedLawId]);

  useEffect(() => {
    if (!articleId) return;

    const stillAvailable = filteredArticles.some(
      (item) => Number(item.articleId) === Number(articleId)
    );

    if (!stillAvailable) {
      setArticleId("");
      setArticleTitle("");
      setOriginalText("");
      setSimplifiedText("");
      setCurrentStatus(null);
      setMessage("ℹ️ Đã đổi bộ luật, vui lòng chọn điều luật tương ứng.");
    }
  }, [articleId, filteredArticles]);

  const selectedLawTitle = useMemo(() => {
    if (!articleId) return "Chưa chọn bộ luật";

    const selectedArticle = articles.find(
      (item) => Number(item.articleId) === Number(articleId)
    );

    return selectedArticle?.lawTitle?.trim() || "Chưa xác định bộ luật";
  }, [articleId, articles]);

  const filteredList = mySimplified.filter((s) =>
    statusFilter === "ALL" ? true : s.status === statusFilter
  );

  const currentMineItem = useMemo(
    () =>
      mySimplified.find(
        (item) => Number(item.articleId) === Number(articleId)
      ) || null,
    [articleId, mySimplified]
  );

  const originalParagraphs = useMemo(
    () =>
      originalText
        .split(/\n+/)
        .map((item) => item.trim())
        .filter(Boolean),
    [originalText]
  );

  const simplifiedParagraphs = useMemo(() => {
    const lineItems = simplifiedText
      .split(/\n+/)
      .map((item) => item.trim())
      .filter(Boolean);

    if (lineItems.length > 1) return lineItems;

    return (simplifiedText.match(/[^.!?]+[.!?]?/g) || [])
      .map((item) => item.trim())
      .filter(Boolean);
  }, [simplifiedText]);

  const pendingReviewCount = useMemo(
    () => mySimplified.filter((item) => item.status === "PENDING").length,
    [mySimplified]
  );

  const bulkHideCount = useMemo(
    () => mySimplified.filter((item) => item.status !== "ARCHIVED").length,
    [mySimplified]
  );

  const bulkShowCount = useMemo(
    () => mySimplified.filter((item) => item.status === "ARCHIVED").length,
    [mySimplified]
  );

  const bulkGenerateTargets = useMemo(() => {
    const scopedArticles = selectedLawId ? filteredArticles : articles;
    if (!scopedArticles.length) return [];

    const mineByArticleId = new Map(
      mySimplified.map((item) => [Number(item.articleId), item])
    );

    return scopedArticles.filter((article) => {
      const mine = mineByArticleId.get(Number(article.articleId));
      return !mine || !String(mine.contentSimplified || "").trim();
    });
  }, [articles, filteredArticles, mySimplified, selectedLawId]);

  const bulkGenerateCount = bulkGenerateTargets.length;

  const queueLabel = useMemo(() => {
    if (currentStatus === "PENDING") {
      return `Đang chờ ${pendingReviewCount || 1} mục thẩm định`;
    }

    if (currentStatus === "APPROVED") {
      return "Nội dung đã được phê duyệt";
    }

    if (currentStatus === "REJECTED") {
      return "Bản rút gọn cần chỉnh sửa và gửi lại";
    }

    if (currentStatus === "ARCHIVED") {
      return "Bản đã ẩn khỏi user, vẫn có thể chỉnh sửa";
    }

    return "Sẵn sàng gửi duyệt";
  }, [currentStatus, pendingReviewCount]);

  const handleCopySimplified = async () => {
    if (!simplifiedText.trim()) return;

    try {
      await navigator.clipboard.writeText(simplifiedText);
      setMessage("📋 Đã sao chép bản rút gọn.");
    } catch {
      setMessage("⚠️ Không thể sao chép nội dung.");
    }
  };

  const handleRegenerate = () => {
    generateAI();
    setIsEditingDraft(false);
  };

  const handleApproveOne = async (item = currentMineItem) => {
    if (!moderatorId) {
      setMessage("⚠️ Không xác định được tài khoản moderator.");
      return;
    }

    if (!item?.id) {
      setMessage("⚠️ Chưa có bản rút gọn để duyệt.");
      return;
    }

    try {
      await axios.put(`${API_SIMPLIFIED}/${item.id}/approve`, null, {
        headers: authHeader,
        params: { moderatorId },
      });

      await refreshMine();
      await refreshPendingQueueCount();
      if (Number(item.articleId) === Number(articleId)) {
        setCurrentStatus("APPROVED");
      }
      setMessage("✅ Đã duyệt bản rút gọn này.");
    } catch {
      setMessage("❌ Không thể duyệt bản rút gọn.");
    }
  };

  const handleApproveAll = async () => {
    if (approvingAll) {
      return;
    }

    if (pendingQueueCount === 0) {
      setMessage("ℹ️ Không có bản rút gọn nào cần duyệt hàng loạt.");
      return;
    }

    setApprovingAll(true);
    try {
      let page = 0;
      const size = 100;
      let totalApproved = 0;
      let hasMore = true;

      while (hasMore) {
        const listRes = await axios.get(API_SIMPLIFIED_MANAGEMENT, {
          headers: authHeader,
          params: { status: "PENDING", page, size },
          validateStatus: () => true,
        });

        if (listRes.status !== 200) {
          throw new Error("Cannot load pending queue");
        }

        const pageData = listRes.data?.data || {};
        const pendingItems = Array.isArray(pageData.content) ? pageData.content : [];

        if (pendingItems.length === 0) {
          hasMore = false;
          break;
        }

        const approveResults = await Promise.allSettled(
          pendingItems.map((item) =>
            axios.put(`${API_SIMPLIFIED_MANAGEMENT}/${item.id}/approve`, null, {
              headers: authHeader,
            })
          )
        );

        totalApproved += approveResults.filter((result) => result.status === "fulfilled").length;

        hasMore = !pageData.last;
        page += 1;
      }

      await refreshMine();
      await refreshPendingQueueCount();
      setCurrentStatus((prev) => (prev && prev !== "NEW" ? "APPROVED" : prev));
      setMessage(`✅ Đã duyệt hàng loạt ${totalApproved} bản rút gọn AI đang chờ duyệt.`);
    } catch {
      setMessage("❌ Không thể duyệt tất cả bản rút gọn.");
    } finally {
      setApprovingAll(false);
    }
  };

  const handleGenerateAllWithAI = async () => {
    if (!moderatorId) {
      setMessage("⚠️ Không xác định được tài khoản moderator.");
      return;
    }

    if (bulkGenerating) {
      return;
    }

    if (bulkGenerateCount === 0) {
      setMessage("ℹ️ Không có điều luật nào cần AI rút gọn thêm.");
      return;
    }

    setBulkGenerating(true);
    setMessage(
      `🤖 Đang AI rút gọn hàng loạt ${bulkGenerateCount} điều luật${selectedLawId ? " trong bộ luật đã chọn" : ""}...`
    );

    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    const getBackendErrorMessage = (response) => {
      const data = response?.data;
      if (!data) return "";
      if (typeof data === "string") return data;
      if (typeof data?.message === "string") return data.message;
      if (typeof data?.error === "string") return data.error;
      return "";
    };

    const summarizeWithRetry = async (payload, maxAttempts = 3) => {
      let lastErrorMessage = "";

      for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
        const aiRes = await axios.post(API_AI, payload, {
          headers: { "Content-Type": "application/json" },
          validateStatus: () => true,
        });

        const summary = String(aiRes.data?.summary || "").trim();
        if (aiRes.status === 200 && summary) {
          return { ok: true, summary };
        }

        const backendMessage = getBackendErrorMessage(aiRes);
        lastErrorMessage = backendMessage || `HTTP ${aiRes.status}`;

        // Nội dung trống thì bỏ qua luôn, retry cũng không có tác dụng.
        if (
          aiRes.status === 400 &&
          (backendMessage.includes("LAW_CONTENT_EMPTY") ||
            backendMessage.includes("Nội dung điều luật trống"))
        ) {
          return { ok: false, skip: true, reason: "LAW_CONTENT_EMPTY" };
        }

        if (attempt < maxAttempts) {
          await sleep(500 * attempt);
          continue;
        }
      }

      return { ok: false, reason: lastErrorMessage || "AI_SUMMARIZE_FAILED" };
    };

    let createdCount = 0;
    let skippedCount = 0;
    let failedCount = 0;
    const failureReasons = [];

    try {
      for (const article of bulkGenerateTargets) {
        const articleIdValue = Number(article.articleId);
        try {
          let originalContent = String(article.content || "").trim();
          let articleTitleValue = String(article.articleTitle || "").trim();

          if (!originalContent || !articleTitleValue) {
            const detailRes = await axios.get(`${API_ARTICLE}/${articleIdValue}`, {
              headers: authHeader,
              validateStatus: () => true,
            });

            if (detailRes.status !== 200) {
              failedCount += 1;
              continue;
            }

            originalContent = String(detailRes.data?.content || "").trim();
            articleTitleValue = String(detailRes.data?.articleTitle || article.articleTitle || "").trim();
          }

          if (!originalContent || !articleTitleValue) {
            skippedCount += 1;
            continue;
          }

          const summarizeResult = await summarizeWithRetry({
            lawContent: originalContent,
            articleTitle: articleTitleValue,
            articleId: articleIdValue,
          });

          if (!summarizeResult.ok) {
            if (summarizeResult.skip) {
              skippedCount += 1;
            } else {
              failedCount += 1;
              if (failureReasons.length < 3) {
                failureReasons.push(`Điều ${articleIdValue}: ${summarizeResult.reason}`);
              }
            }
            continue;
          }

          const saveRes = await axios.post(
            `${API_SIMPLIFIED}/create`,
            {
              articleId: articleIdValue,
              moderatorId: Number(moderatorId),
              category: articleTitleValue,
              contentSimplified: summarizeResult.summary,
            },
            {
              headers: authHeader,
              validateStatus: () => true,
            }
          );

          if (saveRes.status === 200) {
            createdCount += 1;
          } else {
            failedCount += 1;
            if (failureReasons.length < 3) {
              const saveMessage = getBackendErrorMessage(saveRes) || `HTTP ${saveRes.status}`;
              failureReasons.push(`Điều ${articleIdValue}: ${saveMessage}`);
            }
          }

          // Giảm tần suất gọi AI để tránh rate limit khi chạy hàng loạt.
          await sleep(250);
        } catch {
          failedCount += 1;
        }
      }

      await refreshMine();
      await refreshPendingQueueCount();

      const reasonText = failureReasons.length
        ? ` | Chi tiết: ${failureReasons.join("; ")}`
        : "";
      setMessage(
        `✅ AI đã tạo ${createdCount} bản rút gọn. Bỏ qua ${skippedCount}. Lỗi ${failedCount}.${reasonText}`
      );
    } catch {
      setMessage("❌ Không thể AI rút gọn hàng loạt lúc này.");
    } finally {
      setBulkGenerating(false);
    }
  };

  const handleHideFromUser = async (item = currentMineItem) => {
    if (!moderatorId) {
      setMessage("⚠️ Không xác định được tài khoản moderator.");
      return;
    }

    if (!item?.id) {
      setMessage("⚠️ Chưa có bản rút gọn để ẩn.");
      return;
    }

    try {
      await axios.put(`${API_SIMPLIFIED}/${item.id}/hide-from-user`, null, {
        headers: authHeader,
        params: { moderatorId },
      });

      await refreshMine();
      if (Number(item.articleId) === Number(articleId)) {
        setCurrentStatus("ARCHIVED");
      }
      setMessage("🗑️ Đã ẩn bản rút gọn khỏi user, moderator vẫn có thể chỉnh sửa.");
    } catch {
      setMessage("❌ Không thể ẩn bản rút gọn khỏi user.");
    }
  };

  const handleHideAllFromUser = async () => {
    if (!moderatorId) {
      setMessage("⚠️ Không xác định được tài khoản moderator.");
      return;
    }

    if (bulkHideCount === 0) {
      setMessage("ℹ️ Tất cả bản rút gọn đã được ẩn khỏi user.");
      return;
    }

    try {
      const res = await axios.put(
        `${API_SIMPLIFIED}/hide-all/${moderatorId}`,
        null,
        { headers: authHeader }
      );
      await refreshMine();
      setCurrentStatus((prev) => (prev && prev !== "NEW" ? "ARCHIVED" : prev));
      const updatedCount = Number(res.data?.updated || 0);
      setMessage(`🗑️ Đã ẩn hàng loạt ${updatedCount} bản rút gọn khỏi user.`);
    } catch {
      setMessage("❌ Không thể ẩn tất cả bản rút gọn.");
    }
  };

  const handleShowAllToUser = async () => {
    if (!moderatorId) {
      setMessage("⚠️ Không xác định được tài khoản moderator.");
      return;
    }

    if (bulkShowCount === 0) {
      setMessage("ℹ️ Không có bản rút gọn nào đang ẩn để hiện lại.");
      return;
    }

    try {
      const res = await axios.put(
        `${API_SIMPLIFIED}/show-all/${moderatorId}`,
        null,
        { headers: authHeader }
      );
      await refreshMine();
      const updatedCount = Number(res.data?.updated || 0);
      setCurrentStatus((prev) => (prev === "ARCHIVED" ? "APPROVED" : prev));
      setMessage(`👁️ Đã hiện lại ${updatedCount} bản rút gọn cho user.`);
    } catch {
      setMessage("❌ Không thể hiện lại tất cả bản rút gọn.");
    }
  };

  return (
    <ModeratorWorkspace
      active="simplify"
      title="Rút gọn luật"
      description="Hỗ trợ soạn thảo và xem xét các bản tóm lược văn bản pháp luật bằng trí tuệ nhân tạo. Đảm bảo tính pháp lý và sự dễ hiểu cho người lao động."
    >
      <section className="simplify-mode-switch" aria-label="Chế độ làm việc">
        <button
          type="button"
          className={`simplify-mode-btn${activeSection === "simplify" ? " active" : ""}`}
          onClick={() => setActiveSection("simplify")}
        >
          Rút gọn luật
        </button>
        <button
          type="button"
          className={`simplify-mode-btn${activeSection === "manage" ? " active" : ""}`}
          onClick={() => setActiveSection("manage")}
        >
          Quản lý luật
        </button>
      </section>

      {activeSection === "simplify" ? (
        <>
      <section className="simplify-composer-shell">
        <div className="simplify-page">
          <div className="simplify-bulk-actions" aria-label="Thao tác hàng loạt">
            <button
              type="button"
              className="btn submit"
              onClick={handleGenerateAllWithAI}
              disabled={bulkGenerateCount === 0 || bulkGenerating}
            >
              {bulkGenerating
                ? "AI đang rút gọn..."
                : `AI rút gọn tất cả (${bulkGenerateCount})`}
            </button>
            <button
              type="button"
              className="btn danger"
              onClick={handleHideAllFromUser}
              disabled={bulkHideCount === 0}
            >
              Ẩn tất cả ({bulkHideCount})
            </button>
            <button
              type="button"
              className="btn approve"
              onClick={handleShowAllToUser}
              disabled={bulkShowCount === 0}
            >
              Hiện lại tất cả ({bulkShowCount})
            </button>
          </div>

          <div className="simplify-selector-card">
            <div className="simplify-selector-label">Lựa chọn văn bản pháp luật</div>

            <div className="simplify-selector-grid">
              <div className="simplify-selector-field">
                <FiSearch aria-hidden="true" />
                <select
                  value={selectedLawId}
                  onChange={(e) =>
                    setSelectedLawId(e.target.value ? Number(e.target.value) : "")
                  }
                >
                  <option value="">-- Chọn bộ luật --</option>
                  {lawOptions.map((law) => (
                    <option key={law.lawId} value={law.lawId}>
                      {law.lawTitle?.trim()}
                    </option>
                  ))}
                </select>
                <FiChevronDown aria-hidden="true" />
              </div>

              <div className="simplify-selector-field">
                <FiSearch aria-hidden="true" />
                <select
                  value={articleId}
                  onChange={(e) =>
                    setArticleId(e.target.value ? Number(e.target.value) : "")
                  }
                >
                  <option value="">
                    {selectedLawId ? "-- Chọn điều luật trong bộ đã chọn --" : "-- Chọn điều luật --"}
                  </option>
                  {filteredArticles.map((a) => (
                    <option key={a.articleId} value={a.articleId}>
                      {a.articleTitle?.trim()}
                    </option>
                  ))}
                </select>
                <FiChevronDown aria-hidden="true" />
              </div>
            </div>
          </div>

          <div className="content-columns composer">
            <article className="column original compose-card">
              <div className="compose-card-header">
                <h4>📄 Bản gốc</h4>
              </div>

              <div className="law-box enhanced">
                <div className="law-box-title">
                  {articleTitle || "Chưa chọn văn bản pháp luật"}
                </div>

                {originalParagraphs.length > 0 ? (
                  <div className="law-box-content">
                    {originalParagraphs.map((paragraph, index) => (
                      <p key={`${index}-${paragraph.slice(0, 20)}`}>
                        {paragraph}
                      </p>
                    ))}
                  </div>
                ) : (
                  <p className="law-box-empty">Chọn điều luật để xem nội dung gốc.</p>
                )}

                <div className="law-box-footer">{selectedLawTitle}</div>
              </div>
            </article>

            <article className="column simplified compose-card">
              <div className="compose-card-header with-tools">
                <h4>✦ Bản rút gọn (AI)</h4>

                <div className="compose-toolbar">
                  <button type="button" onClick={() => setIsEditingDraft((value) => !value)} title="Chỉnh sửa">
                    <FiEdit3 />
                  </button>
                  <button type="button" onClick={handleCopySimplified} title="Sao chép">
                    <FiClipboard />
                  </button>
                  <button type="button" onClick={handleRegenerate} title="Tạo lại bằng AI">
                    <FiRefreshCw />
                  </button>
                </div>
              </div>

              <div className="simplify-ai-panel">
                <div className="simplify-ai-note">
                  <FiCheckCircle aria-hidden="true" />
                  <span>
                    Nội dung này đã được tối ưu hóa cho công nhân viên chức. Bạn có thể chỉnh sửa trực tiếp nếu cần dưới đây.
                  </span>
                </div>

                {isEditingDraft ? (
                  <div className="simplify-edit-area">
                    <textarea
                      className="simplify-input moderator-mode"
                      disabled={currentStatus === "PENDING"}
                      value={simplifiedText}
                      onChange={(e) => setSimplifiedText(e.target.value)}
                      placeholder="Nhập bản rút gọn..."
                    />
                  </div>
                ) : simplifiedParagraphs.length > 0 ? (
                  <ol className="simplify-ai-list">
                    {simplifiedParagraphs.map((paragraph, index) => (
                      <li key={`${index}-${paragraph.slice(0, 20)}`}>
                        <span className="simplify-ai-index">{String(index + 1).padStart(2, "0")}</span>
                        <p>{paragraph}</p>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <div className="simplify-ai-empty">
                    Chưa có bản rút gọn. Hãy chọn điều luật để AI tạo nội dung gợi ý.
                  </div>
                )}
              </div>
            </article>
          </div>

          <div className="simplify-action-bar">
            <div className="simplify-review-status">
              <div className="simplify-review-avatars" aria-hidden="true">
                <span>A</span>
                <span>R</span>
              </div>
              <span>{queueLabel}</span>
            </div>

            <div className="action-buttons docked">
              <button
                className="btn danger"
                onClick={() => handleHideFromUser()}
                disabled={!currentMineItem?.id}
              >
                Ẩn khỏi user
              </button>
              <button className="btn reset ghost" onClick={handleReset}>
                <FiRefreshCw />
                Làm mới
              </button>
              <button className="btn submit" onClick={handleSubmit}>
                Lưu và duyệt ngay
              </button>
            </div>
          </div>

          {message ? <p className="status-message inline">{message}</p> : null}
        </div>
      </section>

      <section className="moderator-workspace-panel simplify-history-panel">
        <div className="simplify-history-head">
          <div>
            <h3>Lịch sử gửi duyệt</h3>
            <p>Theo dõi các bản rút gọn bạn đã gửi và quay lại chỉnh sửa khi cần.</p>
          </div>

          <div className="filter-row compact">
            <label>Lọc theo trạng thái</label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">Tất cả</option>
              <option value="PENDING">Chờ duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Bị từ chối</option>
              <option value="ARCHIVED">Đã ẩn khỏi user</option>
            </select>
          </div>
        </div>

        <table className="simplified-table">
          <thead>
            <tr>
              <th>Điều luật</th>
              <th>Trạng thái</th>
              <th>Ngày gửi</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {filteredList.length === 0 ? (
              <tr>
                <td colSpan="4">Không có bài nào.</td>
              </tr>
            ) : (
              filteredList.map((s) => (
                <tr key={s.id}>
                  <td>{s.articleTitle}</td>
                  <td>{statusMap[s.status] || s.status}</td>
                  <td>
                    {s.createdAt
                      ? new Date(s.createdAt).toLocaleDateString()
                      : "-"}
                  </td>
                  <td>
                    <div className="action-buttons history-actions">
                      {(s.status === "PENDING" || s.status === "REJECTED") && (
                        <button
                          className="btn approve"
                          onClick={() => handleApproveOne(s)}
                        >
                          Duyệt
                        </button>
                      )}

                      {s.status === "APPROVED" && (
                        <button
                          className="btn danger"
                          onClick={() => handleHideFromUser(s)}
                        >
                          Ẩn khỏi user
                        </button>
                      )}

                      {(s.status === "APPROVED" || s.status === "REJECTED" || s.status === "ARCHIVED") && (
                        <button
                          className="btn edit-btn"
                          onClick={() => {
                            setArticleId(s.articleId);
                            setIsEditingDraft(true);
                            window.scrollTo({ top: 0, behavior: "smooth" });
                          }}
                        >
                          ✏️ Chỉnh sửa
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>
        </>
      ) : (
        <section className="moderator-workspace-panel simplify-management-panel">
          <Laws hideSimplifiedManagement />
        </section>
      )}
    </ModeratorWorkspace>
  );
}

