/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.integration.mrms.command.DischargeCaseRecordBootstrapCommand;
import com.openhis.whale.mrms.contracts.writer.CaseRecordBootstrapWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link MrmsCaseRecordBootstrapExtension} 实现（委托 {@link CaseRecordBootstrapWriter}）。 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(CaseRecordBootstrapWriter.class)
@ConditionalOnProperty(name = "openhis.modules.mrms.enabled", havingValue = "true")
public class MrmsCaseRecordBootstrapExtensionImpl implements MrmsCaseRecordBootstrapExtension {

    private final CaseRecordBootstrapWriter caseRecordBootstrapWriter;
    private final EncounterQueryService encounterQueryService;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onDischargeRecorded(DischargeCaseRecordBootstrapCommand command) {
        if (command == null || command.getEncounterId() == null) {
            return;
        }
        Encounter encounter = encounterQueryService
                .findOptionalEncounterEntityById(command.getEncounterId())
                .orElse(null);
        if (encounter == null || encounter.getPatientId() == null) {
            log.warn("离院建档跳过：就诊 {} 不存在或无患者", command.getEncounterId());
            return;
        }
        Long tenantId = encounter.getTenantId();
        if (tenantId == null) {
            log.warn("离院建档跳过：就诊 {} 无租户", command.getEncounterId());
            return;
        }
        Long caseRecordId = caseRecordBootstrapWriter.createIfAbsent(
                tenantId,
                encounter.getPatientId(),
                encounter.getId(),
                command.getOperatorId());
        log.info("离院建档完成 encounterId={} caseRecordId={}", command.getEncounterId(), caseRecordId);
    }
}
