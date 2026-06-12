package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.AchievementCode;
import com.guanyu.haigui.pojo.model.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    Optional<UserAchievement> findByUserIdAndAchievementCode(Long userId, AchievementCode achievementCode);

    List<UserAchievement> findByUserId(Long userId);

    List<UserAchievement> findByUserIdAndUnlockSessionId(Long userId, String unlockSessionId);

    boolean existsByUserIdAndAchievementCodeAndUnlockedAtIsNotNull(Long userId, AchievementCode achievementCode);

    @Query(value = """
            SELECT COUNT(DISTINCT soup_id) FROM (
              SELECT gs.soup_id AS soup_id FROM haigui_game_session gs
              WHERE gs.user_id = :userId AND gs.play_mode = 'SOLO' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
              UNION
              SELECT gs.soup_id AS soup_id FROM chat_game_members cm
              INNER JOIN chat_games cg ON cm.room_id = cg.room_id
              INNER JOIN haigui_game_session gs ON cg.session_id = gs.session_id
              WHERE cm.member_id = :userId AND gs.play_mode = 'MULTI' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
            ) played
            """, nativeQuery = true)
    Long countDistinctPlayedSoups(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) FROM (
              SELECT gs.session_id FROM haigui_game_session gs
              WHERE gs.user_id = :userId AND gs.play_mode = 'SOLO' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
              UNION
              SELECT gs.session_id FROM chat_game_members cm
              INNER JOIN chat_games cg ON cm.room_id = cg.room_id
              INNER JOIN haigui_game_session gs ON cg.session_id = gs.session_id
              WHERE cm.member_id = :userId AND gs.play_mode = 'MULTI' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
            ) sessions
            """, nativeQuery = true)
    Long countValidCompletedParticipations(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(DISTINCT soup_id) FROM (
              SELECT gs.soup_id AS soup_id FROM haigui_game_session gs
              INNER JOIN hai_gui_soup soup ON gs.soup_id = soup.soup_id
              WHERE gs.user_id = :userId AND gs.play_mode = 'SOLO' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
                AND soup.tags = :tagLabel
              UNION
              SELECT gs.soup_id AS soup_id FROM chat_game_members cm
              INNER JOIN chat_games cg ON cm.room_id = cg.room_id
              INNER JOIN haigui_game_session gs ON cg.session_id = gs.session_id
              INNER JOIN hai_gui_soup soup ON gs.soup_id = soup.soup_id
              WHERE cm.member_id = :userId AND gs.play_mode = 'MULTI' AND gs.status = 'COMPLETED'
                AND gs.is_deleted = 0
                AND (gs.end_reason IS NULL OR gs.end_reason <> 'ROOM_DISBANDED')
                AND soup.tags = :tagLabel
            ) tagged
            """, nativeQuery = true)
    Long countDistinctTaggedSoups(@Param("userId") Long userId, @Param("tagLabel") String tagLabel);
}
