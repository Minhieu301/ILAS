import numpy as np
from pathlib import Path
import json
import os
import re
import unicodedata
import asyncio
from concurrent.futures import ThreadPoolExecutor, as_completed
from inspect import isawaitable
from sklearn.metrics.pairwise import cosine_similarity

from ai.local_embedder import get_local_embedding
from ai.bm25_index import bm25_search, bm25_rerank_candidates
from ai.legal_topic_boost import topic_boost, is_labor_question

DATA_DIR = Path(__file__).resolve().parents[1] / "vector_store"
ENABLE_SEMANTIC_SEARCH = os.getenv("ENABLE_SEMANTIC_SEARCH", "true").lower() == "true"
ALWAYS_RUN_LEXICAL = os.getenv("ALWAYS_RUN_LEXICAL", "false").lower() == "true"
LEXICAL_TOP_K = int(os.getenv("LEXICAL_TOP_K", "80"))
ARTICLE_LEXICAL_TOP_K = int(os.getenv("ARTICLE_LEXICAL_TOP_K", "60"))
LEXICAL_MAX_ASPECTS = int(os.getenv("LEXICAL_MAX_ASPECTS", "5"))
SEMANTIC_TOP_K = int(os.getenv("SEMANTIC_TOP_K", "30"))
FINAL_TOP_K = int(os.getenv("FINAL_TOP_K", "20"))
LEXICAL_FALLBACK_MIN_OVERLAP = float(os.getenv("LEXICAL_FALLBACK_MIN_OVERLAP", "0.18"))


# ======================================
# 1) DETECT ARTICLE NUMBER
# ======================================
def detect_article_number(query: str):
    q = _strip_accents(query).lower()
    m = re.search(r"(?:điều|dieu)\s+(\d+)", q)
    if m:
        return m.group(1)
    return None


def _strip_accents(text: str) -> str:
    text = str(text or "")
    text = unicodedata.normalize("NFD", text)
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return text.replace("đ", "d").replace("Đ", "D")


def detect_law_hint(query: str):
    q = _strip_accents(query).lower()
    law_hints = [
        ("bo luat to tung hinh su", "bộ luật tố tụng hình sự"),
        ("to tung hinh su", "bộ luật tố tụng hình sự"),
        ("bo luat hinh su", "bộ luật hình sự"),
        ("hinh su", "bộ luật hình sự"),
        ("bo luat to tung dan su", "bộ luật tố tụng dân sự"),
        ("to tung dan su", "bộ luật tố tụng dân sự"),
        ("bo luat dan su", "bộ luật dân sự"),
        ("dan su", "bộ luật dân sự"),
        ("bo luat lao dong", "bộ luật lao động"),
        ("lao dong", "bộ luật lao động"),
        ("luat hon nhan va gia dinh", "luật hôn nhân và gia đình"),
        ("hon nhan", "luật hôn nhân và gia đình"),
        ("gia dinh", "luật hôn nhân và gia đình"),
        ("luat dat dai", "luật đất đai"),
        ("dat dai", "luật đất đai"),
        ("luat bao hiem xa hoi", "luật bảo hiểm xã hội"),
        ("bao hiem xa hoi", "luật bảo hiểm xã hội"),
        ("luat bao hiem y te", "luật bảo hiểm y tế"),
        ("bao hiem y te", "luật bảo hiểm y tế"),
        ("luat viec lam", "luật việc làm"),
        ("viec lam", "luật việc làm"),
        ("luat doanh nghiep", "luật doanh nghiệp"),
        ("doanh nghiep", "luật doanh nghiệp"),
        ("luat quan ly thue", "luật quản lý thuế"),
        ("quan ly thue", "luật quản lý thuế"),
        ("luat xay dung", "luật xây dựng"),
        ("xay dung", "luật xây dựng"),
        ("luat an toan ve sinh lao dong", "luật an toàn, vệ sinh lao động"),
        ("an toan ve sinh lao dong", "luật an toàn, vệ sinh lao động"),
    ]
    for marker, hint in law_hints:
        if marker in q:
            return hint
    return None


