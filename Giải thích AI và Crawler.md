## 📋 TÓM TẮT HỆ THỐNG AI (Thư Mục `ai/`)

**Hệ thống AI trong ILAS hoạt động giống một trợ lý pháp lý có quy trình rõ ràng: tìm luật trước, rồi mới trả lời.**

Khác với chatbot trả lời theo kiến thức chung, AI của ILAS sẽ ưu tiên lấy dữ liệu thật từ **kho luật nội bộ** (database chứa luật, chương, mục, điều, simplified articles, FAQ). Vì vậy câu trả lời có căn cứ hơn và hạn chế bịa thông tin. Có thể hiểu đơn giản là: user hỏi gì, hệ thống đi tra luật trước, gom phần liên quan, sau đó mới nhờ AI diễn giải lại cho dễ hiểu.

**Bước 1: Nhận câu hỏi từ frontend**  
Frontend gửi câu hỏi vào endpoint `/api/ask` trong `app.py`. Cùng lúc đó có thể gửi thêm cấu hình từ admin, ví dụ có bật chatbot không, chọn nguồn dữ liệu nào, muốn trả lời dài hay ngắn.

**Bước 2: Tìm điều luật liên quan nhất**  
`retrieval_level6.py` làm nhiệm vụ tìm kiếm. File này kết hợp nhiều cách cùng lúc để tăng độ chính xác:  
- Tìm theo ý nghĩa câu hỏi (semantic search).  
- Tìm theo từ khóa xuất hiện trong nội dung luật (BM25 keyword search).  
- Nhận biết ý định câu hỏi (ví dụ hỏi về nghỉ việc, sa thải, lương) để ưu tiên đúng nhóm điều luật.  

Kết quả cuối sẽ là danh sách các điều luật có điểm cao nhất, thay vì chỉ dựa vào 1 keyword đơn lẻ.

**Bước 3: Tăng tốc bằng cache**  
Để truy vấn nhanh hơn, hệ thống có cache embedding theo `article_number`. Nếu cùng một điều luật đã được xử lý trước đó thì lần sau không cần tính lại từ đầu. Nhờ vậy các câu hỏi lặp lại hoặc cùng chủ đề sẽ phản hồi nhanh hơn đáng kể.  
Ngoài ra log debug của retrieval đã được đưa về chế độ tùy chọn (`RAG_VERBOSE`), nên môi trường chạy thật sẽ đỡ bị spam log.

**Bước 4: Ghép context pháp lý đầy đủ**  
Sau khi tìm được kết quả tốt nhất, `context_builder.py` sẽ lấy nội dung điều luật đầy đủ từ database và ghép thành một khối context rõ ràng. Mục tiêu là để AI có đủ dữ liệu gốc trước khi trả lời, không phải đoán mò.  
Hệ thống cũng giới hạn độ dài context để tránh quá tải model và giữ tốc độ phản hồi ổn định.

**Bước 5: AI diễn giải thành câu trả lời dễ hiểu**  
`legal_rag_pipeline.py` sẽ chọn nhà cung cấp AI theo cấu hình:  
- Mặc định: Gemini (`gemini_service.py`)  
- Dự phòng: Groq (`groq_service.py`)  

Hai provider đều dùng prompt nghiêm ngặt theo hướng pháp lý: ưu tiên bám dữ liệu context, hạn chế suy diễn. Nếu Gemini lỗi theo các trường hợp đã nhận diện, pipeline có thể tự chuyển sang Groq để không làm gián đoạn trải nghiệm user.

**Bước 6: Trả kết quả + fallback an toàn**  
Nếu tìm được dữ liệu phù hợp, hệ thống trả câu trả lời chính có căn cứ từ dữ liệu ILAS. Nếu không tìm được context đủ tốt, hệ thống trả fallback và có ghi chú rõ để phân biệt với câu trả lời bám dữ liệu luật.  

👉 **AI lấy dữ liệu từ đâu?**  
- Từ **database nội bộ** (luật, chương, mục, điều, simplified articles, FAQ).  
- Từ **vector store** (semantic search).  
- Từ **BM25 index** (keyword search).  
- Kết hợp với **topic boost** để ưu tiên đúng chủ đề lao động.  

👉 **AI trả về cái gì?**  
- Một **JSON response** chuẩn hóa gồm:  
  - `answer`: câu trả lời pháp lý dễ hiểu, có trích dẫn Điều/Khoản.  
  - `source`: nguồn dữ liệu (articles, chunks, simplified, FAQ, hoặc fallback).  
  - `citations`: trích dẫn rõ ràng để người dùng kiểm chứng.  
  - `fallback`: nếu không có dữ liệu phù hợp, hệ thống trả câu trả lời tham khảo và đánh dấu `fallback=true`.  

