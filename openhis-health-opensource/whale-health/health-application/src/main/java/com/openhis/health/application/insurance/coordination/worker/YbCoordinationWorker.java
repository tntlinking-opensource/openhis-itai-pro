/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.worker;

import com.openhis.health.application.insurance.config.InsuranceCoordinationProperties;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医保协同单后台 Worker（03-概要设计 §4.3）。
 *
 * <p>定时拉取可运行协同单并委派 {@link YbCoordinationDispatchService} 执行；
 * 同一协同单 ID 在内存中去重，避免并发重复调度。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "insurance.coordination.worker-enabled", havingValue = "true", matchIfMissing = true)
public class YbCoordinationWorker {

    /** 协同单读侧查询（可运行批次）。 */
    private final YbCoordinationQueryService coordinationQueryService;

    /** 协同单步骤派发服务。 */
    private final YbCoordinationDispatchService dispatchService;

    /** 协同 Worker 运行参数（批次大小、退避等）。 */
    private final InsuranceCoordinationProperties properties;

    /** 本进程内正在执行的协同单 ID，防止 tick 与 async 重复派发。 */
    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    /**
     * 定时扫描可运行协同单并逐条派发（fixedDelay 可配置）。
     *
     * <p>每条协同单在 try/finally 中维护 inFlight 标记，派发失败不阻塞后续条目。</p>
     */
    @Scheduled(fixedDelayString = "${insurance.coordination.worker-fixed-delay-ms:5000}")
    public void tick() {
        // Pre：按批次大小拉取当前可运行协同单列表。
        List<YbCoordination> batch = coordinationQueryService.findRunnable(properties.getWorkerBatchSize());
        // Cmd：逐条派发；inFlight 去重避免同一协同单并发执行。
        for (YbCoordination coordination : batch) {
            if (coordination == null || coordination.getId() == null) {
                continue;
            }
            // Gate：已在飞行中的协同单跳过，等待下一轮 tick。
            if (inFlight.putIfAbsent(coordination.getId(), Boolean.TRUE) != null) {
                continue;
            }
            try {
                dispatchService.dispatch(coordination);
            } finally {
                inFlight.remove(coordination.getId());
            }
        }
    }

    /**
     * 异步触发单条协同单派发（结算确认等热路径回调使用）。
     *
     * @param coordinationId 协同单主键；为 null 时静默忽略
     */
    public void dispatchAsync(Long coordinationId) {
        if (coordinationId == null) {
            return;
        }
        try {
            // Cmd：按 ID 加载并进入标准派发流程；异常仅记日志不向上抛出。
            dispatchService.dispatchById(coordinationId);
        } catch (Exception ex) {
            log.warn("async dispatch failed coordinationId={}", coordinationId, ex);
        }
    }
}