# ======================================
# LOAD SOURCE (ĐÃ SỬA CHUẨN)
# ======================================
def load_source(name: str):
    vec_path = DATA_DIR / name / "vectors.npy"
    meta_path = DATA_DIR / name / "meta.json"
    topic_path = DATA_DIR / name / "topic_centroids.npy"

    if not vec_path.exists() or not meta_path.exists():
        return None

    vectors = np.load(vec_path)
    if vectors.size == 0 or vectors.ndim != 2:
        print(f"[RAG] SKIP {name} → invalid vectors shape {vectors.shape}")
        return None

    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    if len(vectors) != len(meta):
        print(f"[RAG] SKIP {name} → vectors/meta mismatch {len(vectors)} vs {len(meta)}")
        return None

    if _is_stale_article_source(name, meta):
        print(f"[RAG] SKIP {name} → vector metadata is stale against current DB")
        return None

    topic_centroids = np.load(topic_path) if topic_path.exists() else None

    return {
        "name": name,
        "vectors": vectors,
        "meta": meta,
        "topic_centroids": topic_centroids
    }


def _is_stale_article_source(name: str, meta: list) -> bool:
    if not name.startswith("articles") or not meta:
        return False

    sample_ids = []
    for item in meta[:200]:
        article_id = item.get("article_id")
        if article_id:
            sample_ids.append(str(article_id))
        if len(sample_ids) >= 20:
            break

    if not sample_ids:
        return False

    try:
        from db_core import execute_query

        placeholders = ",".join(["%s"] * len(sample_ids))
        rows = execute_query(
            f"SELECT article_id FROM articles WHERE article_id IN ({placeholders})",
            tuple(sample_ids),
            fetchall=True,
        ) or []
        found = {str(row.get("article_id")) for row in rows}
        return len(found) == 0
    except Exception as e:
        print(f"[RAG] WARN: could not validate {name} against DB: {repr(e)}")
        return False

# BẢN VÁ SỐ 1: Tải đủ 3 nguồn độc lập thay vì gộp chung
ARTICLES = load_source("articles")
ARTICLES_CHUNKS = load_source("articles/chunks")
SIMPLIFIED = load_source("simplified")

# Fallback logic: Nếu ARTICLES missing, cảnh báo và tự động dùng ARTICLES_CHUNKS
if ARTICLES is None and ARTICLES_CHUNKS is not None:
    print("[RAG] WARNING: vector_store/articles/ missing. Falling back to articles/chunks only.")

# Lọc bỏ những nguồn bị None
SOURCES = list(filter(None, [ARTICLES, ARTICLES_CHUNKS, SIMPLIFIED]))

# Validate: Không được để SOURCES rỗng nếu ARTICLES_CHUNKS tồn tại
if not SOURCES and ARTICLES_CHUNKS is None:
    print("[RAG] CRITICAL: No vector sources available (articles, articles/chunks, simplified all missing)")


def get_source_by_name(name: str):
    for s in SOURCES:
        if s and s["name"] == name:
            return s
    return None


# ======================================
# SEMANTIC SEARCH
# ======================================
def semantic_retrieve(source, query_vec, top_k=20):
    if source is None:
        return []

    if query_vec is None:
        return []

    vectors = source.get("vectors")
    if vectors is None or vectors.ndim != 2 or vectors.size == 0:
        return []

    if query_vec.shape[0] != vectors.shape[1]:
        print(
            f"[RAG] SKIP {source['name']} -> embedding dim mismatch "
            f"query={query_vec.shape[0]} vectors={vectors.shape[1]}"
        )
        return []

    sims = cosine_similarity([query_vec], vectors)[0]
    idxs = np.argsort(sims)[::-1][:top_k]

    meta = source["meta"]
    results = []
    for i in idxs:
        results.append({
            "id": meta[i]["id"],
            "text": meta[i]["text"],
            "source": source["name"],
            "article_id": meta[i].get("article_id"), # THÊM DÒNG NÀY ĐỂ TRÁNH LỖI CONTEXT BUILDER
            "article_number": meta[i].get("article_number"),
            "article_title": meta[i].get("article_title"),
            "clause_number": meta[i].get("clause_number"),
            "law_title": meta[i].get("law_title"),
            "semantic_score": float(sims[i]),
            "topic_cluster": meta[i].get("topic_cluster", None)
        })
    return results


