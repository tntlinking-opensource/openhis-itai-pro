/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.integrationlog.service;

import com.zxplat.plugin.pacs.domain.integrationlog.entity.PacsIntegrationLog;
import com.zxplat.plugin.pacs.domain.integrationlog.repository.PacsIntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PACS 集成日志写侧（序号 16）。
 */
@Service
@RequiredArgsConstructor
public class PacsIntegrationLogDomainService {

    public static final String DIRECTION_OUTBOUND = "outbound";
    public static final String DIRECTION_INBOUND = "inbound";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    public static final String ACTION_SEND_APPLY = "sendApply";
    public static final String ACTION_REPORT_FINALIZE = "reportFinalize";
    public static final String ACTION_REPORT_PATH = "reportPath";
    public static final String ACTION_RETRY = "retry";

    private final PacsIntegrationLogRepository repository;

    /**
     * 写入一条集成日志。
     *
     * @param direction 方向
     * @param actionCode 动作
     * @param entityType 业务类型
     * @param entityId 业务主键
     * @param outcome 结果
     * @param requestBody 请求
     * @param responseBody 响应
     * @param errorMessage 错误
     * @param operatorId 操作人
     * @return 持久化实体
     */
    public PacsIntegrationLog record(
        String direction,
        String actionCode,
        String entityType,
        Long entityId,
        String outcome,
        String requestBody,
        String responseBody,
        String errorMessage,
        Long operatorId) {
        PacsIntegrationLog log = new PacsIntegrationLog();
        log.setDirectionEnum(direction);
        log.setActionCode(actionCode);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOutcomeEnum(outcome);
        log.setRequestBody(requestBody);
        log.setResponseBody(responseBody);
        log.setErrorMessage(errorMessage);
        log.setCreatedBy(operatorId);
        log.setUpdatedBy(operatorId);
        return repository.save(log);
    }

    /**
     * 查询可补发的失败外发日志（最近优先）。
     *
     * @param actionCode 动作码
     * @return 失败日志列表
     */
    public List<PacsIntegrationLog> listFailedOutbound(String actionCode) {
        return repository.findByOutcomeEnumAndActionCodeOrderByCreatedAtDesc(OUTCOME_FAILURE, actionCode);
    }
}
