/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.domain.invoke.entity.EinvBossInvokeLog;
import com.openhis.whale.einvoice.boss.domain.invoke.repository.EinvBossInvokeLogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 博思调用日志持久化（独立事务 REQUIRES_NEW，不随 Worker 主事务回滚）。
 */
@Service
@RequiredArgsConstructor
public class BossInvokeLogPersistService {

    /** 请求/响应摘要最大长度（超出截断）。 */
    private static final int DIGEST_MAX = 480;

    private final EinvBossInvokeLogRepository invokeLogRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 写入一条 invoke 审计记录。
     *
     * @param tenantId 租户
     * @param taskId 关联任务，可 null
     * @param serviceId 博思 serviceId
     * @param busNo 业务流水号
     * @param invokeResult 解析后的响应
     * @param durationMs 耗时毫秒
     * @param requestJson 原始请求（存摘要）
     * @param responseBody 原始响应（存摘要）
     */
    public void persist(
            Long tenantId,
            Long taskId,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            EinvBossInvokeLog log = new EinvBossInvokeLog();
            log.setTenantId(tenantId);
            log.setTaskId(taskId);
            log.setServiceId(serviceId);
            log.setBusNo(busNo);
            log.setResultCode(invokeResult.getResultCode());
            log.setDurationMs(durationMs);
            log.setRequestDigest(digest(requestJson));
            log.setResponseDigest(digest(responseBody));
            log.setCreatedAt(LocalDateTime.now());
            invokeLogRepository.saveAndFlush(log);
        });
    }

    /** 截断长文本为可入库摘要。 */
    private String digest(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        if (text.length() <= DIGEST_MAX) {
            return text;
        }
        return text.substring(0, DIGEST_MAX);
    }
}