def _call_llm_rewrite(llm_rewrite_fn, prompt: str):
    try:
        result = llm_rewrite_fn(prompt, max_tokens=120, temperature=0.2)
    except TypeError:
        result = llm_rewrite_fn(prompt)
    return result


async def hyde_retrieve(query: str, llm_rewrite_fn, vector_store_source, top_k=8) -> list:
    """
    Hypothetical Document Embedding:
    1. Dùng LLM sinh một đoạn điều luật giả có thể trả lời query.
    2. Embed đoạn giả đó.
    3. Search vector store bằng vector của đoạn giả.
    """
    hyde_prompt = (
        f'Viết một đoạn điều luật Việt Nam ngắn (3-5 câu) '
        f'có thể trả lời câu hỏi: "{query}". '
        f'Viết đúng văn phong điều luật, không cần 100% chính xác.'
    )
    hypothetical_doc = _call_llm_rewrite(llm_rewrite_fn, hyde_prompt)
    if isawaitable(hypothetical_doc):
        hypothetical_doc = await hypothetical_doc
    if not hypothetical_doc or len(hypothetical_doc.strip()) < 20:
        return []
    hypo_vec = get_local_embedding(hypothetical_doc)
    if hypo_vec is None:
        return []
    results = semantic_retrieve(vector_store_source, hypo_vec, top_k=top_k)
    for item in results:
        item["hyde_score"] = item.get("semantic_score", 0.0)
    return results


def _result_from_meta(source, item, **scores):
    return {
        "id": item.get("id"),
        "text": item.get("text", ""),
        "source": source["name"],
        "article_id": item.get("article_id"),
        "article_number": item.get("article_number"),
        "article_title": item.get("article_title"),
        "clause_number": item.get("clause_number"),
        "law_title": item.get("law_title"),
        "semantic_score": float(scores.get("semantic_score", 0.0) or 0.0),
        "bm25_score": float(scores.get("bm25_score", 0.0) or 0.0),
        "topic_cluster": item.get("topic_cluster", None),
    }


def _query_aspects(query: str) -> list[str]:
    """
    Split a natural question into smaller search aspects without legal hardcoding.

    Example: "hưởng bao lâu, có rút BHXH một lần không, nộp ở đâu" becomes
    separate lexical searches, so one sub-question does not drown out another.
    """
    q = _norm(query)
    if not q:
        return []

    parts = [q]
    for chunk in re.split(r"[?？;,.;]|(?:\s+-\s+)|(?:\s+—\s+)|(?:\s+và\s+)|(?:\s+hay\s+)|(?:\s+hoặc\s+)", q):
        chunk = chunk.strip(" -")
        if len(chunk) >= 12:
            parts.append(chunk)

    deduped = []
    seen = set()
    for part in parts:
        if part not in seen:
            deduped.append(part)
            seen.add(part)
    return deduped[:max(1, LEXICAL_MAX_ASPECTS)]


