package com.C1SE10.backend.service.ai;

import com.C1SE10.backend.dto.request.ai.ChatRequestDTO;
import com.C1SE10.backend.dto.response.ai.ChatHistoryDTO;
import com.C1SE10.backend.dto.response.ai.ChatResponseDTO;
import com.C1SE10.backend.dto.response.ai.TopQuestionResponse;
import com.C1SE10.backend.model.ChatbotLog;
import com.C1SE10.backend.model.ChatbotSettings;
import com.C1SE10.backend.model.UserAccount;
import com.C1SE10.backend.repository.ChatbotLogRepository;
import com.C1SE10.backend.repository.ChatbotSettingsRepository;
import com.C1SE10.backend.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final UserAccountRepository userRepo;
    private final ChatbotLogRepository chatbotRepo;
    private final ChatbotSettingsRepository settingsRepo;

    private static final String PYTHON_API = "http://127.0.0.1:5000/api/ask";
    private static final int PYTHON_CONNECT_TIMEOUT_MS = 5_000;
    private static final int PYTHON_READ_TIMEOUT_MS = 240_000;

    // =====================================================
    // TOP QUESTIONS (ADMIN)
    // =====================================================
    public List<TopQuestionResponse> getTopQuestions() {
        return chatbotRepo.findTopQuestions();
    }

    // =====================================================
    // MAIN CHATBOT PROCESS
    // =====================================================
    public ChatResponseDTO processQuestion(ChatRequestDTO req) {

        String question = req.getQuestion();
        boolean saveLog = req.isSaveLog();
        Integer userId = req.getUserId();
        String conversationId = (req.getConversationId() != null && !req.getConversationId().isBlank())
            ? req.getConversationId().trim()
            : UUID.randomUUID().toString();

        UserAccount user = (userId != null)
                ? userRepo.findById(userId).orElse(null)
                : null;

        // -------------------------------------------------
        // 1️⃣ LOAD SETTINGS
        // -------------------------------------------------
        ChatbotSettings settings = settingsRepo.findTopByOrderByIdAsc();
        if (settings == null) {
            settings = new ChatbotSettings();
        }

        // -------------------------------------------------
        // 2️⃣ CHECK ENABLED
        // -------------------------------------------------
        if (!settings.isEnabled()) {
            ChatResponseDTO r = new ChatResponseDTO();
            r.setQuestion(question);
            r.setAnswer("⚠️ Chatbot hiện đang được tắt bởi quản trị viên.");
            r.setSources(List.of());
            r.setChunks(List.of());
            r.setConversationId(conversationId);
            return r;
        }

        // -------------------------------------------------
        // 3️⃣ PREPARE REQUEST TO PYTHON
        // -------------------------------------------------
        RestTemplate rest = createRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("question", question);

        // Build settings map safely to avoid NullPointerException when some fields are null
        Map<String, Object> settingsMap = new HashMap<>();
        if (settings.getResponseDelay() != null) settingsMap.put("responseDelay", settings.getResponseDelay());
        if (settings.getDataSource() != null) settingsMap.put("dataSource", settings.getDataSource());
        if (settings.getTemperature() != null) settingsMap.put("temperature", settings.getTemperature());
        if (settings.getMaxTokens() != null) settingsMap.put("maxTokens", settings.getMaxTokens());
        body.put("settings", settingsMap);

        if (conversationId != null && !conversationId.isBlank()) {
            int historyLimit = (settings.getMaxHistory() != null && settings.getMaxHistory() > 0)
                ? Math.min(settings.getMaxHistory(), 5)
                : 5;

            List<ChatbotLog> conversationLogs;
            if (user != null) {
                conversationLogs = chatbotRepo.findTop5ByConversationIdAndUser_UserIdOrderByCreatedAtAsc(conversationId, user.getUserId());
            } else {
                conversationLogs = chatbotRepo.findTop5ByConversationIdOrderByCreatedAtAsc(conversationId);
            }

            if (conversationLogs == null) conversationLogs = List.of();

            List<Map<String, String>> historyList = conversationLogs.stream()
                .map(log -> Map.of(
                    "content", log.getQuestion() == null ? "" : log.getQuestion(),
                    "answer", log.getAnswer() == null ? "" : log.getAnswer()
                ))
                .toList();

            body.put("conversation_id", conversationId);
            body.put("history", historyList);

        }

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, headers);

        // -------------------------------------------------
        // 4️⃣ CALL PYTHON AI
        // -------------------------------------------------
        String answer;
        String sourceType = "unknown";
        String firstChunk = "";

        List<String> sources = new ArrayList<>();
        List<String> chunks = new ArrayList<>();

        try {
            ResponseEntity<?> response =
                    rest.postForEntity(PYTHON_API, entity, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody();

            if (data != null) {
                answer = (String) data.getOrDefault("answer", "");

                if (data.get("sources") instanceof List<?> s) {
                    @SuppressWarnings("unchecked")
                    List<String> castedSources = (List<String>) s;
                    sources = castedSources;
                    if (!sources.isEmpty()) {
                        sourceType = sources.get(0);
                    }
                }

                if (data.get("chunks") instanceof List<?> c) {
                    @SuppressWarnings("unchecked")
                    List<String> castedChunks = (List<String>) c;
                    chunks = castedChunks;
                    if (!chunks.isEmpty()) {
                        firstChunk = chunks.get(0);
                    }
                }
            } else {
                answer = "⚠️ AI không trả về dữ liệu.";
                sourceType = "error";
            }

        } catch (RestClientException e) {
            answer = "⚠️ AI phản hồi quá chậm hoặc không thể kết nối. Vui lòng thử lại.";
            sourceType = "error";
        }

        // -------------------------------------------------
        // 5️⃣ SAVE LOG
        // -------------------------------------------------
        if (saveLog) {
            // determine sequence based on existing history size
            int seq = 1;
            List<ChatbotLog> existing;
            if (user != null) {
                existing = chatbotRepo.findTop5ByConversationIdAndUser_UserIdOrderByCreatedAtAsc(conversationId, user.getUserId());
            } else {
                existing = chatbotRepo.findTop5ByConversationIdOrderByCreatedAtAsc(conversationId);
            }
            if (existing != null) seq = existing.size() + 1;

            ChatbotLog log = new ChatbotLog();
            log.setUser(user);
            log.setQuestion(question);
            log.setAnswer(answer);
            log.setSourceType(sourceType);
            log.setSourceTitle(firstChunk);
            log.setQuestionClean(normalize(question));
            log.setSourceRole("USER");
            log.setConversationId(conversationId);
            log.setSequence(seq);

            chatbotRepo.save(log);
        }

        // -------------------------------------------------
        // 6️⃣ RETURN RESPONSE
        // -------------------------------------------------
        ChatResponseDTO resp = new ChatResponseDTO();
        resp.setQuestion(question);
        resp.setAnswer(answer);
        resp.setSources(sources);
        resp.setChunks(chunks);
        resp.setConversationId(conversationId);
        return resp;
    }

    // =====================================================
    // LIMIT CHAT HISTORY BY maxHistory
    // =====================================================
    public List<ChatHistoryDTO> getHistory(Integer userId) {

        ChatbotSettings settings = settingsRepo.findTopByOrderByIdAsc();

        int limit = (settings != null && settings.getMaxHistory() != null)
                ? settings.getMaxHistory()
                : 10;

        Pageable pageable = PageRequest.of(0, limit);

        List<ChatbotLog> logs =
                chatbotRepo.findLatestByUser(userId, pageable);

        // Đảo lại cho UI (cũ → mới)
        Collections.reverse(logs);

        return logs.stream()
                .map(l -> new ChatHistoryDTO(
                l.getConversationId(),
                        l.getQuestion(),
                        l.getAnswer(),
                        l.getCreatedAt().toString()
                ))
                .toList();
    }

    // =====================================================
    // CLEAR CHAT HISTORY BY USER
    // =====================================================
    public void clearHistory(Integer userId) {
        chatbotRepo.deleteAllByUser_UserId(userId);
    }

    // =====================================================
    // NORMALIZE QUESTION
    // =====================================================
    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9àáạãảâấầậẫẩăắằặẵẳđèéẹẽẻêếềệễể"
                        + "ìíịĩỉòóọõỏôốồộỗổơớờợỡởùúụũủưứừựữử"
                        + "ỳýỵỹỷ\\s]", "").trim();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PYTHON_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(PYTHON_READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
