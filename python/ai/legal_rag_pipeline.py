# ai/legal_rag_pipeline.py
# -------------------------------------------------------
# LEGAL RAG LEVEL 8 – FULL ARTICLE CONTEXT + HYBRID MODE
# -------------------------------------------------------

import os
import hashlib
import time
import unicodedata
from pathlib import Path
from dotenv import load_dotenv
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from collections import OrderedDict

# Load .env from project root
env_path = Path(__file__).parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

from ai.retrieval_level6 import retrieve_multi_source
from ai.context_builder import build_context
from db_core import check_data_health

RETRIEVAL_TIMEOUT_SEC = int(os.getenv("RETRIEVAL_TIMEOUT_SEC", "90"))
_QUERY_CACHE = OrderedDict()
_CACHE_MAX = 200
_CACHE_TTL = 3600

# DYNAMIC MODEL SELECTION
AI_PROVIDER = os.getenv("AI_PROVIDER", "gemini").lower().strip()

if AI_PROVIDER == "groq":
    from ai.groq_service import guarded_completion, fallback_general_answer, rewrite_legal_query
    _ACTIVE_PROVIDER = "Groq"
else:
    # Default to gemini
    from ai.gemini_service import guarded_completion, fallback_general_answer, rewrite_legal_query
    _ACTIVE_PROVIDER = "Gemini"

print(f"🤖 Active completion provider: {_ACTIVE_PROVIDER}")

# ======== STARTUP: Check data health ========
_health = check_data_health()
print(f"[STARTUP] DB alive: {_health['db_alive']} | articles: {_health['articles_count']} | laws: {_health['laws_count']}")
if _health['articles_count'] == 0 or _health['laws_count'] == 0:
    print("⚠️  WARNING: Data might not be imported. Run data import if needed.")


def _normalize_query(text: str) -> str:
    return unicodedata.normalize("NFC", str(text or "").strip())


def _cache_key(query: str) -> str:
    normalized = " ".join(query.lower().strip().split())
    return hashlib.md5(normalized.encode("utf-8")).hexdigest()


def _cache_get(query: str):
    key = _cache_key(query)
    if key in _QUERY_CACHE:
        result, ts = _QUERY_CACHE[key]
        if time.time() - ts < _CACHE_TTL:
            _QUERY_CACHE.move_to_end(key)
            return result
        del _QUERY_CACHE[key]
    return None


def _cache_set(query: str, result: dict):
    key = _cache_key(query)
    _QUERY_CACHE[key] = (result, time.time())
    _QUERY_CACHE.move_to_end(key)
    if len(_QUERY_CACHE) > _CACHE_MAX:
        _QUERY_CACHE.popitem(last=False)


def _truncate_text(text: str, max_length: int = 280) -> str:
    cleaned = str(text or "").strip()
    if len(cleaned) <= max_length:
        return cleaned
    return cleaned[: max_length - 3].rstrip() + "..."


def _build_conversation_context(history) -> str:
    if not history:
        return ""

    lines = []
    for item in list(history)[-4:]:
        if not isinstance(item, dict):
            continue

        # History items use 'content' for the user's message
        question = _truncate_text(item.get("content", ""))
        answer = _truncate_text(item.get("answer", ""))

        if question:
            lines.append(f"Người dùng: {question}")
        if answer:
            lines.append(f"Trợ lý: {answer}")

    return "\n".join(lines)


def _should_rewrite_query(query: str, conversation_context: str) -> bool:
    q = query.strip().lower()
    if not q or not conversation_context:
        return False
    follow_up_markers = [
        "khoản đó",
        "điều đó",
        "trường hợp này",
        "cái đó",
        "ý đó",
        "nói rõ hơn",
        "thế còn",
        "còn nếu",
        "phần trên",
        "mục trên",
    ]
    return any(marker in q for marker in follow_up_markers)