def _keyword_overlap_score(query: str, text: str) -> float:
    stop = {
        "tôi", "em", "anh", "chị", "bạn", "có", "không", "thì", "là", "và",
        "hay", "hoặc", "ở", "đâu", "như", "nào", "bao", "lâu", "được", "khi",
        "theo", "quy", "định", "của", "luật", "trong", "với", "cho"
    }
    q_tokens = {
        token for token in re.findall(r"[\wÀ-ỹ]+", _norm(query))
        if len(token) >= 3 and token not in stop
    }
    if not q_tokens:
        return 0.0
    text_l = _norm(text)
    hits = sum(1 for token in q_tokens if token in text_l)
    return min(hits / max(len(q_tokens), 1), 1.0)


def _title_overlap_score(query: str, title: str) -> float:
    # Title matches are more reliable than body matches for choosing the article.
    return _keyword_overlap_score(query, title) * 1.25


def _substantive_adjustment(query: str, title: str, text: str) -> float:
    """
    Prefer substantive rules over procedure when the user asks for legal rights.

    This is intentionally broad, not a per-question shortcut: procedural articles
    about jurisdiction/forms/fees are useful for "nộp ở đâu/thủ tục thế nào",
    but they should not outrank articles that define rights and legal outcomes.
    """
    q = _norm(query)
    title_l = _norm(title)
    blob = f"{title_l} {_norm(text)}"

    asks_substantive_answer = any(
        marker in q for marker in [
            "có được", "được không", "có quyền", "phải trả", "được hưởng",
            "bị xử lý", "có bị", "chia", "thuộc về", "của ai"
        ]
    )

    procedure_terms = [
        "thẩm quyền", "đơn yêu cầu", "án phí", "lệ phí", "tạm ứng",
        "hòa giải và công nhận", "thủ tục", "tố tụng", "thi hành án"
    ]
    substantive_terms = [
        "nguyên tắc", "điều kiện hưởng", "mức hưởng", "trách nhiệm",
        "nghĩa vụ", "quyền", "tài sản chung", "tài sản riêng",
        "đăng ký quyền sở hữu", "quyền sử dụng", "chấm dứt hợp đồng",
        "chia tài sản", "giải quyết tài sản"
    ]

    score = 0.0
    if asks_substantive_answer and any(term in title_l for term in procedure_terms):
        score -= 0.28
    if any(term in title_l for term in substantive_terms):
        score += 0.18

    # Avoid irrelevant family/civil-law bleed-through such as inheritance when
    # the question is about divorce property, unless the user mentions death.
    if "ly hôn" in q and "tài sản" in q and "thừa kế" in title_l and not any(k in q for k in ["chết", "di sản", "thừa kế"]):
        score -= 0.35

    if "ly hôn" in q and "tài sản" in q:
        if not any(term in blob for term in ["ly hôn", "vợ chồng", "hôn nhân", "gia đình"]):
            score -= 0.85
        if "khi ly hôn" in title_l:
            score += 0.45
        if "nguyên tắc giải quyết tài sản" in title_l:
            score += 0.35
        if "chia quyền sử dụng đất" in title_l:
            score += 0.25
        if "tài sản chung của vợ" in title_l or "tài sản riêng của vợ" in title_l:
            score += 0.25
        if "trong thời kỳ hôn nhân" in title_l and "thời kỳ hôn nhân" not in q:
            score -= 0.22
        if "sống chung với gia đình" in title_l and not any(k in q for k in ["sống chung", "gia đình chồng", "gia đình vợ"]):
            score -= 0.22
        if any(term in title_l for term in ["bị tuyên bố là đã chết", "thừa kế"]) and not any(k in q for k in ["chết", "di sản", "thừa kế"]):
            score -= 0.25

    unregistered_union = any(
        term in q for term in [
            "không đăng ký kết hôn", "khong dang ky ket hon",
            "chung sống không đăng ký", "chung song khong dang ky",
            "sống chung không đăng ký", "song chung khong dang ky",
        ]
    )
    if unregistered_union:
        if any(term in blob for term in ["nam, nữ chung sống với nhau như vợ chồng", "không đăng ký kết hôn"]):
            score += 1.25
        if "giải quyết quan hệ tài sản" in title_l:
            score += 0.65
        if any(term in title_l for term in ["khi ly hôn", "của vợ chồng khi ly hôn"]):
            score -= 0.55

    if "đứng tên" in q and any(term in blob for term in ["đăng ký quyền sở hữu", "giấy chứng nhận", "chỉ ghi tên một bên"]):
        score += 0.35

    return score


