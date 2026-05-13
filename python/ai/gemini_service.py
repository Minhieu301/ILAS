import os
import requests
from dotenv import load_dotenv
from pathlib import Path

# Load .env from project root
env_path = Path(__file__).parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
API_KEY = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
MODEL_NAME = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")


def _post_to_gemini(messages, temperature: float, max_tokens: int) -> str:
    if not API_KEY:
        return "⚠️ GEMINI_API_KEY chưa được cấu hình trong .env."

    try:
        headers = {"Content-Type": "application/json"}
        system_parts = []
        user_parts = []
        for msg in messages:
            role = str(msg.get("role", "user")).lower().strip()
            content = str(msg.get("content", "")).strip()
            if not content:
                continue
            if role == "system":
                system_parts.append(content)
            else:
                user_parts.append(content)

        if not user_parts and system_parts:
            user_parts = ["\n\n".join(system_parts)]
            system_parts = []

        payload = {
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": "\n\n".join(user_parts)}],
                }
            ],
            "generationConfig": {
                "temperature": temperature,
                "maxOutputTokens": max_tokens,
            },
        }
        if system_parts:
            payload["system_instruction"] = {
                "parts": [{"text": "\n\n".join(system_parts)}]
            }

        res = requests.post(
            API_URL.format(model=MODEL_NAME),
            params={"key": API_KEY},
            headers=headers,
            json=payload,
            timeout=40,
        )

        try:
            j = res.json()
        except Exception as e:
            print("Gemini JSON ERROR:", e, "Raw:", res.text[:500])
            return "AI không trả về dữ liệu hợp lệ (JSON error)."

        if "error" in j:
            err = j.get("error", {})
            status = str(err.get("status", "")).upper()
            message = str(err.get("message", ""))
            print(f"Gemini API ERROR [{MODEL_NAME}] {status}: {message}")
            return "Hệ thống AI Gemini đang lỗi cấu hình hoặc quá tải. Vui lòng thử lại."

        candidates = j.get("candidates", [])
        if not candidates:
            print("Gemini EMPTY CANDIDATES:", j)
            return "AI không trả về kết quả (no candidates)."

        candidate = candidates[0]
        content = candidate.get("content", {})
        parts = content.get("parts", []) if isinstance(content, dict) else []
        text_chunks = [part.get("text", "") for part in parts if isinstance(part, dict)]
        content_text = "".join(text_chunks).strip()

        if not content_text:
            print("Gemini EMPTY CONTENT:", j)
            return "AI không sinh ra nội dung trả lời."

        return content_text

    except Exception as e:
        print("Gemini REQUEST ERROR:", repr(e))
        return "Hệ thống AI đang gặp sự cố hoặc mất kết nối. Vui lòng thử lại."


def guarded_completion(
    context: str,
    question: str,
    conversation_context: str = "",
    history=None,
    temperature: float = 0.15,
    max_tokens: int = 900
) -> str:
    """
    ILAS Legal Answer Engine — Strict + Summarization Mode
    (Đã tối ưu để AI nói chuyện TỰ NHIÊN, THÂN THIỆN)
    """

    # Ép kiểu an toàn (FE hay gửi "0.7", "500" dạng string)
    try:
        temperature = float(temperature)
    except:
        temperature = 0.15

    try:
        max_tokens = int(max_tokens)
    except:
        max_tokens = 900

    system_prompt = """
Bạn là một chuyên viên tư vấn pháp luật lao động thân thiện, tận tâm và chuyên nghiệp của nền tảng ILAS.
Nhiệm vụ của bạn là giải đáp thắc mắc cho người lao động dựa TRÊN ĐÚNG NGỮ CẢNH LUẬT được cung cấp.

=== QUY TẮC TRẢ LỜI BẮT BUỘC ===
1. GIỌNG ĐIỆU TỰ NHIÊN: Xưng "tôi" và gọi người dùng là "bạn". Trả lời tự nhiên, thân thiện như đang trò chuyện tư vấn. Diễn giải lại các từ ngữ pháp lý khô khan thành ngôn ngữ đơn giản, dễ hiểu đối với người công nhân bình thường.
2. NGUỒN DUY NHẤT: Chỉ được sử dụng thông tin trong phần "NGỮ CẢNH PHÁP LUẬT". Không được dùng kiến thức ngoài, không suy diễn, không tự bịa ra số liệu/ngày tháng.
3. TRÍCH DẪN KHÉO LÉO: Luôn đi thẳng vào vấn đề trả lời câu hỏi trước (Ví dụ: "Mức trợ cấp của bạn là..."), sau đó mới giải thích chi tiết dựa theo Điều mấy của luật trong ngữ cảnh.
4. TỔNG HỢP HỢP LÝ: Nếu các điểm/khoản trong ngữ cảnh có số liệu, bạn được phép tổng hợp và tính toán (liệt kê rõ phép tính).
5. THIẾU THÔNG TIN: Nếu ngữ cảnh không có thông tin cần thiết → trả lời tự nhiên: "Rất tiếc, theo dữ liệu hiện tại của hệ thống ILAS, tôi chưa tìm thấy quy định cụ thể về vấn đề này để hỗ trợ bạn."
"""
    conversation_section = ""
    if conversation_context and conversation_context.strip():
        conversation_section = f"""
NGỮ CẢNH HỘI THOẠI TRƯỚC ĐÓ:
{conversation_context.strip()}

"""

    # Build history text from structured history list (last 4 turns)
    history_section = ""
    try:
        if history and isinstance(history, (list, tuple)) and len(history) > 0:
            parts = []
            for item in list(history)[-4:]:
                if not isinstance(item, dict):
                    continue
                user_text = str(item.get("content", "")).strip()
                assistant_text = str(item.get("answer", "")).strip()
                if user_text:
                    parts.append(f"Người dùng: {user_text}")
                if assistant_text:
                    parts.append(f"Trợ lý: {assistant_text}")
            if parts:
                history_section = "\n".join(parts) + "\n\n"
    except Exception:
        history_section = ""

    user_prompt = f"""
{conversation_section}{history_section}NGỮ CẢNH PHÁP LUẬT (trích từ cơ sở dữ liệu ILAS):
-------------------------------------------------
{context}
-------------------------------------------------

CÂU HỎI CỦA NGƯỜI DÙNG:
{question}
"""

    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]

    return _post_to_gemini(messages, temperature=temperature, max_tokens=max_tokens)