**Bước 7: Cập nhật dữ liệu AI khi luật thay đổi**  
Khi cần cập nhật dữ liệu, admin gọi `/api/admin/rebuild`. Lúc đó `rebuild_all.py` sẽ chạy lại các bước build vector/index/topic để kho tìm kiếm luôn mới.  
Hiện tại pipeline rebuild gồm chunk vectors, simplified vectors, BM25 index và topic clustering.

---

## 📋 TÓM TẮT HỆ THỐNG CRAWLER (Thư Mục `crawler/`)

**Hệ thống Crawler là một "robot thông minh" tự động lên trang web thuvienphapluat.vn, tải về nội dung luật, làm sạch dữ liệu, rồi lưu vào database để AI có thể search và trả lời câu hỏi.** Thay vì con người phải thủ công copy-paste từng bộ luật, Crawler làm tất cả automatically. Khi có bộ luật mới trên website, admin chỉ cần cung cấp URL, hệ thống tự động download, xử lý, lưu vào DB trong vài phút.

**Bước 1: Tải xuống HTML (Download HTML)**  
Crawler sử dụng Playwright (headless browser) để tải trang, chờ JavaScript render (~4 giây), rồi lấy HTML đầy đủ.

**Bước 2: Trích xuất metadata (Extract Metadata)**  
File `metadata_extractor.py` đọc HTML và tìm thông tin cơ bản của luật: tiêu đề, số hiệu, loại văn bản, ngày ban hành, ngày có hiệu lực. Hỗ trợ nhiều định dạng ngày khác nhau, và nếu không tìm được trong HTML table thì fallback regex để search trong nội dung HTML.

**Bước 3: Chuẩn hóa nội dung (Normalize Content)**  
File `content_cleaner.py` gộp các dòng bị split, nhưng vẫn giữ nguyên cấu trúc pháp lý như khoản (1., 2., 3.), điểm (a), b), c)), dấu bullet (•). Nhờ vậy nội dung sau khi clean vẫn đúng cấu trúc pháp luật.

**Bước 4: Archive dữ liệu cũ (Archive Old Data)**  
File `archive_cleanup.py` archive phiên bản cũ (status='archived'), cascade xuống chapters, sections, articles, simplified. Đồng thời hard delete các version quá cũ, chỉ giữ 5 version mới nhất.

**Bước 5: Insert vào Database (Insert into Database)**  
File `db_inserts.py` insert dữ liệu theo thứ tự: chapters → sections → articles. Có truncate an toàn để tránh lỗi “Data too long”. Mỗi lần crawl, version_number tự động tăng.

**Bước 6: Quản lý version (Version Management)**  
Database quản lý version_number riêng cho từng luật. Workflow: archive old → insert new → update version_number. Cấu trúc cây dữ liệu: laws → chapters → sections → articles → simplified_articles.  

👉 **Làm sao để không nhầm điều luật của bộ luật này sang bộ luật kia?**  
- Mỗi bản ghi article/section/chapter đều gắn với **law_id** duy nhất.  
- Khi crawl, hệ thống kiểm tra `law_id` và `version_number` để đảm bảo dữ liệu mới thuộc đúng bộ luật.  
- Các foreign key (law_id → chapter_id → section_id → article_id) giúp ràng buộc dữ liệu, tránh việc điều luật của bộ luật này bị gán nhầm sang bộ luật khác.  
- Khi insert, crawler luôn cascade theo đúng `law_id` hiện tại, không reuse ID từ luật khác.

**Bước 7: Logging & Error Handling**  
File `log_utils.py` in log với timestamp cho từng bước. Entry points: `run_crawl_api.py` (CLI mode, exit code 0/1/2), `run_crawl.py` (interactive mode). Sau khi crawl xong, trigger `rebuild_all.py` để rebuild vector store cho AI.  

---

👉 **Cách nắm vững luồng hoạt động:**  
- Hiểu rõ **Crawler** là bước đầu tiên: lấy dữ liệu luật, chuẩn hóa, lưu vào DB.  
- Sau đó **AI service** dùng retrieval để tra cứu dữ liệu từ DB + vector store.  
- **LLM** (Gemini/Groq) chỉ diễn giải lại dựa trên context đã lấy.  
- Cuối cùng **Frontend** hiển thị câu trả lời cho người dùng.  

Toàn bộ hệ thống là một chuỗi liên kết: **Crawler → Database → AI Retrieval → LLM → Frontend.** Nắm rõ từng bước sẽ giúp bạn hiểu cách dữ liệu đi từ nguồn gốc (website luật) đến câu trả lời cuối cùng hiển thị cho người dùng.