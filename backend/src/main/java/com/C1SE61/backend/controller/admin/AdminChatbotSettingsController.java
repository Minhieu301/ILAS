package com.C1SE61.backend.controller.admin;

import com.C1SE61.backend.dto.response.LawDTO;
import com.C1SE61.backend.repository.LawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminChatbotSettingsController {

    private final LawRepository lawRepository;

    /**
     * Get all available laws for domain configuration
     */
    @GetMapping("/laws")
    public ResponseEntity<List<LawDTO>> getAllLaws() {
        List<LawDTO> laws = lawRepository.findAll().stream()
                .map(law -> LawDTO.builder()
                        .lawId(law.getLawId())
                        .title(law.getTitle())
                        .code(law.getCode())
                        .build())
                .toList();
        return ResponseEntity.ok(laws);
    }
}
