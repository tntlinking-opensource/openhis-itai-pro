package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.domain.invokelog.entity.EinvBossInvokeLog;
import com.openhis.whale.einvoice.boss.domain.invokelog.repository.EinvBossInvokeLogRepository;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 写入 einv_boss_invoke_log（脱敏摘要）。
 */
@Component
@RequiredArgsConstructor
public class BossInvokeLogWriter {

    private static final int DIGEST_MAX = 480;

    private final EinvBossInvokeLogRepository invokeLogRepository;

    /**
     * 记录一次博思 HTTP 调用。
     *
     * @param task 关联任务
     * @param serviceId 博思 serviceId
     * @param busNo 业务流水号
     * @param invokeResult 解析结果
     * @param durationMs 耗时
     * @param requestJson 请求明文
     * @param responseBody 响应原文
     */
    public void write(
            EinvBossIssueTask task,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
        write(task.getTenantId(), task.getId(), serviceId, busNo, invokeResult, durationMs, requestJson, responseBody);
    }

    /**
     * 记录一次博思 HTTP 调用（无关联任务，如冲红/查票）。
     *
     * @param tenantId 租户主键
     * @param serviceId 博思 serviceId
     * @param busNo 业务流水号
     * @param invokeResult 解析结果
     * @param durationMs 耗时
     * @param requestJson 请求明文
     * @param responseBody 响应原文
     */
    public void writeWithoutTask(
            Long tenantId,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
        write(tenantId, null, serviceId, busNo, invokeResult, durationMs, requestJson, responseBody);
    }

    private void write(
            Long tenantId,
            Long taskId,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
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
        invokeLogRepository.save(log);
    }

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
