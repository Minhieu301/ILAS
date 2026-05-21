# ai/bm25_index.py
from pathlib import Path
import json
from typing import Dict, List, Any
from rank_bm25 import BM25Okapi
import pickle
import os
import json
import numpy as np
DATA_DIR = Path(__file__).resolve().parents[1] / "vector_store"
INDEX_DIR = Path(__file__).resolve().parents[1] / "bm25_index"
INDEX_DIR.mkdir(exist_ok=True)


def tokenize(text: str) -> List[str]:
    """
    Tạm thời split theo khoảng trắng.
    Sau này có thể thay bằng tokenizer tiếng Việt tốt hơn.
    """
    return (text or "").lower().split()


def _load_meta(source: str) -> List[Dict[str, Any]]:
    meta_path = DATA_DIR / source / "meta.json"
    # Thêm 3 dòng này để kiểm tra file, không có thì bỏ qua an toàn
    if not os.path.exists(meta_path):
        print(f"[BM25] Bỏ qua {source} vì không tìm thấy meta.json")
        return []
    with open(meta_path, "r", encoding="utf-8") as f:
        return json.load(f)


# Tạo tên file BM25 an toàn
def _index_filename(source: str):
    # "articles/chunks" -> "articles_chunks_bm25.pkl"
    safe = source.replace("/", "_")
    return INDEX_DIR / f"{safe}_bm25.pkl"


def build_bm25_for_source(source: str):
    meta = _load_meta(source)

    # Nếu nguồn không có dữ liệu thì bỏ qua
    if not meta:
        print(f"[BM25] Skip {source} (no documents)")
        return

    corpus_tokens: List[List[str]] = []
    ids: List[str] = []

    for idx, item in enumerate(meta):
        # Đảm bảo có id
        if "id" not in item:
            item["id"] = f"{source}_{idx}"

        text = item.get("text") or item.get("content") or ""
        tokens = item.get("tokens") or tokenize(text)

        corpus_tokens.append(tokens)
        ids.append(item["id"])
        item["tokens"] = tokens

    # Lưu lại meta.json với token đã thêm
    meta_path = DATA_DIR / source / "meta.json"
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    if len(corpus_tokens) == 0:
        print(f"[BM25] Skip {source} (empty corpus)")
        return

    bm25 = BM25Okapi(corpus_tokens)

    # Dùng tên file an toàn
    with open(_index_filename(source), "wb") as f:
        pickle.dump({"bm25": bm25, "ids": ids}, f)

    print(f"[BM25] Built index for {source}: {len(ids)} docs")


def build_all_bm25():
    sources = [
        "articles",
        "articles/chunks",     # thêm mới
        "faq",
        "simplified"
    ]

    for src in sources:
        build_bm25_for_source(src)


def bm25_search(source: str, query: str, top_k: int = 50) -> List[Dict[str, Any]]:
    # Tự động dùng file index an toàn
    with open(_index_filename(source), "rb") as f:
        data = pickle.load(f)

    bm25: BM25Okapi = data["bm25"]
    ids: List[str] = data["ids"]

    tokens = tokenize(query)
    scores = bm25.get_scores(tokens)
    if len(scores) == 0:
        return []

    k = min(max(int(top_k), 1), len(scores))
    top_idxs = np.argpartition(scores, -k)[-k:]
    top_idxs = top_idxs[np.argsort(scores[top_idxs])[::-1]]

    return [{"id": ids[int(i)], "bm25_score": float(scores[int(i)])} for i in top_idxs]


def bm25_rerank_candidates(source_name: str, query: str, candidate_ids, top_k: int = 20) -> List[Dict[str, Any]]:
    """
    Score only known candidate ids with BM25.

    This is used after semantic/article lexical retrieval has already narrowed
    the search space, avoiding a full-corpus BM25 fallback for hard questions.
    """
    if not candidate_ids:
        return []

    with open(_index_filename(source_name), "rb") as f:
        data = pickle.load(f)

    bm25: BM25Okapi = data["bm25"]
    ids: List[str] = data["ids"]
    id_to_idx = {doc_id: idx for idx, doc_id in enumerate(ids)}

    seen = set()
    idxs = []
    rerank_ids = []
    for doc_id in candidate_ids:
        if doc_id in seen:
            continue
        seen.add(doc_id)
        idx = id_to_idx.get(doc_id)
        if idx is None:
            continue
        idxs.append(idx)
        rerank_ids.append(doc_id)

    if not idxs:
        return []

    tokens = tokenize(query)
    if hasattr(bm25, "get_batch_scores"):
        scores = bm25.get_batch_scores(tokens, idxs)
    else:
        scores = []
        for idx in idxs:
            doc_score = 0.0
            doc_len = bm25.doc_len[idx]
            for token in tokens:
                freq = bm25.doc_freqs[idx].get(token, 0)
                if freq == 0:
                    continue
                idf = bm25.idf.get(token, 0.0)
                denom = freq + bm25.k1 * (1 - bm25.b + bm25.b * doc_len / bm25.avgdl)
                doc_score += idf * (freq * (bm25.k1 + 1) / denom)
            scores.append(doc_score)

    ranked = sorted(
        (
            {"id": rerank_ids[i], "bm25_score": float(score)}
            for i, score in enumerate(scores)
        ),
        key=lambda item: item["bm25_score"],
        reverse=True,
    )
    return ranked[: max(int(top_k), 1)]


def bm25_score_map(source: str, query: str, top_k: int = 200) -> Dict[str, float]:
    """
    Return BM25 scores keyed by document id.

    Retrieval fusion needs the BM25 score for the same document that semantic
    search returned. The old code used the top BM25 score of the whole source
    for every semantic result, which inflated unrelated chunks.
    """
    try:
        return {item["id"]: item["bm25_score"] for item in bm25_search(source, query, top_k=top_k)}
    except Exception:
        return {}


if __name__ == "__main__":
    build_all_bm25()
