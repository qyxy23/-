package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.PlayAccessRequestStatus;
import com.guanyu.haigui.pojo.model.PlayAccessRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayAccessRequestRepository extends JpaRepository<PlayAccessRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, PlayAccessRequestStatus status);

    Optional<PlayAccessRequest> findFirstByUserIdAndStatusOrderByCreateTimeDesc(Long userId, PlayAccessRequestStatus status);

    List<PlayAccessRequest> findByUserIdOrderByCreateTimeDesc(Long userId);

    Page<PlayAccessRequest> findByStatus(PlayAccessRequestStatus status, Pageable pageable);

    Page<PlayAccessRequest> findAllByOrderByCreateTimeDesc(Pageable pageable);

    Optional<PlayAccessRequest> findFirstByUserIdAndStatusAndReviewedAtAfterOrderByReviewedAtDesc(
            Long userId, PlayAccessRequestStatus status, LocalDateTime reviewedAfter);
}
