# ai/local_embedder.py
from sentence_transformers import SentenceTransformer
import numpy as np
import os
import re

# Ưu tiên model từ env để dễ chỉnh theo máy.
# Mặc định chuyển sang bản small để giảm nguy cơ lỗi thiếu RAM/pagefile.
PRIMARY_MODEL = os.getenv("EMBEDDING_MODEL", "intfloat/multilingual-e5-small").strip()
FALLBACK_MODELS = [
    PRIMARY_MODEL,
    "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
]

_model = None
_active_model_name = None
_EMBED_CACHE = {}
_EMBED_CACHE_MAX = 500


def _load_model() -> SentenceTransformer:
    global _model, _active_model_name

    if _model is not None:
        return _model

    last_error = None
    tried = set()

    for model_name in FALLBACK_MODELS:
        if not model_name or model_name in tried:
            continue
        tried.add(model_name)

        print(f"🔍 Loading embedding model: {model_name} ...")
        try:
            _model = SentenceTransformer(model_name)
            _active_model_name = model_name
            print(f"✅ Embedding model ready: {_active_model_name}")
            return _model
        except OSError as e:
            # Lỗi 1455 thường do thiếu page file/RAM khi load model lớn.
            last_error = e
            print(f"⚠️ Load failed for {model_name}: {e}")
        except Exception as e:
            last_error = e
            print(f"⚠️ Unexpected load error for {model_name}: {e}")

    raise RuntimeError(
        "Không thể load embedding model. "
        "Hãy thử model nhẹ hơn qua EMBEDDING_MODEL hoặc tăng page file."
    ) from last_error


def clean_text(text: str) -> str:
    """Làm sạch text trước khi embed."""
    if not text:
        return ""
    text = re.sub(r"\s+", " ", text)
    # bỏ prefix Điều/Khoản để tránh model bị nhiễu
    text = re.sub(r"Điều\s+\d+\.?", "", text, flags=re.IGNORECASE)
    text = re.sub(r"Khoản\s+\d+\.?", "", text, flags=re.IGNORECASE)
    return text.strip()


def get_local_embedding(text: str) -> np.ndarray | None:
    """Embedding 1 câu (giữ lại cho code cũ nếu có dùng)."""
    if not text:
        return None
    key = text.strip()[:200]
    if key in _EMBED_CACHE:
        return _EMBED_CACHE[key]
    model = _load_model()
    text_clean = clean_text(text)
    vec = model.encode([text_clean], batch_size=16, normalize_embeddings=True)[0]
    result = np.asarray(vec, dtype=np.float32)
    if len(_EMBED_CACHE) >= _EMBED_CACHE_MAX:
        keys_to_delete = list(_EMBED_CACHE.keys())[:100]
        for k in keys_to_delete:
            del _EMBED_CACHE[k]
    _EMBED_CACHE[key] = result
    return result


def embed_texts(texts: list[str]) -> np.ndarray:
    """
    Embedding nhiều câu cùng lúc – dùng trong RAG Level 4.
    Trả về array shape (N, D).
    """
    model = _load_model()

    if not texts:
        return np.zeros((0, model.get_sentence_embedding_dimension()), dtype=np.float32)

    cleaned = [clean_text(t) for t in texts]
    vecs = model.encode(cleaned, batch_size=16, normalize_embeddings=True)
    return np.asarray(vecs, dtype=np.float32)
