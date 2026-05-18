def archive_old_data(cur, law_id):
    """
    Archive dữ liệu cũ của law_id trước khi insert bản mới.
    Chỉ archive (đánh dấu) chứ không xóa cứng, để còn giữ log và feedback cũ.
    """
    cur.execute("""
        UPDATE articles SET status='archived'
        WHERE law_id=%s AND status='active'
    """, (law_id,))

    cur.execute("""
        UPDATE sections SET status='archived'
        WHERE chapter_id IN (
            SELECT chapter_id FROM chapters WHERE law_id=%s AND status='active'
        )
    """, (law_id,))

    cur.execute("""
        UPDATE chapters SET status='archived'
        WHERE law_id=%s AND status='active'
    """, (law_id,))

    cur.execute("""
        UPDATE simplified_articles SET status='archived'
        WHERE article_id IN (
            SELECT article_id FROM articles WHERE law_id=%s AND status='active'
        )
    """, (law_id,))


def cleanup_versions(cur, keep_last=5):
    """
    Giữ lại keep_last bản ghi MỚI NHẤT trong law_versions (theo crawled_at DESC),
    còn lại xóa cứng:
      - feedback (FK -> articles)
      - simplified_articles (FK -> articles)
      - articles
      - sections
      - chapters
      - law_versions
    """
    # 1) Lấy các (law_id, version_number) mới nhất cần GIỮ
    # Thay đổi: thực hiện theo *từng law_id* (KEEP N PER LAW), không phải toàn hệ thống.
    recent_pairs = []
    try:
        # MySQL 8+ or other DBs supporting window functions
        cur.execute(f"""
            SELECT law_id, version_number FROM (
                SELECT law_id, version_number,
                       ROW_NUMBER() OVER (PARTITION BY law_id ORDER BY COALESCE(crawled_at, created_at) DESC) AS rn
                FROM law_versions
            ) t
            WHERE t.rn <= {int(keep_last)}
        """)
        rows = cur.fetchall()
        for r in rows:
            # support both dict-cursor and tuple cursor
            try:
                lid = r["law_id"]
                v = r["version_number"]
            except Exception:
                lid = r[0]
                v = r[1]
            recent_pairs.append((lid, v))
    except Exception:
        # Fallback for older MySQL versions without window functions
        # Keep versions where there are fewer than keep_last newer versions of the same law
        cur.execute(f"""
            SELECT lv.law_id, lv.version_number
            FROM law_versions lv
            WHERE (
                SELECT COUNT(*) FROM law_versions lv2
                WHERE lv2.law_id = lv.law_id
                  AND (COALESCE(lv2.crawled_at, lv2.created_at) > COALESCE(lv.crawled_at, lv.created_at)
                       OR (COALESCE(lv2.crawled_at, lv2.created_at) = COALESCE(lv.crawled_at, lv.created_at)
                           AND lv2.version_number > lv.version_number))
            ) < {int(keep_last)}
        """)
        rows = cur.fetchall()
        for r in rows:
            try:
                lid = r["law_id"]
                v = r["version_number"]
            except Exception:
                lid = r[0]
                v = r[1]
            recent_pairs.append((lid, v))

    # Debug: write a brief log to stdout so operator can inspect what's being kept
    try:
        print(f"[cleanup_versions] Keeping {len(recent_pairs)} version entries (keep_last={keep_last}). Samples: {recent_pairs[:20]}")
    except Exception:
        pass

    # Nếu chưa có đủ dữ liệu thì không cần cleanup
    if not recent_pairs:
        return {
            "feedback": 0,
            "simplified_articles": 0,
            "articles": 0,
            "sections": 0,
            "chapters": 0,
            "law_versions": 0,
        }

    # 2) Temp table recent_versions
    cur.execute("DROP TEMPORARY TABLE IF EXISTS recent_versions")
    cur.execute("CREATE TEMPORARY TABLE recent_versions (law_id INT, version_number INT)")

    cur.executemany("""
        INSERT INTO recent_versions (law_id, version_number)
        VALUES (%s, %s)
    """, recent_pairs)

    def delete_and_count(query):
        cur.execute(query)
        return cur.rowcount or 0

    # 3) Xóa theo thứ tự an toàn (để không vướng FK)

    counts = {}

    # 3.1) FEEDBACK (phụ thuộc articles) -> xóa trước
    # Nếu feedback có FK theo article_id như log của bạn:
    counts["feedback"] = delete_and_count("""
        DELETE f FROM feedback f
        JOIN articles a ON f.article_id = a.article_id
        LEFT JOIN recent_versions rv
            ON a.law_id = rv.law_id AND a.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 3.2) SIMPLIFIED_ARTICLES (phụ thuộc articles) -> xóa trước articles
    counts["simplified_articles"] = delete_and_count("""
        DELETE sa FROM simplified_articles sa
        JOIN articles a ON sa.article_id = a.article_id
        LEFT JOIN recent_versions rv
            ON a.law_id = rv.law_id AND a.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 3.3) ARTICLES
    counts["articles"] = delete_and_count("""
        DELETE a FROM articles a
        LEFT JOIN recent_versions rv
            ON a.law_id = rv.law_id AND a.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 3.4) SECTIONS (nếu sections có law_id + version_number thì xóa trực tiếp sẽ nhanh hơn;
    # nhưng code bạn đang join qua chapters => giữ nguyên style của bạn)
    counts["sections"] = delete_and_count("""
        DELETE s FROM sections s
        JOIN chapters c ON s.chapter_id = c.chapter_id
        LEFT JOIN recent_versions rv
            ON c.law_id = rv.law_id AND c.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 3.5) CHAPTERS
    counts["chapters"] = delete_and_count("""
        DELETE c FROM chapters c
        LEFT JOIN recent_versions rv
            ON c.law_id = rv.law_id AND c.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 3.6) LAW_VERSIONS
    counts["law_versions"] = delete_and_count("""
        DELETE lv FROM law_versions lv
        LEFT JOIN recent_versions rv
            ON lv.law_id = rv.law_id AND lv.version_number = rv.version_number
        WHERE rv.law_id IS NULL
    """)

    # 4) Drop temp
    cur.execute("DROP TEMPORARY TABLE recent_versions")

    return counts
