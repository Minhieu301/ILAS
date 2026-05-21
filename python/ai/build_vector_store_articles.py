import numpy as np
import json
from pathlib import Path
import sys

# Allow import from parent folder
sys.path.append(str(Path(__file__).resolve().parents[1]))

from ai.local_embedder import get_local_embedding
from db_core import get_connection

VECTOR_STORE_DIR = Path(__file__).resolve().parents[1] / "vector_store"

# LOAD ARTICLES TỪ DB
def load_articles_with_law_info():
    conn = get_connection()
    with conn.cursor() as cur:
        cur.execute("""
            SELECT a.article_id, a.article_number, a.article_title, a.content, l.title as law_title
            FROM articles a
            LEFT JOIN laws l ON a.law_id = l.law_id
            WHERE a.status='active'
            ORDER BY a.article_id ASC
        """)
        return cur.fetchall()

# BUILD VECTOR STORE - FULL ARTICLES (NO CHUNKS)
def build_full_articles_store():
    articles = load_articles_with_law_info()
    vectors = []
    metadata = []

    print(f"🚀 Building FULL ARTICLES vector store for {len(articles)} articles...")

    for idx, art in enumerate(articles):
        content = art.get("content") or ""
        if not content.strip():
            print(f"  ⏭️  Skipping article_id={art['article_id']} (empty content)")
            continue

        emb = get_local_embedding(content)
        if emb is None:
            print(f"  ⏭️  Skipping article_id={art['article_id']} (embedding failed)")
            continue

        vectors.append(emb)

        metadata.append({
            "id": f"art_{art['article_id']}",
            "article_id": art["article_id"],
            "article_number": art["article_number"],
            "article_title": art["article_title"],
            "law_title": art.get("law_title", "Unknown"),
            "text": content[:500] + "..." if len(content) > 500 else content,  # Preview text
            "content_length": len(content),
            "source_type": "articles"
        })

        if (idx + 1) % 100 == 0:
            print(f"  ✓ Processed {idx + 1}/{len(articles)}")

    vectors = np.array(vectors, dtype=np.float32)

    # SAVE TO vector_store/articles/
    out_dir = VECTOR_STORE_DIR / "articles"
    out_dir.mkdir(parents=True, exist_ok=True)

    np.save(out_dir / "vectors.npy", vectors)
    with open(out_dir / "meta.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f"✅ DONE — {len(vectors)} full articles stored at {out_dir}")


if __name__ == "__main__":
    build_full_articles_store()