def lexical_retrieve(source, query: str, top_k=LEXICAL_TOP_K):
    """
    BM25-first retrieval over the full vector-store metadata.

    This is the general safety net: new legal topics should be found by terms in
    the database/vector store instead of adding a new hand-written anchor every
    time a user asks something new.
    """
    if source is None:
        return []

    meta = source.get("meta") or []
    by_id = {item.get("id"): item for item in meta if item.get("id")}
    merged = {}

    for aspect in _query_aspects(query):
        try:
            matches = bm25_search(source["name"], aspect, top_k=top_k)
        except Exception:
            matches = []

        for match in matches:
            item = by_id.get(match.get("id"))
            if not item:
                continue
            key = item.get("id")
            candidate = _result_from_meta(source, item, bm25_score=match.get("bm25_score", 0.0))
            candidate["lexical_aspect"] = aspect
            if key not in merged or candidate["bm25_score"] > merged[key].get("bm25_score", 0.0):
                merged[key] = candidate

    return sorted(merged.values(), key=lambda x: x.get("bm25_score", 0.0), reverse=True)[:top_k]


def _article_catalog(source):
    catalog = source.get("_article_catalog")
    if catalog is not None:
        return catalog

    grouped = {}
    for item in source.get("meta") or []:
        key = item.get("article_id") or (item.get("article_number"), item.get("law_title"))
        if not key:
            continue
        entry = grouped.setdefault(
            key,
            {
                "item": item,
                "texts": [],
            },
        )
        if len(entry["texts"]) < 6:
            text = _norm(item.get("text"))
            if text:
                entry["texts"].append(text)

    catalog = []
    for entry in grouped.values():
        item = entry["item"]
        law_title = _norm(item.get("law_title"))
        article_title = _norm(item.get("article_title"))
        combined_text = " ".join(entry["texts"])
        catalog.append({
            "item": item,
            "search_text": f"{law_title} {law_title} {article_title} {article_title} {combined_text}",
        })

    source["_article_catalog"] = catalog
    return catalog


def article_lexical_retrieve(source, query: str, top_k=ARTICLE_LEXICAL_TOP_K):
    """
    Fast article-level lexical retrieval using metadata already loaded in memory.

    This avoids full BM25 scoring over every chunk on every request, while still
    giving the ranker legal articles whose titles/body terms match the question.
    """
    if source is None:
        return []

    q = _norm(query)
    results = []
    for entry in _article_catalog(source):
        item = entry["item"]
        search_text = entry["search_text"]
        if "ly hôn" in q and not any(term in search_text for term in ["ly hôn", "vợ chồng", "hôn nhân", "gia đình"]):
            continue
        overlap = _keyword_overlap_score(query, search_text)
        title_text = f"{item.get('law_title', '')} {item.get('article_title', '')}"
        title_score = _title_overlap_score(query, title_text)
        substantive = _substantive_adjustment(query, title_text, search_text)
        score = overlap + title_score + substantive
        if score <= 0:
            continue

        candidate = _result_from_meta(source, item)
        candidate["article_lexical_score"] = score
        candidate["final_score"] = score
        results.append(candidate)

    return sorted(results, key=lambda x: x.get("article_lexical_score", 0.0), reverse=True)[:top_k]


# ======================================
# SUBJECT
# ======================================
def detect_subject(query):
    q = query.lower()
    nld = ["tôi", "em", "người lao động", "nhân viên"]
    nsdld = ["công ty", "doanh nghiệp", "sếp", "quản lý"]

    for w in nld:
        if w in q:
            return "nld"
    for w in nsdld:
        if w in q:
            return "nsdld"
    return "unknown"


