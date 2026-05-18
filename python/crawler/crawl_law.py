#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
import unicodedata
from pathlib import Path

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

from .db import get_db_connection
from .content_cleaner import normalize_article_content
from .metadata_extractor import extract_metadata
from .db_inserts import insert_chapter, insert_section, insert_article
from .archive_cleanup import (
    archive_old_data,
    cleanup_versions
)
from .log_utils import log_step


def _strip_accents(text: str) -> str:
    if not text:
        return ""
    return "".join(
        c for c in unicodedata.normalize("NFD", text)
        if unicodedata.category(c) != "Mn"
    )


def _normalize_title(text: str) -> str:
    base = _strip_accents(text or "").lower()
    base = re.sub(r"[^a-z0-9\s]", " ", base)
    return re.sub(r"\s+", " ", base).strip()


def _title_similarity(a: str, b: str) -> float:
    ta = set(_normalize_title(a).split())
    tb = set(_normalize_title(b).split())
    if not ta or not tb:
        return 0.0
    return len(ta & tb) / len(ta | tb)


def _resolve_chromium_executable():
    configured = os.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH", "").strip()
    if configured and Path(configured).exists():
        return configured

    browsers_root = Path.home() / "AppData" / "Local" / "ms-playwright"
    if browsers_root.exists():
        for candidate_dir in sorted(browsers_root.glob("chromium-*"), reverse=True):
            chrome_exe = candidate_dir / "chrome-win64" / "chrome.exe"
            if chrome_exe.exists():
                return str(chrome_exe)

    return None


