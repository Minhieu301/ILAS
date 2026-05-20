from db_core import execute_query
import re
import traceback

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

def load_full_article_by_number(article_number: str) -> str:
    # Ưu tiên Bộ Luật Lao động (law_id=1) nếu có, nhưng không khóa cứng để tránh miss dữ liệu.
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

def build_context(results):
    if not results:
        return None

    top = results[0]

    if top.get("source") not in ["articles", "articles/chunks"]:
        return None

    article_id = top.get("article_id")

    if not article_id and top.get("id"):
        match = re.search(r"art_(\d+)", top.get("id"))
        if match:
            article_id = match.group(1)

    # 1. Nếu có ID -> Tìm theo ID (Vector Search gọi)
    if article_id:
        context = load_full_article(article_id)
        if context is None:
            print(f"[WARN] build_context failed: no DB row found. top_result article_id={article_id}")
        return context
        
    # 2. Nếu không có ID mà chỉ có số Điều -> Tìm theo Số Điều (Hàm Intent gọi)
    article_number = top.get("article_number")
    if article_number:
        context = load_full_article_by_number(article_number)
        if context is None:
            print(f"[WARN] build_context failed: no DB row found. top_result article_number={article_number}")
        return context

    print(f"[WARN] build_context failed: no DB row found. top_result={top}")
    return None