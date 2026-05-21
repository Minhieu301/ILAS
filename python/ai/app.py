from flask import Flask, request, jsonify
from flask_cors import CORS
from ai.legal_rag_pipeline import answer_legal_question
import subprocess
import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from dotenv import load_dotenv

load_dotenv(dotenv_path=Path(__file__).resolve().parents[2] / ".env")

app = Flask(__name__)
CORS(app)

AI_REQUEST_TIMEOUT_SEC = int(os.getenv("AI_REQUEST_TIMEOUT_SEC", "75"))


def _run_with_timeout(func, timeout_sec: int, *args, **kwargs):
    executor = ThreadPoolExecutor(max_workers=1)
    future = executor.submit(func, *args, **kwargs)
    try:
        return future.result(timeout=timeout_sec)
    finally:
        # Không chờ worker đang kẹt để tránh treo request Flask.
        executor.shutdown(wait=False, cancel_futures=True)


# AI ANSWER ENDPOINT
@app.route("/api/ask", methods=["POST"])
def ask():
    data = request.get_json(force=True)

    question = data.get("question", "").strip()
    settings = data.get("settings", {})   # <-- nhận settings từ backend
    history = data.get("history", [])
    conversation_id = data.get("conversation_id")

    if not question:
        return jsonify({"error": "Missing 'question' field"}), 400

    print("\n🔥 NEW AI REQUEST")
    print("QUESTION:", question)
    print("SETTINGS:", settings)

    try:
        result = _run_with_timeout(
            answer_legal_question,
            AI_REQUEST_TIMEOUT_SEC,
            question,
            settings,
            history,
            conversation_id,
        )

        return jsonify(result), 200

    except FuturesTimeoutError:
        print(f"❌ AI TIMEOUT: request exceeded {AI_REQUEST_TIMEOUT_SEC}s")
        return jsonify({
            "answer": "⚠️ AI xử lý quá lâu nên đã hết thời gian chờ. Vui lòng thử lại câu ngắn hơn.",
            "context_used": None,
            "source": "timeout",
            "fallback": True
        }), 504

    except Exception as e:
        print("❌ AI SERVER ERROR:", e)
        return jsonify({
            "answer": "⚠️ AI Server gặp lỗi nội bộ.",
            "error": str(e)
        }), 500

# REBUILD VECTOR + BM25 + TOPIC CLUSTERS
@app.route("/api/admin/rebuild", methods=["POST"])
def rebuild():
    try:
        subprocess.Popen(["python", "ai/rebuild_all.py"])
        print("🚀 REBUILD STARTED in background!")
        return jsonify({"message": "Rebuild started"}), 200

    except Exception as e:
        print("❌ REBUILD FAILED:", e)
        return jsonify({"error": str(e)}), 500

# RUN SERVER
if __name__ == "__main__":
    print("🚀 AI Server is running at http://127.0.0.1:5000")
    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)
