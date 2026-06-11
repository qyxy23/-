package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.*;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.Exception.PlayQuotaException;
import com.guanyu.haigui.config.PlayQuotaProperties;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.GrantPlayQuotaDTO;
import com.guanyu.haigui.pojo.dto.QueryPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.ReviewPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.SubmitPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestListVO;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestVO;
import com.guanyu.haigui.pojo.vo.PlayQuotaSummaryVO;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.PlayQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class PlayQuotaServiceImpl implements PlayQuotaService {

    private final UserPlayQuotaRepository userPlayQuotaRepository;
    private final PlayAccessRequestRepository playAccessRequestRepository;
    private final PlayQuotaLedgerRepository playQuotaLedgerRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserInfoRepository userInfoRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final PlayQuotaProperties playQuotaProperties;

    @Override
    public void initForNewUser(Long userId) {
        if (userPlayQuotaRepository.existsById(userId)) {
            return;
        }
        int grant = playQuotaProperties.getRegisterGrant();
        long endedSessions = gameSessionRepository.countChargedOrEndedByUserId(userId);
        int remaining = Math.max(0, grant - (int) endedSessions);

        UserPlayQuota quota = new UserPlayQuota();
        quota.setUserId(userId);
        quota.setGlobalGamesRemaining(remaining);
        quota.setUnlimited(isRoleExempt(userId));
        quota.setTotalConsumed((int) endedSessions);
        userPlayQuotaRepository.save(quota);

        if (remaining > 0) {
            UserEntitlement entitlement = new UserEntitlement();
            entitlement.setUserId(userId);
            entitlement.setEntitlementType(EntitlementType.GLOBAL_GAME);
            entitlement.setQuantityRemaining(remaining);
            entitlement.setSource(EntitlementSource.REGISTER);
            userEntitlementRepository.save(entitlement);
            appendLedger(userId, remaining, remaining, PlayQuotaLedgerReason.REGISTER.name(), null, null);
        }
        log.info("初始化用户游玩额度 userId={}, remaining={}", userId, remaining);
    }

    @Override
    public PlayQuotaSummaryVO getMySummary() {
        Long userId = BaseContext.getCurrentId();
        UserPlayQuota quota = ensureQuotaRow(userId);
        return buildSummary(quota, userId);
    }

    @Override
    public void assertCanStartNewGame(Long userId) {
        UserPlayQuota quota = ensureQuotaRow(userId);
        if (Boolean.TRUE.equals(quota.getUnlimited()) || isRoleExempt(userId)) {
            return;
        }
        int effective = computeEffectiveRemaining(userId, quota.getGlobalGamesRemaining());
        if (effective <= 0) {
            throw PlayQuotaException.exhausted(
                    "免费游玩次数已用完，请提交申请或联系开发者获取额度");
        }
    }

    @Override
    public void chargeOnSettlement(String gameSessionId) {
        GameSession session = gameSessionRepository.findById(gameSessionId).orElse(null);
        if (session == null || Boolean.TRUE.equals(session.getQuotaCharged())) {
            return;
        }
        if (session.getStatus() == GameSession.GameSessionStatus.ONGOING) {
            return;
        }

        Long payerId = session.getUserId();
        UserPlayQuota quota = ensureQuotaRow(payerId);
        session.setQuotaCharged(true);
        gameSessionRepository.save(session);

        if (Boolean.TRUE.equals(quota.getUnlimited()) || isRoleExempt(payerId)) {
            log.debug("免扣额度结算 gameSessionId={}, payerId={}", gameSessionId, payerId);
            return;
        }

        int remaining = quota.getGlobalGamesRemaining();
        if (remaining <= 0) {
            log.warn("结算扣减时额度已为 0 gameSessionId={}, payerId={}", gameSessionId, payerId);
            return;
        }

        quota.setGlobalGamesRemaining(remaining - 1);
        quota.setTotalConsumed(quota.getTotalConsumed() + 1);
        userPlayQuotaRepository.save(quota);
        appendLedger(payerId, -1, quota.getGlobalGamesRemaining(),
                PlayQuotaLedgerReason.CONSUME.name(), gameSessionId, null);
        log.info("扣减游玩额度 gameSessionId={}, payerId={}, balance={}", gameSessionId, payerId, quota.getGlobalGamesRemaining());
    }

    @Override
    public PlayAccessRequestVO submitAccessRequest(SubmitPlayAccessRequestDTO dto) {
        Long userId = BaseContext.getCurrentId();
        ensureQuotaRow(userId);

        if (playAccessRequestRepository.existsByUserIdAndStatus(userId, PlayAccessRequestStatus.PENDING)) {
            throw PlayQuotaException.pendingRequest("您已有待处理的申请，请耐心等待审核");
        }

        LocalDateTime cooldownSince = LocalDateTime.now().minusDays(playQuotaProperties.getRejectCooldownDays());
        playAccessRequestRepository
                .findFirstByUserIdAndStatusAndReviewedAtAfterOrderByReviewedAtDesc(
                        userId, PlayAccessRequestStatus.REJECTED, cooldownSince)
                .ifPresent(r -> {
                    throw new BusinessException(400,
                            "申请被拒绝后需等待 " + playQuotaProperties.getRejectCooldownDays() + " 天再提交");
                });

        PlayAccessRequest request = new PlayAccessRequest();
        request.setUserId(userId);
        request.setStatus(PlayAccessRequestStatus.PENDING);
        request.setUserMessage(StringUtils.hasText(dto.getUserMessage()) ? dto.getUserMessage().trim() : null);
        playAccessRequestRepository.save(request);
        return toRequestVOList(List.of(request)).get(0);
    }

    @Override
    public List<PlayAccessRequestVO> listMyRequests() {
        Long userId = BaseContext.getCurrentId();
        return toRequestVOList(playAccessRequestRepository.findByUserIdOrderByCreateTimeDesc(userId));
    }

    @Override
    public PlayAccessRequestListVO listAccessRequestsForReview(QueryPlayAccessRequestDTO dto) {
        assertAuditPermission();
        int pageNum = dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? Math.min(dto.getPageSize(), 50) : 10;
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<PlayAccessRequest> page;
        if (StringUtils.hasText(dto.getStatus())) {
            PlayAccessRequestStatus status = PlayAccessRequestStatus.valueOf(dto.getStatus().trim().toUpperCase());
            page = playAccessRequestRepository.findByStatus(status, pageable);
        } else {
            page = playAccessRequestRepository.findAllByOrderByCreateTimeDesc(pageable);
        }

        PlayAccessRequestListVO vo = new PlayAccessRequestListVO();
        vo.setList(toRequestVOList(page.getContent()));
        vo.setTotal(page.getTotalElements());
        vo.setPages(page.getTotalPages());
        vo.setPageNum(pageNum);
        return vo;
    }

    @Override
    public PlayAccessRequestVO reviewAccessRequest(ReviewPlayAccessRequestDTO dto) {
        assertAuditPermission();
        if (dto.getRequestId() == null || dto.getApproved() == null) {
            throw new BusinessException(400, "参数不完整");
        }

        PlayAccessRequest request = playAccessRequestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (request.getStatus() != PlayAccessRequestStatus.PENDING) {
            throw new BusinessException(400, "该申请已处理");
        }

        Long reviewerId = BaseContext.getCurrentId();
        request.setReviewerId(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminNote(StringUtils.hasText(dto.getAdminNote()) ? dto.getAdminNote().trim() : null);

        if (Boolean.TRUE.equals(dto.getApproved())) {
            int grant = dto.getGrantedGames() != null && dto.getGrantedGames() > 0
                    ? dto.getGrantedGames()
                    : playQuotaProperties.getApprovalGrant();
            request.setStatus(PlayAccessRequestStatus.APPROVED);
            request.setGrantedGames(grant);
            playAccessRequestRepository.save(request);
            grantGamesInternal(request.getUserId(), grant, EntitlementSource.APPROVAL,
                    request.getRequestId(), PlayQuotaLedgerReason.APPROVAL.name());
        } else {
            request.setStatus(PlayAccessRequestStatus.REJECTED);
            request.setGrantedGames(null);
            playAccessRequestRepository.save(request);
        }
        return toRequestVOList(List.of(request)).get(0);
    }

    @Override
    public void adminGrantQuota(GrantPlayQuotaDTO dto) {
        assertAdminPermission();
        if (dto.getUserId() == null || dto.getGames() == null || dto.getGames() <= 0) {
            throw new BusinessException(400, "请指定用户和有效局数");
        }
        userInfoRepository.findById(dto.getUserId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        ensureQuotaRow(dto.getUserId());
        grantGamesInternal(dto.getUserId(), dto.getGames(), EntitlementSource.ADMIN_GRANT,
                null, PlayQuotaLedgerReason.ADMIN_GRANT.name());
    }

    private UserPlayQuota ensureQuotaRow(Long userId) {
        return userPlayQuotaRepository.findById(userId).orElseGet(() -> {
            initForNewUser(userId);
            return userPlayQuotaRepository.findById(userId).orElseThrow();
        });
    }

    private PlayQuotaSummaryVO buildSummary(UserPlayQuota quota, Long userId) {
        int ongoing = countOngoingUncharged(userId);
        int effective = Boolean.TRUE.equals(quota.getUnlimited()) || isRoleExempt(userId)
                ? Integer.MAX_VALUE
                : computeEffectiveRemaining(userId, quota.getGlobalGamesRemaining());

        PlayQuotaSummaryVO vo = new PlayQuotaSummaryVO();
        vo.setGlobalGamesRemaining(quota.getGlobalGamesRemaining());
        vo.setOngoingUnchargedCount(ongoing);
        vo.setEffectiveRemaining(effective == Integer.MAX_VALUE ? quota.getGlobalGamesRemaining() : effective);
        vo.setUnlimited(Boolean.TRUE.equals(quota.getUnlimited()) || isRoleExempt(userId));
        vo.setTotalConsumed(quota.getTotalConsumed());
        vo.setHasPendingRequest(playAccessRequestRepository.existsByUserIdAndStatus(
                userId, PlayAccessRequestStatus.PENDING));
        playAccessRequestRepository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(
                        userId, PlayAccessRequestStatus.PENDING)
                .or(() -> playAccessRequestRepository.findByUserIdOrderByCreateTimeDesc(userId).stream().findFirst())
                .ifPresent(r -> vo.setLatestRequestStatus(r.getStatus().name()));
        return vo;
    }

    private int computeEffectiveRemaining(Long userId, int globalRemaining) {
        return globalRemaining - countOngoingUncharged(userId);
    }

    private int countOngoingUncharged(Long userId) {
        Long count = gameSessionRepository.countByUserIdAndStatusAndQuotaChargedFalseAndIsDeletedFalse(
                userId, GameSession.GameSessionStatus.ONGOING);
        return count != null ? count.intValue() : 0;
    }

    private void grantGamesInternal(Long userId, int games, EntitlementSource source,
                                    Long sourceRefId, String ledgerReason) {
        UserPlayQuota quota = ensureQuotaRow(userId);
        quota.setGlobalGamesRemaining(quota.getGlobalGamesRemaining() + games);
        userPlayQuotaRepository.save(quota);

        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUserId(userId);
        entitlement.setEntitlementType(EntitlementType.GLOBAL_GAME);
        entitlement.setQuantityRemaining(games);
        entitlement.setSource(source);
        entitlement.setSourceRefId(sourceRefId);
        userEntitlementRepository.save(entitlement);

        appendLedger(userId, games, quota.getGlobalGamesRemaining(), ledgerReason, null, sourceRefId);
    }

    private void appendLedger(Long userId, int delta, int balanceAfter, String reason,
                              String gameSessionId, Long sourceRefId) {
        PlayQuotaLedger ledger = new PlayQuotaLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setReason(reason);
        ledger.setGameSessionId(gameSessionId);
        ledger.setSourceRefId(sourceRefId);
        playQuotaLedgerRepository.save(ledger);
    }

    private List<PlayAccessRequestVO> toRequestVOList(List<PlayAccessRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> applicantIds = new HashSet<>();
        for (PlayAccessRequest request : requests) {
            userIds.add(request.getUserId());
            applicantIds.add(request.getUserId());
            if (request.getReviewerId() != null) {
                userIds.add(request.getReviewerId());
            }
        }
        Map<Long, UserInfo> userMap = userInfoRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
        Map<Long, Long> playedCountMap = loadPlayedCountMap(applicantIds);
        return requests.stream()
                .map(request -> toRequestVO(request, userMap, playedCountMap))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> loadPlayedCountMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : gameSessionRepository.countChargedOrEndedByUserIds(userIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private PlayAccessRequestVO toRequestVO(
            PlayAccessRequest request,
            Map<Long, UserInfo> userMap,
            Map<Long, Long> playedCountMap) {
        PlayAccessRequestVO vo = new PlayAccessRequestVO();
        vo.setRequestId(request.getRequestId());
        vo.setUserId(request.getUserId());
        vo.setStatus(request.getStatus().name());
        vo.setUserMessage(request.getUserMessage());
        vo.setAdminNote(request.getAdminNote());
        vo.setReviewerId(request.getReviewerId());
        vo.setGrantedGames(request.getGrantedGames());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setCreateTime(request.getCreateTime());

        UserInfo applicant = userMap.get(request.getUserId());
        if (applicant != null) {
            vo.setUsername(applicant.getUsername());
            vo.setNickname(applicant.getUsername());
        }
        if (request.getReviewerId() != null) {
            UserInfo reviewer = userMap.get(request.getReviewerId());
            if (reviewer != null) {
                vo.setReviewerNickname(reviewer.getUsername());
            }
        }
        vo.setTotalPlayedSessions(playedCountMap.getOrDefault(request.getUserId(), 0L));
        return vo;
    }

    private boolean isRoleExempt(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()));
    }

    private void assertAuditPermission() {
        Long userId = BaseContext.getCurrentId();
        if (!isRoleExempt(userId)) {
            throw new BusinessException(403, "您不是审核员，无权限");
        }
    }

    private void assertAdminPermission() {
        Long userId = BaseContext.getCurrentId();
        boolean isAdmin = sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
        if (!isAdmin) {
            throw new BusinessException(403, "仅管理员可操作");
        }
    }
}
