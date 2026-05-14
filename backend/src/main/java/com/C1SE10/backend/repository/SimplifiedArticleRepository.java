package com.C1SE10.backend.repository;

import com.C1SE10.backend.model.SimplifiedArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimplifiedArticleRepository extends JpaRepository<SimplifiedArticle, Integer> {

    Page<SimplifiedArticle> findByStatus(SimplifiedArticle.Status status, Pageable pageable);

    // Lấy tất cả simplified articles theo moderator ID
    List<SimplifiedArticle> findByModerator_UserId(Integer moderatorId);

       // Lấy simplified articles theo moderator ID và status
       List<SimplifiedArticle> findByModerator_UserIdAndStatus(Integer moderatorId, SimplifiedArticle.Status status);

    // Lấy tất cả simplified articles theo article ID
    List<SimplifiedArticle> findByArticle_ArticleId(Integer articleId);

    // Lấy simplified article theo article ID và moderator ID
    Optional<SimplifiedArticle> findByArticle_ArticleIdAndModerator_UserId(Integer articleId, Integer moderatorId);

       // Lấy simplified articles theo article ID và status
       List<SimplifiedArticle> findByArticle_ArticleIdAndStatus(Integer articleId, SimplifiedArticle.Status status);

    // Tìm kiếm bằng từ khóa trong nội dung hoặc category
    @Query("SELECT s FROM SimplifiedArticle s " +
           "WHERE LOWER(s.contentSimplified) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.category) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<SimplifiedArticle> searchByKeyword(@Param("keyword") String keyword);

    // Tìm simplified article đã được APPROVED theo article ID
    @Query("SELECT s FROM SimplifiedArticle s " +
           "LEFT JOIN FETCH s.article " +
           "LEFT JOIN FETCH s.moderator " +
           "WHERE s.article.articleId = :articleId " +
           "AND s.status = com.C1SE10.backend.model.SimplifiedArticle.Status.APPROVED")
    Optional<SimplifiedArticle> findApprovedByArticleId(@Param("articleId") Integer articleId);

    // Tìm tất cả simplified articles theo article ID (mọi trạng thái)
    @Query("SELECT s FROM SimplifiedArticle s " +
           "WHERE s.article.articleId = :articleId " +
           "ORDER BY s.createdAt DESC")
    List<SimplifiedArticle> findAllByArticleId(@Param("articleId") Integer articleId);

    // Lấy tất cả simplified articles đã được APPROVED
    @Query("SELECT s FROM SimplifiedArticle s " +
           "WHERE s.status = com.C1SE10.backend.model.SimplifiedArticle.Status.APPROVED")
    List<SimplifiedArticle> findAllApproved();

    // Lấy tất cả simplified articles theo status
    List<SimplifiedArticle> findByStatus(SimplifiedArticle.Status status);
    
    // Đếm số simplified articles theo status
    long countByStatus(SimplifiedArticle.Status status);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<SimplifiedArticle> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}