def _insufficient_context_answer() -> str:
    return (
        "Rất tiếc, theo dữ liệu hiện tại của hệ thống ILAS, tôi chưa tìm thấy đủ "
        "căn cứ pháp luật phù hợp để trả lời chắc chắn vấn đề này. Bạn có thể thử "
        "hỏi lại với thông tin cụ thể hơn như loại hợp đồng, ngày hết hạn hợp đồng, "
        "thời điểm nghỉ thai sản và thời gian đã đóng bảo hiểm thất nghiệp."
    )


def _build_source_payload(results, limit: int = 5):
    sources = []
    chunks = []
    seen = set()
    for item in results or []:
        if item.get("source") not in ["articles", "articles/chunks"]:
            continue
        article_number = item.get("article_number")
        source_title = item.get("law_title")
        key = (article_number, source_title)
        if key in seen:
            continue
        seen.add(key)
        label = f"Điều {article_number} - {source_title}" if article_number else str(source_title)
        sources.append(label)
        snippet = _truncate_text(item.get("text", ""), 360)
        chunks.append(f"{label}: {snippet}" if snippet else label)
        if len(sources) >= limit:
            break
    return sources, chunks


def _append_source_summary(answer: str, sources) -> str:
    if not sources:
        return answer
    if "Căn cứ ILAS đã dùng" in str(answer or ""):
        return answer
    source_lines = "\n".join(f"- {source}" for source in sources[:6])
    return f"{str(answer or '').strip()}\n\n**Căn cứ ILAS đã dùng:**\n{source_lines}"


