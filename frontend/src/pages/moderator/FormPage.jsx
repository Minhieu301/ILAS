import React, { useCallback, useEffect, useState } from "react";
import { Plus, Edit2, Trash2, Eye, EyeOff, FileText, Calendar, User, Search, Filter } from "lucide-react";
import ModeratorWorkspace from "../../components/moderator/ModeratorWorkspace";
import "../../styles/moderator/FormPage.css";
import {
  getFormsByModerator,
  createForm,
  updateForm,
  deleteForm,
  publishForm,
  hideForm,
} from "../../api/form";
import FormModal from "../../components/moderator/FormModal";

export default function FormPage() {
  const [moderatorId, setModeratorId] = useState(null);
  const [token] = useState(localStorage.getItem("token"));
  const [forms, setForms] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [editForm, setEditForm] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL"); 

  useEffect(() => {
    if (!token) return;
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      setModeratorId(Number(payload.userId ?? payload.id ?? payload.sub ?? null));
    } catch (e) {
      console.error("Decode token lỗi:", e);
    }
  }, [token]);

  const loadForms = useCallback(async () => {
    if (!moderatorId) return;

    try {
      const res = await getFormsByModerator(moderatorId);
      const list = Array.isArray(res.data) ? res.data : [];
      setForms(list);
    } catch (error) {
      console.error("Lỗi tải biểu mẫu:", error);
      setForms([]);
    }
  }, [moderatorId]);

  useEffect(() => {
    loadForms();
  }, [loadForms]);

  const handleCreate = () => {
    setEditForm(null);
    setShowModal(true);
  };

  const handleEdit = (form) => {
    setEditForm(form);
    setShowModal(true);
  };

  const handleSave = async (form) => {
    const payload = {
      title: form.title,
      category: form.category,
      description: form.description,
      fileUrl: form.fileUrl || null,
    };
    if (editForm) await updateForm(editForm.templateId, payload);
    else await createForm(moderatorId, payload);
    loadForms();
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Bạn có chắc muốn xóa biểu mẫu này không?")) return;

    try {
      await deleteForm(id);
      alert("🗑️ Biểu mẫu đã được xóa thành công!");
      loadForms();
    } catch (err) {
      if (err.response?.status === 400 || err.response?.status === 403) {
        alert("⚠️ Không thể xóa biểu mẫu đang hiển thị. Hãy ẩn trước.");
      } else {
        alert("❌ Lỗi khi xóa biểu mẫu. Vui lòng thử lại sau.");
      }
    }
  };

  const handlePublish = async (id) => {
    try {
      await publishForm(id);
      alert("📢 Biểu mẫu đã được đăng!");
      loadForms();
    } catch (err) {
      alert(err?.response?.data?.message || "❌ Không thể đăng biểu mẫu.");
    }
  };

  const handleHide = async (id) => {
    try {
      await hideForm(id);
      alert("🙈 Biểu mẫu đã được ẩn khỏi user.");
      loadForms();
    } catch (err) {
      alert(err?.response?.data?.message || "❌ Không thể ẩn biểu mẫu.");
    }
  };

  // Lọc danh sách biểu mẫu theo trạng thái
  const filteredForms = forms.filter((f) => {
    const matchSearch = (f.title || "")
      .toLowerCase()
      .includes(search.toLowerCase());
    const matchStatus =
      statusFilter === "ALL" ||
      (f.status || "").toLowerCase() === statusFilter.toLowerCase();
    return matchSearch && matchStatus;
  });

  return (
    <ModeratorWorkspace
      active="forms"
      title="Template Management"
      description="Quản lý kho biểu mẫu do moderator phụ trách, đăng trực tiếp và ẩn khi cần."
    >
      <section className="moderator-workspace-panel">
        <div className="form-page">

          {/* Thanh tìm kiếm + nút thêm */}
          <div className="search-bar">
            <div className="search-wrapper">
              <Search size={18} className="search-icon" />
              <input
                type="text"
                placeholder="Tìm kiếm biểu mẫu..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            {/* Dropdown lọc trạng thái */}
            <div className="filter-wrapper">
              <Filter size={18} className="filter-icon" />
              <select
                className="filter-select"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="draft">Nháp</option>
                <option value="approved">Đang đăng</option>
                <option value="archived">Đã ẩn</option>
                <option value="pending">Chờ duyệt</option>
                <option value="rejected">Bị từ chối</option>
              </select>
            </div>

            <button className="btn-create" onClick={handleCreate}>
              <Plus size={18} />
              Thêm biểu mẫu
            </button>
          </div>

          <div className="form-list">
            {filteredForms.length === 0 ? (
              <p className="empty-text">Không có biểu mẫu phù hợp</p>
            ) : (
              filteredForms.map((form) => {
                const status = (form.status || "draft").toLowerCase();
                const isPublished = status === "approved";

                return (
                  <div key={form.templateId} className="form-card">
                    <div className="form-header">
                      <div className="form-title-section">
                        <div className="form-title-wrapper">
                          <FileText size={20} className="form-icon" />
                          <div>
                            <h3>{form.title}</h3>
                            <p className="meta">
                              <span className="meta-item">
                                {form.category}
                              </span>
                              <span className="meta-divider">•</span>
                              <span className="meta-item">
                                <Calendar size={13} className="inline-icon" />
                                {new Date(form.createdAt).toLocaleDateString("vi-VN")}
                              </span>
                            </p>
                          </div>
                        </div>
                      </div>
                      <span className={`status-badge ${status}`}>{form.status}</span>
                    </div>

                    <p className="description">{form.description || "Không có mô tả"}</p>

                    <div className="form-footer">
                      <div className="moderator-info">
                        <User size={16} className="inline-icon" />
                        {form.moderatorName}
                      </div>

                      <div className="form-actions">
                        <button
                          className="btn edit"
                          onClick={() => handleEdit(form)}
                          disabled={isPublished}
                          title={
                            isPublished
                              ? "Không thể sửa biểu mẫu đang đăng"
                              : "Chỉnh sửa biểu mẫu"
                          }
                        >
                          <Edit2 size={16} />
                          Sửa
                        </button>

                        {isPublished ? (
                          <button
                            className="btn danger"
                            onClick={() => handleHide(form.templateId)}
                            title="Ẩn biểu mẫu khỏi người dùng"
                          >
                            <EyeOff size={16} />
                            Ẩn
                          </button>
                        ) : (
                          <button
                            className="btn submit"
                            onClick={() => handlePublish(form.templateId)}
                            title="Đăng biểu mẫu"
                          >
                            <Eye size={16} />
                            Đăng
                          </button>
                        )}

                        <button
                          className="btn delete"
                          onClick={() => handleDelete(form.templateId)}
                          disabled={isPublished}
                          title={
                            isPublished
                              ? "Không thể xóa biểu mẫu đang đăng"
                              : "Xóa biểu mẫu"
                          }
                        >
                          <Trash2 size={16} />
                          Xóa
                        </button>
                      </div>
                    </div>

                    {form.fileUrl && (
                      <a
                        href={form.fileUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="file-link"
                      >
                        <FileText size={14} className="inline-icon" />
                        Xem file đính kèm
                      </a>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {showModal && (
            <FormModal
              moderatorId={moderatorId}
              formData={editForm}
              onSave={handleSave}
              onClose={() => setShowModal(false)}
            />
          )}
        </div>
      </section>
    </ModeratorWorkspace>
  );
}

