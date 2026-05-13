import React, { useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { renderAsync } from "docx-preview";
import * as formAPI from "../../api/form";
import "../../styles/user/FormDetailPage.css";

const resolveFileUrl = (fileUrl) => {
  if (!fileUrl) return null;

  try {
    return new URL(fileUrl).toString();
  } catch (_) {
    const backendBase = "http://localhost:8080";
    return `${backendBase}${fileUrl.startsWith("/") ? "" : "/"}${fileUrl}`;
  }
};

const getTypeLabel = (fileType) => (fileType || "DOCX").toUpperCase();

const getBadgeClass = (fileType) => {
  const type = (fileType || "other").toLowerCase();
  if (type.includes("pdf")) return "pdf";
  if (type.includes("doc")) return "docx";
  return "other";
};

export default function FormDetailPage() {
  const { templateId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const docxViewerRef = useRef(null);

  const [form, setForm] = useState(location.state?.form || null);
  const [loading, setLoading] = useState(!location.state?.form);
  const [error, setError] = useState("");
  const [viewerError, setViewerError] = useState("");
  const [viewerLoading, setViewerLoading] = useState(false);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  useEffect(() => {
    let mounted = true;

    const loadForm = async () => {
      if (!templateId) return;
      if (location.state?.form && String(location.state.form.templateId) === String(templateId)) {
        setForm(location.state.form);
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError("");
        const response = await formAPI.getFormTemplateById(templateId);
        const nextForm = response?.data || response || null;

        if (!mounted) return;

        if (nextForm) {
          setForm(nextForm);
        } else {
          setError("Không tìm thấy biểu mẫu cần xem.");
        }
      } catch (requestError) {
        console.error("Error loading form detail:", requestError);
        if (mounted) {
          setError("Không thể tải chi tiết biểu mẫu.");
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    loadForm();

    return () => {
      mounted = false;
    };
  }, [templateId, location.state]);

  const normalizedUrl = useMemo(() => resolveFileUrl(form?.fileUrl), [form?.fileUrl]);
  const fileType = (form?.fileType || "").toLowerCase();
  const isPdf = fileType.includes("pdf") || (normalizedUrl || "").toLowerCase().includes(".pdf");
  const isDocx = fileType.includes("doc") || (normalizedUrl || "").toLowerCase().includes(".doc");

  const previewUrl = useMemo(() => {
    if (!normalizedUrl || !isPdf) return null;
    return normalizedUrl;
  }, [normalizedUrl, form?.fileType]);

  useEffect(() => {
    let cancelled = false;

    const renderDocx = async () => {
      if (!isDocx || !normalizedUrl || !docxViewerRef.current) {
        return;
      }

      try {
        setViewerLoading(true);
        setViewerError("");
        const response = await fetch(normalizedUrl);
        if (!response.ok) {
          throw new Error(`Không tải được file DOCX (status ${response.status})`);
        }

        const buffer = await response.arrayBuffer();
        if (cancelled || !docxViewerRef.current) return;

        docxViewerRef.current.innerHTML = "";
        await renderAsync(buffer, docxViewerRef.current, undefined, {
          className: "docx-preview-viewer",
          inWrapper: true,
          ignoreWidth: false,
          ignoreHeight: false,
          breakPages: true,
        });
      } catch (requestError) {
        console.error("Error rendering DOCX preview:", requestError);
        if (!cancelled) {
          setViewerError("Không thể hiển thị DOCX trực tiếp. Bạn có thể tải file hoặc mở tab mới.");
        }
      } finally {
        if (!cancelled) setViewerLoading(false);
      }
    };

    renderDocx();

    return () => {
      cancelled = true;
      if (docxViewerRef.current) {
        docxViewerRef.current.innerHTML = "";
      }
    };
  }, [normalizedUrl, isDocx]);

  const handleDownload = async () => {
    if (!form?.fileUrl) return;
    try {
      await formAPI.downloadFile(form.fileUrl, form.fileName || form.title || "form.docx");
    } catch (requestError) {
      console.error("Error downloading form:", requestError);
      window.alert("Không thể tải file. Vui lòng thử lại.");
    }
  };

  if (loading && !form) {
    return (
      <div className="udash-page formdetail-page-shell">
        <main className="formdetail-main">
          <div className="formdetail-loading">Đang tải chi tiết biểu mẫu...</div>
        </main>
      </div>
    );
  }

  return (
    <div className="udash-page formdetail-page-shell">
      <main className="formdetail-main">
        <section className="formdetail-topbar">
          <button type="button" className="formdetail-back-btn" onClick={() => navigate(-1)}>
            ← Quay lại
          </button>
          <div className="formdetail-breadcrumb">Xem biểu mẫu</div>
        </section>

        {error && <div className="formdetail-message error">{error}</div>}

        <section className="formdetail-viewer-card compact">
          <div className="formdetail-viewer-top compact">
            <div>
              <h1>{form?.title || "Xem biểu mẫu"}</h1>
              <p>{form?.fileName || "Tài liệu gốc"}</p>
            </div>
            <div className="formdetail-viewer-actions">
              <span className={`formdetail-type-badge ${getBadgeClass(form?.fileType)}`}>
                {getTypeLabel(form?.fileType)}
              </span>
              <button type="button" className="formdetail-mini-btn" onClick={handleDownload}>
                Tải file
              </button>
              <a className="formdetail-mini-btn link" href={normalizedUrl || "#"} target="_blank" rel="noreferrer">
                Mở tab mới
              </a>
            </div>
          </div>

          <div className="formdetail-frame-shell compact">
            {isDocx ? (
              <div className="formdetail-docx-shell">
                {viewerLoading && <div className="formdetail-frame-empty">Đang tải bản xem trước...</div>}
                {viewerError && <div className="formdetail-frame-empty error">{viewerError}</div>}
                <div ref={docxViewerRef} className="formdetail-docx-viewer" />
              </div>
            ) : previewUrl ? (
              <iframe title="form-preview" src={previewUrl} className="formdetail-frame" />
            ) : (
              <div className="formdetail-frame-empty">Không có đường dẫn xem trước cho biểu mẫu này.</div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
