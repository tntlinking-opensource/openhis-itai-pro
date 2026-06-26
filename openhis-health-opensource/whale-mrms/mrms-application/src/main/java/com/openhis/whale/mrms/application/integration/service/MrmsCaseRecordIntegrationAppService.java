/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.integration.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.application.integration.request.CreateForEncounterRequest;
import com.openhis.whale.mrms.application.support.MrmsOperatorResolver;
import com.openhis.whale.mrms.domain.service.CaseRecordBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统集成 AppService → {@code POST /api/app/mrmsCaseRecordIntegration/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordIntegrationAppService {

    private final CaseRecordBootstrapService caseRecordBootstrapService;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 幂等为就诊创建病案主档与空首页。
     *
     * @param request 建档请求
     * @return 病案主档主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createForEncounter(CreateForEncounterRequest request) {
        // Gate：就诊与患者必填
        if (request.getEncounterId() == null || request.getPatientId() == null) {
            throw new ValidationException("患者与就诊主键不能为空");
        }
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            tenantId = operatorResolver.requireTenantId();
        }
        Long operatorId = request.getOperatorId();
        if (operatorId == null) {
            operatorId = operatorResolver.resolveOperatorIdOrNull();
        }
        // Cmd：幂等建档（新建时自动全量取值并刷新列表冗余）
        return caseRecordBootstrapService.createIfAbsent(
                tenantId, request.getPatientId(), request.getEncounterId(), operatorId);
    }
}
