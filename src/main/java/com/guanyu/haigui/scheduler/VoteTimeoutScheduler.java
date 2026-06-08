package com.guanyu.haigui.scheduler;

import com.guanyu.haigui.service.VoteTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteTimeoutScheduler {

    private static final long CHECK_INTERVAL_MS = 60_000;

    private final VoteTimeoutService voteTimeoutService;

    @Scheduled(fixedRate = CHECK_INTERVAL_MS)
    public void checkVoteTimeouts() {
        int expired = voteTimeoutService.expireOverdueVotes();
        if (expired > 0) {
            log.info("vote timeout scheduler expired {} session(s)", expired);
        }
    }
}