def crawl_law_page(url: str):
    # BẮT ĐẦU
    log_step("Bắt đầu crawl luật")
    log_step(f"URL: {url}")

    # TẢI HTML
    log_step("Đang tải HTML")
    with sync_playwright() as p:
        launch_kwargs = {"headless": True}
        chromium_executable = _resolve_chromium_executable()
        if chromium_executable:
            launch_kwargs["executable_path"] = chromium_executable
            log_step(f"Dùng Chromium đã cài: {chromium_executable}")

        browser = p.chromium.launch(**launch_kwargs)
        page = browser.new_page(viewport={"width": 1440, "height": 2200})

        try:
            page.goto(url, wait_until="domcontentloaded", timeout=60000)
        except PlaywrightTimeoutError:
            log_step("Cảnh báo: load trang chậm, thử lại với chế độ nhẹ hơn")
            page.goto(url, wait_until="commit", timeout=90000)

        page.wait_for_timeout(4000)
        html = page.content()
        browser.close()
    log_step("Tải HTML xong")

    soup = BeautifulSoup(html, "lxml")

    # METADATA
    log_step("Đang lấy metadata")
    title, code, law_type, issued_date, effective_date = extract_metadata(soup)
    log_step(f"Metadata: tiêu đề={title} | mã={code}")

    safe_title = (title or "").strip()
    normalized_code = re.sub(r"\s+", "", (code or "")).strip().upper()

    # Chặn ghi nhầm: metadata phải đủ rõ ràng trước khi chạm DB
    if not safe_title or safe_title.lower() in ("không rõ", "khong ro"):
        raise ValueError("Không xác định được tiêu đề văn bản từ trang nguồn.")
    if not normalized_code or normalized_code in ("KHONGRO", "KHÔNGRÕ"):
        raise ValueError("Không xác định được số hiệu văn bản từ trang nguồn.")

    # NỘI DUNG
    all_p = soup.select("div#ctl00_Content_ThongTinVB_pnlDocContent p")
    log_step(f"Tìm thấy {len(all_p)} đoạn nội dung")

    # Chặn crawl nhầm trang (ví dụ trang lỗi/redirect/trang không phải văn bản luật)
    article_anchor_count = len(soup.select("a[name^='dieu_']"))
    if article_anchor_count == 0:
        raise ValueError("Trang nguồn không có cấu trúc điều luật hợp lệ (không tìm thấy anchor dieu_).")

    # DATABASE
    conn = get_db_connection()

    try:
        with conn.cursor() as cur:
            # LƯU THÔNG TIN LUẬT + TẠO VERSION
            log_step("Đang lưu thông tin luật")

            safe_code = normalized_code

            # Không phụ thuộc unique index để tăng version.
            cur.execute(
                "SELECT law_id, version_number, title FROM laws WHERE code=%s ORDER BY law_id ASC LIMIT 1",
                (safe_code,)
            )
            row = cur.fetchone()

            if row:
                existing_title = row.get("title") or ""
                sim = _title_similarity(existing_title, safe_title)

                # Chặn ghi đè nhầm khi cùng code nhưng tiêu đề khác xa nhau.
                if sim < 0.35:
                    raise ValueError(
                        "Phát hiện khả năng ghi nhầm bộ luật: "
                        f"code={safe_code}, title_db='{existing_title}', title_new='{safe_title}', sim={sim:.2f}"
                    )

                law_id = row["law_id"]
                version_number = int(row.get("version_number") or 0) + 1

                cur.execute(
                    """
                    UPDATE laws
                    SET
                        title = %s,
                        code = %s,
                        law_type = %s,
                        issued_date = %s,
                        effective_date = %s,
                        source_url = %s,
                        last_crawled_at = NOW(),
                        version_number = %s,
                        status = 'active'
                    WHERE law_id = %s
                    """,
                    (
                        title,
                        safe_code,
                        law_type,
                        issued_date,
                        effective_date,
                        url,
                        version_number,
                        law_id,
                    ),
                )
            else:
                version_number = 1
                cur.execute(
                    """
                    INSERT INTO laws
                        (title, code, law_type, issued_date, effective_date, source_url, status, version_number)
                    VALUES
                        (%s,%s,%s,%s,%s,%s,'active',%s)
                    """,
                    (title, safe_code, law_type, issued_date, effective_date, url, version_number),
                )
                law_id = cur.lastrowid

            log_step(f"Luật ID={law_id} | Phiên bản={version_number}")

            # ARCHIVE
            # Đã bỏ tính năng archive tất cả luật khác — giữ nhiều luật active cùng lúc
            log_step("Đang archive dữ liệu cũ của luật")
            archive_old_data(cur, law_id)

            inserted = False

            # Newer schema requires title + created_at in law_versions.
            try:
                cur.execute(
                    """
                    INSERT INTO law_versions (
                        law_id, version_number, title, law_type,
                        issued_date, effective_date, source_url,
                        status, created_at
                    )
                    VALUES (%s,%s,%s,%s,%s,%s,%s,'active',NOW())
                    """,
                    (law_id, version_number, title, law_type, issued_date, effective_date, url)
                )
                inserted = True
            except Exception:
                pass

            if not inserted:
                try:
                    cur.execute(
                        """
                        INSERT INTO law_versions (
                            law_id, version_number, title, law_type,
                            issued_date, effective_date, source_url, status
                        )
                        VALUES (%s,%s,%s,%s,%s,%s,%s,'active')
                        """,
                        (law_id, version_number, title, law_type, issued_date, effective_date, url)
                    )
                    inserted = True
                except Exception:
                    pass

            if not inserted:
                cur.execute(
                    "INSERT INTO law_versions (law_id, version_number) VALUES (%s,%s)",
                    (law_id, version_number)
                )

            conn.commit()
            log_step("Lưu phiên bản mới xong")

            # CRAWL CHƯƠNG / MỤC / ĐIỀU
            log_step("Bắt đầu xử lý Chương - Mục - Điều")

            current_chapter_id = None
            current_section_id = None

            stats = {
                "chapters": 0,
                "sections": 0,
                "articles": 0
            }

            for i, p in enumerate(all_p):
                a = p.find("a", attrs={"name": True})
                if not a:
                    continue

                name = a["name"]

                # ------------------ CHƯƠNG ------------------
                if name.startswith("chuong_") and not name.endswith("_name"):
                    chap_num = name.replace("chuong_", "")
                    chap_text = p.get_text(" ", strip=True)

                    m = re.search(r"Chương\s+([IVXLC\d]+)", chap_text)
                    chapter_number = m.group(1) if m else chap_text

                    nxt = p.find_next_sibling("p")
                    if nxt and nxt.find("a", attrs={"name": f"chuong_{chap_num}_name"}):
                        chap_title = nxt.get_text(" ", strip=True)
                    else:
                        chap_title = re.sub(
                            r"^Chương\s+[IVXLC\d]+[.:]?\s*",
                            "",
                            chap_text
                        ).strip()

                    current_chapter_id = insert_chapter(
                        cur,
                        law_id,
                        chapter_number,
                        chap_title,
                        version_number
                    )

                    stats["chapters"] += 1
                    current_section_id = None

                    log_step(f"Đang xử lý Chương {chapter_number}: {chap_title}")

                # ------------------ MỤC ------------------
                elif name.startswith("muc_") and not name.endswith("_name"):
                    sec_text = p.get_text(" ", strip=True)

                    m = re.search(r"Mục\s+([IVXLC\d]+)", sec_text)
                    sec_num = m.group(1) if m else None

                    sec_title = re.sub(
                        r"^Mục\s+[IVXLC\d]+[.:]?\s*",
                        "",
                        sec_text
                    ).strip()

                    if current_chapter_id:
                        current_section_id = insert_section(
                            cur,
                            current_chapter_id,
                            sec_num,
                            sec_title,
                            version_number
                        )
                        stats["sections"] += 1

                # ------------------ ĐIỀU ------------------
                elif name.startswith("dieu_"):
                    art_title = p.get_text(" ", strip=True)
                    m = re.match(r"Điều\s+(\d+)", art_title)
                    art_num = m.group(1) if m else None

                    content_parts = []
                    j = i + 1
                    while j < len(all_p):
                        a2 = all_p[j].find("a", attrs={"name": True})
                        if a2 and a2["name"].startswith(
                            ("chuong_", "muc_", "dieu_")
                        ):
                            break

                        txt = all_p[j].get_text(" ", strip=True)
                        if txt:
                            content_parts.append(txt)
                        j += 1

                    raw = "\n".join(content_parts).strip()
                    art_content = normalize_article_content(raw)

                    insert_article(
                        cur,
                        law_id,
                        current_chapter_id,
                        current_section_id,
                        art_num,
                        art_title,
                        art_content,
                        version_number
                    )

                    stats["articles"] += 1

            conn.commit()

            log_step(
                f"Đã lưu xong: {stats['chapters']} chương, "
                f"{stats['sections']} mục, {stats['articles']} điều"
            )

            # CLEANUP (AN TOÀN)
            log_step("Đang dọn các phiên bản cũ")
            try:
                counts = cleanup_versions(cur)
                conn.commit()
                log_step(f"Dọn xong: {counts}")
            except Exception as e:
                conn.rollback()
                log_step(f"Cảnh báo: dọn phiên bản cũ bị lỗi (bỏ qua): {e}")

    except Exception as e:
        conn.rollback()
        log_step(f"Lỗi khi crawl luật: {e}")
        raise

    finally:
        conn.close()

    # KẾT THÚC
    log_step("Hoàn tất crawl luật")