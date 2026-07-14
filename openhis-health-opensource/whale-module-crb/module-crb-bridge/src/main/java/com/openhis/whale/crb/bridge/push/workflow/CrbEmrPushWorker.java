/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.workflow;

import com.openhis.whale.crb.gateway.client.NationalFrontSoftwareClient;
import com.openhis.whale.crb.domain.enums.PushOperation;
import com.openhis.whale.crb.domain.enums.PushStatus;
import com.openhis.whale.crb.domain.push.result.PushResult;
import com.openhis.whale.crb.domain.share.CrbPushConstants;
import com.openhis.whale.crb.domain.push.CrbEmrPushDomainService;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.CrbEmrPushTaskRepository;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.bridge.push.mapper.EmrPayloadMapperRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 推送 Worker：加载 Mapper 组 payload 并调用前置 Client。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrbEmrPushWorker {

    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbEmrPushDomainService pushDomainService;
    private final NationalFrontSoftwareClient nationalFrontSoftwareClient;
    private final EmrPayloadMapperRegistry mapperRegistry;
    private final CrbProperties crbProperties;

    /**
     * 消费单条推送任务。
     *
     * @param taskId 任务 id
     */
    @Transactional
    public void process(Long taskId) {
        if (taskId == null) {
            return;
        }
        Optional<CrbEmrPushTask> optionalTask = taskRepository.findById(taskId);
        if (optionalTask.isEmpty()) {
            return;
        }
        CrbEmrPushTask task = optionalTask.get();
        if (task.getIsDeleted() != null && task.getIsDeleted()) {
            return;
        }
        if (!PushStatus.PENDING.getCode().equals(task.getStatus())) {
            return;
        }

        long started = System.currentTimeMillis();
        String requestSummary = "";
        PushResult result;

        PushOperation operation = resolveOperation(task.getOperation());
        if (operation == PushOperation.DELETE) {
            requestSummary = "DELETE id=" + task.getBusinessKey();
            result = nationalFrontSoftwareClient.delete(task.getResource(), task.getBusinessKey());
        } else {
            Optional<String> payload = mapperRegistry.buildPayloadJson(task);
            if (payload.isEmpty()) {
                result = new PushResult()
                        .setSuccess(false)
                        .setErrorCode("CRB-MAP-001")
                        .setErrorName("Mapper 未就绪")
                        .setDescription("resource=" + task.getResource() + " 尚无 Mapper 实现");
                requestSummary = "UPSERT skipped: no mapper";
            } else {
                requestSummary = truncate(payload.get());
                result = nationalFrontSoftwareClient.upsert(task.getResource(), payload.get());
            }
        }

        long durationMs = System.currentTimeMillis() - started;
        String responseSummary = summarizeResult(result);
        pushDomainService.recordPushAttempt(
                task,
                result,
                requestSummary,
                responseSummary,
                durationMs,
                resolveMaxRetry());

        if (result != null && result.isSuccess()) {
            log.info("CRB push ok, resource={}, businessKey={}", task.getResource(), task.getBusinessKey());
        } else {
            log.warn("CRB push failed, resource={}, businessKey={}, error={}",
                    task.getResource(), task.getBusinessKey(), responseSummary);
        }
    }

    private static PushOperation resolveOperation(String code) {
        if (PushOperation.DELETE.getCode().equals(code)) {
            return PushOperation.DELETE;
        }
        return PushOperation.UPSERT;
    }

    private static String summarizeResult(PushResult result) {
        if (result == null) {
            return "null";
        }
        if (result.isSuccess()) {
            return "success id=" + result.getRemoteId();
        }
        return result.getErrorCode() + ":" + result.getErrorName();
    }

    private int resolveMaxRetry() {
        int configured = crbProperties.getPush().getMaxRetry();
        if (configured < 1) {
            return CrbPushConstants.DEFAULT_MAX_RETRY;
        }
        return configured;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= CrbPushConstants.LOG_SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, CrbPushConstants.LOG_SUMMARY_MAX_LENGTH);
    }
}
