package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 游戏会话数据访问接口
 */
@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, String> {

    /**
     * 根据海龟汤ID查找所有会话
     * @param soupId 海龟汤ID
     * @return 会话列表
     */
    List<GameSession> findBySoupIdAndIsDeletedFalse(String soupId);

    /**
     * 根据用户ID查找所有会话
     * @param userId 用户ID
     * @return 会话列表
     */
    List<GameSession> findByUserIdAndIsDeletedFalse(Long userId);

    /**
     * 根据海龟汤ID和用户ID查找会话
     * @param soupId 海龟汤ID
     * @param userId 用户ID
     * @return 会话列表
     */
    List<GameSession> findBySoupIdAndUserIdAndIsDeletedFalse(String soupId, Long userId);

    /**
     * 根据海龟汤ID和用户ID查找最新的会话
     * @param soupId 海龟汤ID
     * @param userId 用户ID
     * @return 最新的会话
     */
    Optional<GameSession> findTopBySoupIdAndUserIdAndIsDeletedFalseOrderByStartTimeDesc(String soupId, Long userId);

    /**
     * 根据状态查找会话
     * @param status 会话状态
     * @return 会话列表
     */
    List<GameSession> findByStatusAndIsDeletedFalse(GameSession.GameSessionStatus status);

    /**
     * 根据海龟汤ID和状态查找会话
     * @param soupId 海龟汤ID
     * @param status 会话状态
     * @return 会话列表
     */
    List<GameSession> findBySoupIdAndStatusAndIsDeletedFalse(String soupId, GameSession.GameSessionStatus status);

    /**
     * 根据用户ID和状态查找会话
     * @param userId 用户ID
     * @param status 会话状态
     * @return 会话列表
     */
    List<GameSession> findByUserIdAndStatusAndIsDeletedFalse(Long userId, GameSession.GameSessionStatus status);

    /**
     * 查找正在进行中的会话
     * @param userId 用户ID
     * @return 进行中的会话列表
     */
    List<GameSession> findByUserIdAndStatusEqualsAndIsDeletedFalseOrderByStartTimeDesc(Long userId, GameSession.GameSessionStatus status);

    /**
     * 查找指定时间之后创建的会话
     * @param userId 用户ID
     * @param afterTime 时间
     * @return 会话列表
     */
    @Query("SELECT gs FROM GameSession gs WHERE gs.userId = :userId AND gs.startTime > :afterTime AND gs.isDeleted = false ORDER BY gs.startTime DESC")
    List<GameSession> findByUserIdAndStartTimeAfter(@Param("userId") Long userId, @Param("afterTime") LocalDateTime afterTime);

    /**
     * 统计用户的海龟汤游戏次数
     * @param userId 用户ID
     * @return 游戏次数
     */
    @Query("SELECT COUNT(gs) FROM GameSession gs WHERE gs.userId = :userId AND gs.isDeleted = false")
    Long countByUserIdAndIsDeletedFalse(@Param("userId") Long userId);

    /**
     * 统计海龟汤的总游戏次数
     * @param soupId 海龟汤ID
     * @return 游戏次数
     */
    @Query("SELECT COUNT(gs) FROM GameSession gs WHERE gs.soupId = :soupId AND gs.isDeleted = false")
    Long countBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 统计用户完成的游戏次数
     * @param userId 用户ID
     * @return 完成次数
     */
    @Query("SELECT COUNT(gs) FROM GameSession gs WHERE gs.userId = :userId AND gs.status = 'COMPLETED' AND gs.isDeleted = false")
    Long countCompletedByUserIdAndIsDeletedFalse(@Param("userId") Long userId);

    /**
     * 计算用户平均游戏进度
     * @param userId 用户ID
     * @return 平均进度
     */
    @Query("SELECT AVG(gs.currentProgress) FROM GameSession gs WHERE gs.userId = :userId AND gs.isDeleted = false")
    Double getAverageProgressByUserId(@Param("userId") Long userId);

    /**
     * 查找用户得分最高的游戏会话
     * @param userId 用户ID
     * @return 得分最高的会话
     */
    @Query("SELECT gs FROM GameSession gs WHERE gs.userId = :userId AND gs.isDeleted = false ORDER BY gs.score DESC")
    List<GameSession> findTopByUserIdOrderByScoreDesc(@Param("userId") Long userId);

    /**
     * 查找用户游戏时间最长的会话
     * @param userId 用户ID
     * @return 游戏时间最长的会话
     */
    @Query("SELECT gs FROM GameSession gs WHERE gs.userId = :userId AND gs.isDeleted = false ORDER BY (gs.endTime - gs.startTime) DESC")
    List<GameSession> findTopByUserIdOrderByDurationDesc(@Param("userId") Long userId);

    /**
     * 批量删除用户的旧会话（逻辑删除）
     * @param userId 用户ID
     * @param beforeTime 保留此时间之后的会话
     * @return 删除数量
     */
    @Query("UPDATE GameSession gs SET gs.isDeleted = true WHERE gs.userId = :userId AND gs.startTime < :beforeTime AND gs.isDeleted = false")
    int softDeleteOldSessions(@Param("userId") Long userId, @Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 查找最近N天的活跃会话
     * @param userId 用户ID
     * @param since 天数
     * @return 活跃会话列表
     */
    @Query("SELECT gs FROM GameSession gs WHERE gs.userId = :userId AND gs.startTime >= :since AND gs.isDeleted = false ORDER BY gs.startTime DESC")
    List<GameSession> findRecentActiveSessions(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    List<GameSession> findBySoupIdAndUserIdAndIsDeletedFalseOrderByStartTimeDesc(String soupId, Long currentId);

    Optional<GameSession> findFirstByUserIdAndSoupIdAndPlayModeAndStatusAndIsDeletedFalseOrderByStartTimeDesc(
            Long userId,
            String soupId,
            com.guanyu.haigui.Enum.PlayMode playMode,
            GameSession.GameSessionStatus status);

    List<GameSession> findByUserIdAndPlayModeAndStatusAndIsDeletedFalseOrderByStartTimeDesc(
            Long userId,
            com.guanyu.haigui.Enum.PlayMode playMode,
            GameSession.GameSessionStatus status);

    Long countByUserIdAndStatusAndQuotaChargedFalseAndIsDeletedFalse(
            Long userId, GameSession.GameSessionStatus status);

    List<GameSession> findByUserIdAndPlayModeAndStatusInAndIsDeletedFalseOrderByEndTimeDesc(
            Long userId,
            com.guanyu.haigui.Enum.PlayMode playMode,
            Collection<GameSession.GameSessionStatus> statuses);

    @Query("""
            SELECT COUNT(gs) FROM GameSession gs
            WHERE gs.userId = :userId AND gs.isDeleted = false
              AND (gs.quotaCharged = true OR gs.status IN ('COMPLETED', 'CANCELED'))
            """)
    Long countChargedOrEndedByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT gs.userId, COUNT(gs) FROM GameSession gs
            WHERE gs.userId IN :userIds AND gs.isDeleted = false
              AND (gs.quotaCharged = true OR gs.status IN ('COMPLETED', 'CANCELED'))
            GROUP BY gs.userId
            """)
    List<Object[]> countChargedOrEndedByUserIds(@Param("userIds") Collection<Long> userIds);
}