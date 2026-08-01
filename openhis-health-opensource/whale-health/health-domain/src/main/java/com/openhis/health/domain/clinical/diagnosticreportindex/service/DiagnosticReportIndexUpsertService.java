/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreportindex.service;

import com.openhis.health.domain.clinical.diagnosticreportindex.command.UpsertDrIndexFromPeisCommand;
import com.openhis.health.domain.clinical.diagnosticreportindex.entity.DiagnosticReportIndex;
import com.openhis.health.domain.clinical.diagnosticreportindex.repository.DiagnosticReportIndexRepository;
import com.openhis.health.domain.share.constants.DiagnosticReportIndexConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 报告读侧索引写侧（DM-CLN-006；P0 仅 upsertFromPeis）。
 */
@Service
@RequiredArgsConstructor
public class DiagnosticReportIndexUpsertService {

    private final DiagnosticReportIndexRepository repository;

    /**
     * 总检完成后幂等写入 PEIS 索引行（MAS-04）。
     *
     * @param cmd PEIS upsert 命令
     * @return 索引实体
     */
    public DiagnosticReportIndex upsertFromPeis(UpsertDrIndexFromPeisCommand cmd) {
        // uniqueKey=PEIS:sourceReportId 幂等 upsert
        if (cmd == null || cmd.getSourceReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INDEX_INVALID_ARGUMENT, "源报告主键不能为空");
        }
        if (cmd.getEncounterId() == null || cmd.getPatientId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INDEX_INVALID_ARGUMENT, "就诊与患者不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INDEX_INVALID_ARGUMENT, "租户标识不能为空");
        }

        String uniqueKey = DiagnosticReportIndexConstants.UNIQUE_KEY_PREFIX_PEIS + cmd.getSourceReportId();
        Optional<DiagnosticReportIndex> existing = repository.findByUniqueKeyAndIsDeletedFalse(uniqueKey);
        DiagnosticReportIndex entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            entity = new DiagnosticReportIndex();
            entity.setTenantId(cmd.getTenantId());
            entity.setUniqueKey(uniqueKey);
            entity.setModuleEnum(DiagnosticReportIndexConstants.MODULE_PEIS);
            entity.setSourceReportId(cmd.getSourceReportId());
            if (entity.getIsDeleted() == null) {
                entity.setIsDeleted(Boolean.FALSE);
            }
        }

        entity.setOrgId(cmd.getOrgId());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setStatusEnum(DiagnosticReportIndexConstants.STATUS_FINAL);
        entity.setPublishedAt(LocalDateTime.now());
        if (StringUtils.isNotEmpty(cmd.getTitle())) {
            entity.setTitle(cmd.getTitle().trim());
        } else {
            entity.setTitle("体检总检报告");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        return repository.save(entity);
    }
}
