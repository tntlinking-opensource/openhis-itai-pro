/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.mrms.domain.command.RefreshAutoFillCommand;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 病案建档编排：主档 + 首页 + 离院后首次自动取值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseRecordBootstrapService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;

    /**
     * 幂等创建病案；新建时自动全量 HIS 取值并刷新列表冗余。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param operatorId 操作人
     * @return 病案主档主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) {
        boolean isNew = caseRecordDomainService.findByEncounterId(tenantId, encounterId).isEmpty();
        CaseRecord record = caseRecordDomainService.createIfAbsent(tenantId, patientId, encounterId, operatorId);
        caseRecordHomepageDomainService.ensureHomepage(record.getId(), record.getTenantId(), operatorId);
        if (isNew) {
            bootstrapAutoFill(record, operatorId);
        }
        return record.getId();
    }

    private void bootstrapAutoFill(CaseRecord record, Long operatorId) {
        try {
            RefreshAutoFillCommand command = new RefreshAutoFillCommand();
            command.setCaseRecordId(record.getId());
            command.setSections(List.of("all"));
            command.setForceOverwrite(false);
            command.setOperatorId(operatorId);
            caseRecordDomainService.refreshAutoFill(command);
        } catch (Exception ex) {
            log.warn("离院建档自动取值失败 caseRecordId={} encounterId={}",
                    record.getId(), record.getEncounterId(), ex);
        }
    }
}
