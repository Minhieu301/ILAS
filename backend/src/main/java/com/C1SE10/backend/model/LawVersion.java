package com.C1SE10.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "law_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LawVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Integer versionId;

    @Column(name = "law_id", nullable = false)
    private Integer lawId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "changed_by")
    private Integer changedBy;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}