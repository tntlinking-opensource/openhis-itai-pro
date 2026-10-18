/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.support;

import com.zxplat.domain.administration.encounter.command.RegisterOutpatientEncounterCommand;
import com.zxplat.domain.administration.encounter.queries.OutpatientDuplicateCheckQuery;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientDuplicateCheckQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 本地补登记就诊解析：复用当日门诊就诊或开立新就诊。
 */
@Component
@RequiredArgsConstructor
public class PacsLocalRegistrationEncounterResolver {

    private final EncounterQueryService encounterQueryService;
    private final EncounterDomainService encounterDomainService;

    /**
     * 解析本地补登记使用的就诊主键。
     *
     * @param patientId 患者主键
     * @param organizationId 科室主键
     * @param encounterId 请求携带的就诊；非空则直接返回
     * @param serviceTypeId 开立门诊就诊时的号源类型
     * @param operatorId 操作人
     * @return 就诊主键
     */
    public Long resolveEncounterId(
        Long patientId,
        Long organizationId,
        Long encounterId,
        Long serviceTypeId,
        Long operatorId) {
        if (encounterId != null) {
            return encounterId;
        }
        if (serviceTypeId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT,
                "未指定就诊时号源类型不能为空");
        }
        OutpatientDuplicateCheckQuery duplicateQuery = new OutpatientDuplicateCheckQuery();
        duplicateQuery.setPatientId(patientId);
        duplicateQuery.setOrganizationId(organizationId);
        duplicateQuery.setRegistrationDate(LocalDate.now());
        OutpatientDuplicateCheckQueryModel duplicateResult =
            encounterQueryService.checkOutpatientDuplicateRegistration(duplicateQuery);
        if (Boolean.TRUE.equals(duplicateResult.getDuplicated())) {
            List<Long> existingIds = duplicateResult.getEncounterIds();
            if (existingIds == null || existingIds.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "当日同科室门诊就诊已存在但无法解析就诊标识");
            }
            return existingIds.get(0);
        }
        RegisterOutpatientEncounterCommand encounterCommand = new RegisterOutpatientEncounterCommand();
        encounterCommand.setOperatorId(operatorId);
        RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item =
            new RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand();
        item.setPatientId(patientId);
        item.setOrganizationId(organizationId);
        item.setServiceTypeId(serviceTypeId);
        item.setRegistrarId(operatorId);
        encounterCommand.setItems(List.of(item));
        return encounterDomainService.registerOutpatientEncounter(encounterCommand);
    }
}
