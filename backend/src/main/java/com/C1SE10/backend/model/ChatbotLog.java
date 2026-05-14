package com.C1SE10.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_log", indexes = {
    @Index(name = "idx_chatbot_conversation_sequence", columnList = "conversation_id, sequence")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_title", columnDefinition = "TEXT")
    private String sourceTitle;

    @Column(name = "question_clean", columnDefinition = "TEXT")
    private String questionClean;

    @Column(name = "source_role", length = 20)
    private String sourceRole;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "sequence")
    private Integer sequence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
