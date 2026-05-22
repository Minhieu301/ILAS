from db_core import execute_query
import re
import traceback
import json
from pathlib import Path

VECTOR_META_PATH = Path(__file__).resolve().parents[1] / "vector_store" / "articles" / "chunks" / "meta.json"
MAX_CONTEXT_ARTICLES = 6
MAX_LAWS_PER_SOURCE = 2
MAX_CONTEXT_CHARS = 3500


def _normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", str(text or "")).strip()


def _load_chunks_meta():
    try:
        with open(VECTOR_META_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except Exception:
        return []


def load_article_from_vector_store(article_id=None, article_number=None) -> str:
    """
    Fallback when MySQL is unavailable.

    The vector store has clause/chunk-level text, so we can still build a useful
    article context by joining chunks with the same article_id. This prevents a
    good retrieval result from degrading into a general LLM fallback.
    """
    chunks = []
    for item in _load_chunks_meta():
        if article_id and str(item.get("article_id") or "") != str(article_id):
            continue
        if not article_id and article_number and str(item.get("article_number") or "") != str(article_number):
            continue
        chunks.append(item)

    if not chunks:
        return None

    law_title = chunks[0].get("law_title") or "Nguồn vector ILAS"
    article_title = chunks[0].get("article_title") or f"Điều {article_number}"
    article_no = chunks[0].get("article_number") or article_number or "?"
    texts = []
    seen = set()
    for item in chunks:
        text = _normalize_space(item.get("text"))
        if text and text not in seen:
            texts.append(text)
            seen.add(text)

    if not texts:
        return None

    return f"[{law_title}]\nĐiều {article_no}. {article_title}\n\n" + "\n".join(texts)

def load_full_article(article_id: str) -> str:
    # Truy vấn chuẩn mới (Dùng ID)
    query = """
        SELECT l.title as law_name, a.article_title, a.content 
        FROM articles a
        JOIN laws l ON a.law_id = l.law_id
        WHERE a.article_id = %s
        LIMIT 1
    """
    try:
        row = execute_query(query, (article_id,), fetchone=True)
        if not row or row == {}:
            print(f"[WARN] DB returned empty for article_id={article_id}. Check DB connection or data import.")
            return None
        return f"[{row['law_name']}]\n{row['article_title']}\n\n{row['content']}"
    except Exception as e:
        print(f"[ERROR] load_full_article exception for article_id={article_id}:")
        print(f"  {type(e).__name__}: {str(e)[:100]}")
        traceback.print_exc()
        return None

def load_full_article_by_number(article_number: str, law_hint: str = None) -> str:
    try:
        if law_hint:
            hinted_query = """
                SELECT l.title as law_name, a.article_title, a.content
                FROM articles a
                JOIN laws l ON a.law_id = l.law_id
                WHERE a.article_number = %s
                  AND LOWER(l.title) LIKE %s
                ORDER BY a.status = 'active' DESC, l.law_id ASC, a.article_id ASC
                LIMIT 1
            """
            row = execute_query(hinted_query, (article_number, f"%{law_hint.lower()}%"), fetchone=True)
            if row and row != {}:
                return f"[{row['law_name']}]\n{row['article_title']}\n\n{row['content']}"

        fallback_query = """
            SELECT l.title as law_name, a.article_title, a.content
            FROM articles a
            JOIN laws l ON a.law_id = l.law_id
            WHERE a.article_number = %s
            ORDER BY a.status = 'active' DESC, a.law_id ASC, a.article_id ASC
            LIMIT 1
        """
        row = execute_query(fallback_query, (article_number,), fetchone=True)

        if not row or row == {}:
            print(f"[WARN] DB returned empty for article_number={article_number}. Check DB connection or data import.")
            return None
        return f"[{row['law_name']}]\n{row['article_title']}\n\n{row['content']}"
    except Exception as e:
        print(f"[ERROR] load_full_article_by_number exception for article_number={article_number}:")
        print(f"  {type(e).__name__}: {str(e)[:100]}")
        traceback.print_exc()
        return None


def load_full_article_by_number_legacy(article_number: str) -> str:
    # Kept only as a reference for older call sites; build_context uses
    # load_full_article_by_number(article_number, law_hint).
    preferred_query = """
        SELECT l.title as law_name, a.article_title, a.content
        FROM articles a
        JOIN laws l ON a.law_id = l.law_id
        WHERE a.article_number = %s AND a.law_id = 1
        LIMIT 1
    """
    try:
        row = execute_query(preferred_query, (article_number,), fetchone=True)

        if not row or row == {}:
            fallback_query = """
                SELECT l.title as law_name, a.article_title, a.content
                FROM articles a
                JOIN laws l ON a.law_id = l.law_id
                WHERE a.article_number = %s
                ORDER BY a.law_id ASC
                LIMIT 1
            """
            row = execute_query(fallback_query, (article_number,), fetchone=True)

        if not row or row == {}:
            print(f"[WARN] DB returned empty for article_number={article_number}. Check DB connection or data import.")
            return None
        return f"[{row['law_name']}]\n{row['article_title']}\n\n{row['content']}"
    except Exception as e:
        print(f"[ERROR] load_full_article_by_number exception for article_number={article_number}:")
        print(f"  {type(e).__name__}: {str(e)[:100]}")
        traceback.print_exc()
        return None


def _extract_relevant_excerpt(content: str, query: str, max_chars: int = 600) -> str:
    """
    Tách đoạn trong điều luật chứa nhiều từ khóa query nhất.
    Không dump toàn bộ nội dung để giảm token gửi sang LLM.
    """
    if not content or len(content) <= max_chars:
        return content

    query_words = set(re.findall(r"[\wÀ-ỹ]+", str(query or "").lower()))
    parts = re.split(r"(?=\d+\.\s|[a-zđ]\)\s)", content)

    scored = []
    for i, part in enumerate(parts):
        if len(part.strip()) < 20:
            continue
        part_words = set(re.findall(r"[\wÀ-ỹ]+", part.lower()))
        overlap = len(query_words & part_words)
        scored.append((overlap, i, part))

    if not scored:
        return content[:max_chars]

    scored.sort(key=lambda x: x[0], reverse=True)
    top_parts = sorted(scored[:2], key=lambda x: x[1])
    excerpt = " ".join(p[2].strip() for p in top_parts)

    return excerpt[:max_chars]


def _limit_article_context(context: str, query_hint: str) -> str:
    if not context:
        return context

    header, sep, body = context.partition("\n\n")
    if not sep:
        return _extract_relevant_excerpt(context, query_hint)

    excerpt = _extract_relevant_excerpt(body, query_hint)
    return f"{header}\n\n{excerpt}".strip()


def build_context(results, query=""):
    if not results:
        return None

    contexts = []
    seen_articles = set()
    seen_laws = {}

    for top in results:
        if len(contexts) >= MAX_CONTEXT_ARTICLES:
            break

        if top.get("source") not in ["articles", "articles/chunks"]:
            continue

        article_id = top.get("article_id")

        if not article_id and top.get("id"):
            match = re.search(r"art_(\d+)", top.get("id"))
            if match:
                article_id = match.group(1)

        article_number = top.get("article_number")
        law_title = top.get("law_title")
        law_hint = top.get("law_hint")
        if law_title and seen_laws.get(law_title, 0) >= MAX_LAWS_PER_SOURCE:
            continue

        dedupe_keys = []
        if article_id:
            dedupe_keys.append(("id", str(article_id)))
        if article_number or law_title:
            dedupe_keys.append(("number_title", str(article_number or ""), str(law_title or "")))
        if not dedupe_keys or any(key in seen_articles for key in dedupe_keys):
            continue

        context = None
        if article_id:
            context = load_full_article(article_id)
            if context is None:
                context = load_article_from_vector_store(article_id=article_id)

        if context is None and article_number:
            context = load_full_article_by_number(article_number, law_hint=law_hint)
            if context is None:
                context = load_article_from_vector_store(article_number=article_number)

        if context:
            contexts.append(_limit_article_context(context, query))
            seen_articles.update(dedupe_keys)
            if law_title:
                seen_laws[law_title] = seen_laws.get(law_title, 0) + 1

    if not contexts:
        print(f"[WARN] build_context failed: no DB/vector rows found. top_result={results[0] if results else None}")
        return None

    combined = "\n\n---\n\n".join(contexts)
    if len(combined) > MAX_CONTEXT_CHARS:
        combined = combined[:MAX_CONTEXT_CHARS].rsplit("\n", 1)[0].rstrip()
    return combined