def fallback_general_answer(question: str, conversation_context: str = "") -> str:
    """
    Fallback khi retrieval yếu → dùng kiến thức tổng quát của Gemini.
    Không dựa trên context ILAS.
    """
    system_prompt = """
Bạn là trợ lý pháp lý tổng quát của ILAS. Hãy xưng "tôi" và gọi "bạn" thân thiện.
Hãy trả lời câu hỏi dưới đây dựa trên kiến thức phổ biến, KHÔNG dùng context luật.
Trả lời ngắn gọn, dễ hiểu cho người công nhân.
Không trích dẫn điều khoản cụ thể.
"""

    conversation_section = ""
    if conversation_context and conversation_context.strip():
        conversation_section = f"""
NGỮ CẢNH HỘI THOẠI TRƯỚC ĐÓ:
{conversation_context.strip()}

"""

    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"{conversation_section}CÂU HỎI CỦA NGƯỜI DÙNG:\n{question}"},
    ]

    return _post_to_gemini(messages, temperature=0.5, max_tokens=500)


def rewrite_legal_query(user_question: str, conversation_context: str = "") -> str:
    """
    Sử dụng AI để chuyển câu hỏi tự nhiên thành cụm từ khóa pháp lý chuẩn.
    Giúp Semantic Search tìm luật chính xác hơn.
    """
    system_prompt = """
Bạn là chuyên gia phân tích ngôn ngữ pháp lý. 
Nhiệm vụ của bạn là chuyển đổi câu hỏi thông tục của người dùng thành MỘT CÂU TRUY VẤN TỪ KHÓA pháp lý chuẩn xác để tìm kiếm trong cơ sở dữ liệu luật lao động.

QUY TẮC BẮT BUỘC:
1. CHỈ TRẢ VỀ DUY NHẤT CÂU TRUY VẤN đã tối ưu. KHÔNG có câu chào, KHÔNG giải thích, KHÔNG ngoặc kép.
2. Dùng đúng thuật ngữ luật (VD: "nghỉ đẻ" -> "chế độ thai sản", "đuổi việc" -> "đơn phương chấm dứt hợp đồng", "đền bao nhiêu" -> "mức bồi thường").
3. Nếu có ngữ cảnh hội thoại trước đó, hãy dùng nó để hiểu các tham chiếu như "khoản 1", "điều đó", "trường hợp này" rồi viết lại thành một truy vấn độc lập đầy đủ.
"""

    conversation_section = ""
    if conversation_context and conversation_context.strip():
        conversation_section = f"""
NGỮ CẢNH HỘI THOẠI TRƯỚC ĐÓ:
{conversation_context.strip()}

"""

    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f'{conversation_section}Hãy tối ưu câu hỏi này: "{user_question}"'},
    ]

    # Gọi AI bằng hàm _post_to_gemini có sẵn, temp thấp để câu từ chuẩn xác
    optimized = _post_to_gemini(messages, temperature=0.1, max_tokens=100)

    # Nếu AI lỗi hoặc trả về rỗng, dùng tạm câu hỏi cũ
    if not optimized or "Hệ thống AI đang gặp sự cố" in optimized or "JSON error" in optimized:
        return user_question

    return optimized.strip()