def answer_legal_question(query: str, settings: dict = None, history=None, conversation_id=None):
    if settings is None:
        settings = {}

    query = _normalize_query(query)
    conversation_context = _build_conversation_context(history)

    # 0.5) Check enabled
    if settings.get("enabled") is False:
        return {
            "answer": "⚠️ Chatbot hiện đang được Admin tạm thời vô hiệu hóa.",
            "context_used": None,
            "source": None,
            "fallback": True
        }

    """
    LEGAL RAG PIPELINE – LEVEL 8 (Full Article Context + Hybrid Mode)
    + Hỗ trợ Admin Settings (delay, datasource, temperature…)
    """

    # 0) Validate câu hỏi
    if not query or not query.strip():
        return {
            "answer": "Vui lòng nhập câu hỏi hợp lệ.",
            "context_used": None,
            "source": None,
            "fallback": False
        }

    # 1) Delay nếu Admin config
    delay = settings.get("responseDelay", 0)
    if isinstance(delay, (int, float)) and delay > 0:
        time.sleep(delay / 1000)

    # 2) Filter nguồn dữ liệu
    source_filter = settings.get("dataSource", "all")
    if _should_rewrite_query(query, conversation_context):
        optimized_query = rewrite_legal_query(query, conversation_context)
        search_query = optimized_query.strip() if isinstance(optimized_query, str) and optimized_query.strip() else query
    else:
        search_query = query

    if not history:
        cached = _cache_get(query)
        if cached:
            print("⚡ CACHE HIT")
            return cached

    print(f"🔍 Câu hỏi gốc: {query}")
    print(f"🚀 Câu tối ưu: {search_query}")

    try:
        # 3) Retrieval (có filter nguồn)
        executor = ThreadPoolExecutor(max_workers=1)
        future = executor.submit(retrieve_multi_source, search_query, source_filter)
        try:
            results = future.result(timeout=RETRIEVAL_TIMEOUT_SEC)
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

        print("\n===== DEBUG RETRIEVAL =====")
        print("TOP SOURCE:", results[0].get("source") if results else None)
        print("TOP ARTICLE_NUMBER:", results[0].get("article_number") if results else None)
        print("TOP RAW:", results[0] if results else None)
        print("============================\n")

        # Retrieval fail
        if not results:
            return {
                "answer": _insufficient_context_answer(),
                "context_used": None,
                "source": "insufficient-context",
                "sources": [],
                "chunks": [],
                "fallback": True
            }

        # 4) Build FULL ARTICLE CONTEXT
        context = build_context(results, query=query)

        if not context or len(context.strip()) == 0:
            return {
                "answer": _insufficient_context_answer(),
                "context_used": None,
                "source": "insufficient-context",
                "sources": [],
                "chunks": [],
                "fallback": True
            }

        # 5) STRICT LEGAL MODE
        try:
            temperature = settings.get("temperature", 0.15)
            max_tokens = settings.get("maxTokens", 900)

            answer = guarded_completion(
                context=context,
                question=query, # LƯU Ý: Chỗ này VẪN GIỮ NGUYÊN là 'query' gốc nhé
                conversation_context=conversation_context,
                history=history,
                temperature=float(temperature),
                max_tokens=int(max_tokens)
            )

            # If Gemini returns a known failure message, fallback to Groq automatically.
            if AI_PROVIDER == "gemini" and isinstance(answer, str):
                fail_markers = [
                    "AI không trả về",
                    "AI trả về kết quả rỗng",
                    "Hệ thống AI Gemini đang lỗi",
                    "Gemini fallback failed",
                    "JSON error",
                    "GEMINI_API_KEY"
                ]
                if any(marker in answer for marker in fail_markers):
                    try:
                        from ai.groq_service import guarded_completion as groq_guarded_completion
                        groq_answer = groq_guarded_completion(
                            context=context,
                            question=query,
                            conversation_context=conversation_context,
                            history=history,
                            temperature=float(temperature),
                            max_tokens=int(max_tokens)
                        )
                        if isinstance(groq_answer, str) and groq_answer.strip():
                            answer = groq_answer + "\n\n⚠️ *Ghi chú: Gemini lỗi, hệ thống đã tự chuyển sang Groq.*"
                    except Exception as fallback_err:
                        print("❌ GROQ FALLBACK ERROR:", repr(fallback_err))

        except Exception as e:
            print(f"❌ {_ACTIVE_PROVIDER.upper()} COMPLETION ERROR:", repr(e))
            return {
                "answer": "⚠️ Hệ thống AI gặp lỗi khi sinh câu trả lời. Vui lòng thử lại.",
                "context_used": None,
                "source": None,
                "fallback": True
            }

        # 6) Extracting source
        legal_sources, legal_chunks = _build_source_payload(results)
        answer_with_sources = _append_source_summary(answer, legal_sources)

        result = {
            "answer": answer_with_sources,
            "context_used": "; ".join(legal_sources) if legal_sources else None,
            "source": ",".join(legal_sources) if legal_sources else "articles",
            "sources": legal_sources,
            "chunks": legal_chunks,
            "fallback": False
        }
        if not history:
            _cache_set(query, result)
        return result

    except FuturesTimeoutError:
        print(f"❌ RETRIEVAL TIMEOUT: exceeded {RETRIEVAL_TIMEOUT_SEC}s")
        return {
            "answer": "⚠️ Tra cứu dữ liệu ILAS bị chậm nên tôi chưa thể trả lời chắc chắn bằng căn cứ pháp luật. Vui lòng thử lại.",
            "context_used": None,
            "source": "fallback-timeout",
            "sources": [],
            "chunks": [],
            "fallback": True
        }

    except Exception as e:
        print("❌ PIPELINE ERROR:", repr(e))
        return {
            "answer": "❌ Lỗi hệ thống nội bộ. Vui lòng thử lại sau.",
            "error": str(e),
            "context_used": None,
            "source": None,
            "fallback": False
        }
    



# Test mode
if __name__ == "__main__":
    while True:
        q = input("❓ Hỏi pháp lý ('exit' để thoát): ")
        if q.lower().strip() == "exit":
            break

        result = answer_legal_question(q)

        print("\n===== ANSWER =====")
        print(result["answer"])

        print("\n===== CONTEXT USED =====")
        print(result["context_used"])

        print("\n===== SOURCE =====")
        print(result["source"])

        print("\n===== FALLBACK =====")
        print(result["fallback"])

