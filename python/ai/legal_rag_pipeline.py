# ai/legal_rag_pipeline.py
# -------------------------------------------------------
# LEGAL RAG LEVEL 8 – FULL ARTICLE CONTEXT + HYBRID MODE
# -------------------------------------------------------

import os
from pathlib import Path
from dotenv import load_dotenv
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError

# Load .env from project root
env_path = Path(__file__).parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

from ai.retrieval_level6 import retrieve_multi_source
from ai.context_builder import build_context
from db_core import check_data_health
import time

RETRIEVAL_TIMEOUT_SEC = int(os.getenv("RETRIEVAL_TIMEOUT_SEC", "20"))

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
    q = (query or "").strip().lower()
    if not q:
        return False

    # Nếu không có hội thoại trước đó thì không cần rewrite.
    if not conversation_context or not conversation_context.strip():
        return False

    # Chỉ rewrite cho các câu follow-up mơ hồ để giảm 1 lượt gọi model.
    follow_up_markers = [
        "khoản",
        "điều đó",
        "trường hợp này",
        "cái đó",
        "ý đó",
        "nói rõ hơn",
        "thế còn",
        "còn nếu",
        "phần trên",
        "mục trên",
        "đoạn trên",
        "nội dung đó",
    ]
    return any(marker in q for marker in follow_up_markers)


def answer_legal_question(query: str, settings: dict = None, history=None, conversation_id=None):
    if settings is None:
        settings = {}

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
            fb = fallback_general_answer(query, conversation_context)
            return {
                "answer": fb + "\n\n⚠️ *Ghi chú: Câu trả lời này không dựa trên dữ liệu ILAS (fallback Gemini).*",
                "context_used": None,
                "source": "fallback",
                "fallback": True
            }

        # 4) Build FULL ARTICLE CONTEXT
        context = build_context(results)

        if not context or len(context.strip()) == 0:
            fb = fallback_general_answer(query, conversation_context)
            return {
                "answer": fb + "\n\n⚠️ *Không tìm thấy quy định phù hợp trong dữ liệu ILAS (fallback Gemini).*",
                "context_used": None,
                "source": "fallback",
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
        top = results[0]
        article_number = top.get("article_number")
        source_title = top.get("law_title")

        return {
            "answer": answer,
            "context_used": source_title,
            "source": f"article_{article_number}",
            "fallback": False
        }

    except FuturesTimeoutError:
        print(f"❌ RETRIEVAL TIMEOUT: exceeded {RETRIEVAL_TIMEOUT_SEC}s")
        fb = fallback_general_answer(query, conversation_context)
        return {
            "answer": fb + "\n\n⚠️ *Tra cứu dữ liệu ILAS bị chậm nên hệ thống tạm trả lời theo kiến thức chung.*",
            "context_used": None,
            "source": "fallback-timeout",
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

