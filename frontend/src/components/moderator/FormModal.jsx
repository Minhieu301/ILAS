import React, { useState, useEffect } from "react";
import { Upload, Save, X, File, AlertCircle } from "lucide-react";
import { uploadFormFile } from "../../api/form";
import "../../styles/moderator/FormModal.css";


export default function FormModal({ moderatorId, formData, onSave, onClose }) {
  const [form, setForm] = useState({
    title: "",
    category: "",
    description: "",
    fileUrl: "",
  });
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);

  // Reset form mỗi khi mở modal (tạo mới hoặc sửa)
  useEffect(() => {
    if (formData) {
      setForm({
        title: formData.title || "",
        category: formData.category || "",
        description: formData.description || "",
        fileUrl: formData.fileUrl || "",
      });
    } else {
      setForm({
        title: "",
        category: "",
        description: "",
        fileUrl: "",
      });
    }
  }, [formData]);

  const handleFileChange = (e) => setFile(e.target.files[0]);

  const handleUpload = async () => {
    if (!file) return alert("Vui lòng chọn file trước khi tải lên!");
    try {
      setUploading(true);
      const res = await uploadFormFile(moderatorId, file);
      if (res.data?.fileUrl) {
        setForm((prev) => ({ ...prev, fileUrl: res.data.fileUrl }));
        alert("✅ File đã tải lên thành công!");
      } else {
        alert("Không nhận được fileUrl từ server!");
      }
    } catch (err) {
      console.error("Lỗi upload file:", err);
      alert("Lỗi khi tải file!");
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!form.title.trim()) return alert("Vui lòng nhập tiêu đề!");

    if (!form.fileUrl) {
      const confirmUpload = window.confirm(
        "Bạn chưa tải file lên. Vẫn muốn lưu biểu mẫu này?"
      );
      if (!confirmUpload) return;
    }

    await onSave(form);
    onClose();
  };

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header">
          <h3>{formData ? "Chỉnh sửa biểu mẫu" : "Tạo mới biểu mẫu"}</h3>
          <button className="modal-close" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <div className="modal-body">
          <div className="form-group">
            <label>Tiêu đề biểu mẫu *</label>
            <input
              type="text"
              placeholder="Nhập tiêu đề..."
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label>Phân loại</label>
            <input
              type="text"
              placeholder="vd: Nghỉ phép, Hợp đồng..."
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label>Mô tả biểu mẫu</label>
            <textarea
              placeholder="Nhập mô tả..."
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows="4"
            />
          </div>

          {/* Upload file */}
          <div className="form-group">
            <label>Tệp đính kèm</label>
            <div className="file-upload">
              <input
                type="file"
                accept=".pdf,.doc,.docx"
                onChange={handleFileChange}
                id="file-input"
              />
              <label htmlFor="file-input" className="file-input-label">
                <Upload size={18} />
                Chọn file
              </label>
              <button
                onClick={handleUpload}
                disabled={uploading || !file}
                className="btn-upload"
              >
                {uploading ? "Đang tải..." : "Tải lên"}
              </button>
            </div>
            {file && <p className="file-name">📄 {file.name}</p>}
          </div>

          {/* Link xem file */}
          {form.fileUrl && (
            <div className="form-group file-preview">
              <p className="file-link-label">
                <File size={14} />
                File đã tải:
              </p>
              <a
                href={form.fileUrl}
                target="_blank"
                rel="noreferrer"
                className="file-link"
              >
                Xem file
              </a>
            </div>
          )}

          {/* Preview PDF */}
          {form.fileUrl?.endsWith(".pdf") && (
            <div className="pdf-preview">
              <iframe
                src={form.fileUrl}
                title="preview"
                width="100%"
                height="300px"
              ></iframe>
            </div>
          )}
        </div>

        <div className="modal-actions">
          <button className="btn-primary" onClick={handleSave}>
            <Save size={16} />
            Lưu
          </button>
          <button className="btn-secondary" onClick={onClose}>
            <X size={16} />
            Hủy
          </button>
        </div>
      </div>
    </div>
  );
}