def subject_score(text, subject):
    t = text.lower()
    if subject == "nld" and "người lao động" in t:
        return 0.1
    if subject == "nsdld" and "người sử dụng lao động" in t:
        return 0.1
    return 0.0


# ======================================
# RANK SCORE (ĐÃ VÁ LỖI)
# ======================================
SOURCE_PRIORITY = {
    "articles/chunks": 0.12,
    "articles": 0.10,
    "simplified": 0.02
}

def _norm(text: str) -> str:
    return re.sub(r"\s+", " ", str(text or "").lower()).strip()


def fusion_rank(query, query_vec, sem_results):
    subject = detect_subject(query)
    fused = []

    for r in sem_results:
        src = r["source"]

        # BẢN VÁ SỐ 2: ĐÃ XÓA BỎ LỆNH CẤM LUẬT THI HÀNH Ở ĐÂY
        # Hệ thống giờ đây sẽ nhận diện mọi bộ luật một cách bình đẳng.

        semantic_score = float(r.get("semantic_score", 0.0) or 0.0)
        bm25_score = float(r.get("bm25_score", 0.0) or 0.0)
        bm25_component = min(max(bm25_score, 0.0), 12.0) / 12.0

        subject_bonus = subject_score(r["text"], subject)
        overlap_score = _keyword_overlap_score(query, r.get("text", ""))
        title_text = f"{r.get('law_title', '')} {r.get('article_title', '')}"
        title_score = _title_overlap_score(query, title_text)
        substantive_score = _substantive_adjustment(query, title_text, r.get("text", ""))
        article_lexical_component = min(float(r.get("article_lexical_score", 0.0) or 0.0), 2.0) / 2.0
        topic_boost_score = topic_boost(query.lower(), str(r.get("text", "")).lower())
        priority = SOURCE_PRIORITY.get(src, 0.0)

        final_score = (
            0.45 * semantic_score +
            0.35 * bm25_component +
            0.12 * overlap_score +
            0.16 * title_score +
            0.25 * article_lexical_component +
            substantive_score +
            subject_bonus +
            topic_boost_score +
            priority
        )

        fused.append({
            **r,
            "bm25_score": bm25_score,
            "overlap_score": overlap_score,
            "title_score": title_score,
            "substantive_score": substantive_score,
            "article_lexical_score": r.get("article_lexical_score", 0.0),
            "final_score": final_score,
        })

    fused = sorted(fused, key=lambda x: x["final_score"], reverse=True)
    return fused[:FINAL_TOP_K]


def _merge_and_rank(query: str, *result_groups):
    unranked = []
    ranked = []
    for group in result_groups:
        for item in group or []:
            if "final_score" in item:
                ranked.append(item)
            else:
                unranked.append(item)

    if unranked:
        ranked.extend(fusion_rank(query, None, unranked))

    merged = {}
    for item in ranked:
        key = (item.get("source"), item.get("article_id"), item.get("id"))
        if key not in merged or item.get("final_score", 0) > merged[key].get("final_score", 0):
            merged[key] = item

    return aggregate_by_article(
        query,
        sorted(merged.values(), key=lambda x: x.get("final_score", 0), reverse=True)
    )


def _needs_lexical_fallback(ranked_results) -> bool:
    if not ranked_results:
        return True
    top = ranked_results[0]
    if float(top.get("article_lexical_score", 0.0) or 0.0) >= 0.45:
        return False
    if float(top.get("final_score", 0.0) or 0.0) >= 1.0:
        return False
    if float(top.get("semantic_score", 0.0) or 0.0) >= 0.82:
        return False
    if top.get("bm25_score", 0.0) > 0:
        return False
    return float(top.get("overlap_score", 0.0) or 0.0) < LEXICAL_FALLBACK_MIN_OVERLAP


def _article_key(item):
    return (item.get("source"), item.get("article_id") or item.get("article_number") or item.get("id"))


