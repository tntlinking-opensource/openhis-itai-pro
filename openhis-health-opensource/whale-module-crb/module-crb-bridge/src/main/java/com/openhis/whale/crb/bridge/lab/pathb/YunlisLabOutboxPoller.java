/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 云 LIS outbox 轮询器：按配置间隔触发 {@link YunlisLabOutboxConsumer}。
 *
 * <p>轮询间隔取自 {@code openhis.crb.lab.outbox-poll-seconds}（经 {@link YunlisOutboxPollProperties} 换算毫秒）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.crb.lab.yunlis.enabled", havingValue = "true")
public class YunlisLabOutboxPoller {

    private final YunlisOutboxPollProperties pollProperties;
    private final YunlisLabOutboxConsumer outboxConsumer;

    /**
     * 轮询 yunlis outbox 并消费待推送检验结果。
     */
    @Scheduled(fixedDelayString = "#{@yunlisOutboxPollProperties.outboxPollIntervalMs}")
    public void pollOutbox() {
        log.debug(
                "Yunlis outbox poll tick (source={}, intervalMs={})",
                pollProperties.getSource(),
                pollProperties.getOutboxPollIntervalMs());
        outboxConsumer.consumeBatch();
    }
}
