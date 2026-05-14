package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.request.moderator.SimplifiedArticleRequestDTO;
import com.C1SE10.backend.dto.response.moderator.SimplifiedArticleResponseDTO;
import com.C1SE10.backend.model.SimplifiedArticle;
import com.C1SE10.backend.service.moderator.SimplifiedArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/moderator/simplified")
@RequiredArgsConstructor
@CrossOrigin("*")
public class SimplifiedArticleController {

    private final SimplifiedArticleService simplifiedService;

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody SimplifiedArticleRequestDTO req) {
        try {
            SimplifiedArticle sa = simplifiedService.createOrUpdateSimplified(
                    req.getArticleId(),
                    req.getModeratorId(),
                    req.getCategory(),
                    req.getContentSimplified()
            );
            return ResponseEntity.ok(sa);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/by-article/{articleId}")
    public ResponseEntity<?> getSimplifiedByArticle(@PathVariable Integer articleId) {
        return simplifiedService.getByArticleId(articleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/mine/{moderatorId}")
    public ResponseEntity<List<SimplifiedArticleResponseDTO>> getMine(@PathVariable Integer moderatorId) {
        return ResponseEntity.ok(simplifiedService.getByModerator(moderatorId));
    }

    @PutMapping("/{simplifiedId}/approve")
    public ResponseEntity<?> approveOne(
            @PathVariable Integer simplifiedId,
            @RequestParam Integer moderatorId
    ) {
        try {
            return ResponseEntity.ok(simplifiedService.approveOne(simplifiedId, moderatorId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/approve-all/{moderatorId}")
    public ResponseEntity<?> approveAll(@PathVariable Integer moderatorId) {
        try {
            int updated = simplifiedService.approveAll(moderatorId);
            return ResponseEntity.ok(Map.of(
                    "updated", updated,
                    "message", "Approved all simplified items successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/hide-all/{moderatorId}")
    public ResponseEntity<?> hideAll(@PathVariable Integer moderatorId) {
        try {
            int updated = simplifiedService.hideAllFromUser(moderatorId);
            return ResponseEntity.ok(Map.of(
                    "updated", updated,
                    "message", "Hidden all simplified items from users successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/show-all/{moderatorId}")
    public ResponseEntity<?> showAll(@PathVariable Integer moderatorId) {
        try {
            int updated = simplifiedService.showAllToUser(moderatorId);
            return ResponseEntity.ok(Map.of(
                    "updated", updated,
                    "message", "Shown all simplified items to users successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{simplifiedId}/hide-from-user")
    public ResponseEntity<?> hideFromUser(
            @PathVariable Integer simplifiedId,
            @RequestParam Integer moderatorId
    ) {
        try {
            return ResponseEntity.ok(simplifiedService.hideFromUser(simplifiedId, moderatorId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{simplifiedId}/show-to-user")
    public ResponseEntity<?> showToUser(
            @PathVariable Integer simplifiedId,
            @RequestParam Integer moderatorId
    ) {
        try {
            return ResponseEntity.ok(simplifiedService.showToUser(simplifiedId, moderatorId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}