def aggregate_by_article(query: str, ranked_results):
    """
    Convert chunk-level results into article-level results.

    Most legal answers need the correct article, not the single best matching
    sentence. Aggregating prevents multiple chunks from one noisy/procedural
    article from crowding out substantive articles that answer the question.
    """
    grouped = {}
    for item in ranked_results or []:
        key = _article_key(item)
        if not key:
            continue

        current = grouped.get(key)
        if current is None:
            grouped[key] = {
                "best": item,
                "count": 1,
                "score_sum": float(item.get("final_score", 0.0) or 0.0),
                "max_score": float(item.get("final_score", 0.0) or 0.0),
                "max_bm25": float(item.get("bm25_score", 0.0) or 0.0),
                "max_semantic": float(item.get("semantic_score", 0.0) or 0.0),
            }
            continue

        current["count"] += 1
        current["score_sum"] += float(item.get("final_score", 0.0) or 0.0)
        current["max_bm25"] = max(current["max_bm25"], float(item.get("bm25_score", 0.0) or 0.0))
        current["max_semantic"] = max(current["max_semantic"], float(item.get("semantic_score", 0.0) or 0.0))
        if float(item.get("final_score", 0.0) or 0.0) > current["max_score"]:
            current["best"] = item
            current["max_score"] = float(item.get("final_score", 0.0) or 0.0)

    articles = []
    for group in grouped.values():
        best = dict(group["best"])
        evidence_bonus = min(group["count"], 4) * 0.05
        title_bonus = _title_overlap_score(
            query,
            f"{best.get('law_title', '')} {best.get('article_title', '')}"
        ) * 0.10
        article_score = group["max_score"] + evidence_bonus + title_bonus
        best["final_score"] = article_score
        best["article_evidence_count"] = group["count"]
        best["bm25_score"] = max(float(best.get("bm25_score", 0.0) or 0.0), group["max_bm25"])
        best["semantic_score"] = max(float(best.get("semantic_score", 0.0) or 0.0), group["max_semantic"])
        articles.append(best)

    return sorted(articles, key=lambda x: x.get("final_score", 0.0), reverse=True)[:FINAL_TOP_K]


def _active_hyde_rewrite_fn():
    provider = os.getenv("AI_PROVIDER", "gemini").lower().strip()
    if provider == "groq":
        from ai.groq_service import rewrite_legal_query
    else:
        from ai.gemini_service import rewrite_legal_query
    return rewrite_legal_query


def _run_hyde_retrieve_sync(query: str, source, top_k: int = 8):
    if source is None:
        return []
    rewrite_fn = _active_hyde_rewrite_fn()
    try:
        return asyncio.run(hyde_retrieve(query, rewrite_fn, source, top_k=top_k))
    except RuntimeError:
        loop = asyncio.new_event_loop()
        try:
            return loop.run_until_complete(hyde_retrieve(query, rewrite_fn, source, top_k=top_k))
        finally:
            loop.close()
    except Exception as e:
        print(f"[RAG] HyDE unavailable for {source.get('name')}: {repr(e)}")
        return []


def _candidate_ids_for_source(source, *result_groups, limit: int = 160):
    source_name = source.get("name")
    ids = []
    seen = set()
    for group in result_groups:
        for item in group or []:
            if item.get("source") != source_name:
                continue
            doc_id = item.get("id")
            if not doc_id or doc_id in seen:
                continue
            seen.add(doc_id)
            ids.append(doc_id)
            if len(ids) >= limit:
                return ids
    return ids


def _bm25_rerank_source(source, query: str, candidate_ids, top_k: int = 20):
    if source is None or not candidate_ids:
        return []

    meta = source.get("meta") or []
    by_id = {item.get("id"): item for item in meta if item.get("id")}
    try:
        matches = bm25_rerank_candidates(source["name"], query, candidate_ids, top_k=top_k)
    except Exception as e:
        print(f"[RAG] BM25 candidate rerank unavailable for {source.get('name')}: {repr(e)}")
        return []

    results = []
    for match in matches:
        item = by_id.get(match.get("id"))
        if item:
            results.append(_result_from_meta(source, item, bm25_score=match.get("bm25_score", 0.0)))
    return results


# ======================================
# MAIN RETRIEVAL
# ======================================
def retrieve_multi_source(query: str, source_filter="all"):

    mapping = {
        "laws": "articles/chunks",
        "content": "simplified",
        "all": "all"
    }

    selected_source = mapping.get(source_filter, "all")

    # 1️⃣ Điều X
    article_no = detect_article_number(query)
    if article_no:
        law_hint = detect_law_hint(query)
        print(f"🔥 DIRECT ARTICLE MATCH: Điều {article_no}")
        return [{
            "article_number": article_no,
            "law_hint": law_hint,
            "law_title": law_hint,
            "source": "articles",
            "text": "",
            "final_score": 999
        }]

    # 2️⃣ Semantic retrieval over vector store, when the embedding model is available.
    query_vec = None
    if ENABLE_SEMANTIC_SEARCH:
        try:
            query_vec = get_local_embedding(query)
        except Exception as e:
            print(f"[RAG] Semantic search unavailable, using lexical-only retrieval: {repr(e)}")

    sem_results = []
    ranked = []

    if query_vec is not None:
        for source in SOURCES:
            if not source:
                continue

            if selected_source != "all" and source["name"] != selected_source:
                if not (selected_source == "articles/chunks" and source["name"] == "articles"):
                    continue

            sem_results += semantic_retrieve(source, query_vec, top_k=SEMANTIC_TOP_K)

        ranked = fusion_rank(query, query_vec, sem_results)

    # 3️⃣ Fast article-level lexical retrieval over loaded vector metadata.
    lexical_results = []
    for source in SOURCES:
        if not source:
            continue

        if selected_source != "all" and source["name"] != selected_source:
            if not (selected_source == "articles/chunks" and source["name"] == "articles"):
                continue

        lexical_results += article_lexical_retrieve(source, query)

    ranked_with_article_lexical = _merge_and_rank(query, ranked, lexical_results) if lexical_results else ranked

    # 4️⃣ Hard-query fallback: HyDE + BM25 rerank only over known candidates.
    bm25_results = []
    hyde_results = []
    if ALWAYS_RUN_LEXICAL or query_vec is None or _needs_lexical_fallback(ranked_with_article_lexical):
        selected_sources = []
        for source in SOURCES:
            if not source:
                continue

            if selected_source != "all" and source["name"] != selected_source:
                if not (selected_source == "articles/chunks" and source["name"] == "articles"):
                    continue

            selected_sources.append(source)

        with ThreadPoolExecutor(max_workers=min(max(len(selected_sources) * 2, 1), 6)) as executor:
            futures = []
            for source in selected_sources:
                candidate_ids = _candidate_ids_for_source(
                    source,
                    ranked_with_article_lexical,
                    ranked,
                    lexical_results,
                    sem_results,
                )
                if candidate_ids:
                    futures.append(executor.submit(_bm25_rerank_source, source, query, candidate_ids, 20))
                futures.append(executor.submit(_run_hyde_retrieve_sync, query, source, 8))

            for future in as_completed(futures):
                try:
                    items = future.result()
                except Exception as e:
                    print(f"[RAG] fallback task failed: {repr(e)}")
                    continue
                if not items:
                    continue
                if any("hyde_score" in item for item in items):
                    hyde_results += items
                else:
                    bm25_results += items

    # 5️⃣ Fusion rank + article-level aggregation.
    if lexical_results or bm25_results or hyde_results:
        return _merge_and_rank(query, ranked, lexical_results, hyde_results, bm25_results)

    return aggregate_by_article(query, ranked)
