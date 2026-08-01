/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreport.service;

import com.openhis.health.domain.clinical.diagnosticreport.entity.DiagnosticReport;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.repository.DiagnosticReportRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 临床总检报告读侧（DM-CLN-008 §5.2）。
 */
@Service
@RequiredArgsConstructor
public class DiagnosticReportQueryService {

    private final DiagnosticReportRepository repository;

    /**
     * 按就诊读取主报告（可空）。
     *
     * @param encounterId 就诊主键
     * @return 读模型
     */
    public Optional<DiagnosticReportModel> getByEncounterId(Long encounterId) {
        // 软删过滤 → 仓储查询 → 组装 Model
        if (encounterId == null) {
            return Optional.empty();
        }
        return repository.findByEncounterIdAndIsDeletedFalse(encounterId).map(this::toModel);
    }

    /**
     * 按主键读取；不存在抛错。
     *
     * @param id 报告主键
     * @return 读模型
     */
    public DiagnosticReportModel mustGetById(Long id) {
        if (id == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "报告主键不能为空");
        }
        DiagnosticReport entity = repository.findById(id).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_NOT_FOUND, "总检报告不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_NOT_FOUND, "总检报告不存在");
        }
        return toModel(entity);
    }

    private DiagnosticReportModel toModel(DiagnosticReport entity) {
        DiagnosticReportModel model = new DiagnosticReportModel();
        model.setId(entity.getId());
        model.setEncounterId(entity.getEncounterId());
        model.setPatientId(entity.getPatientId());
        model.setStatusEnum(entity.getStatusEnum());
        model.setSummaryText(entity.getSummaryText());
        model.setConclusionText(entity.getConclusionText());
        model.setResultRefsJson(entity.getResultRefsJson());
        model.setPdfStorageKey(entity.getPdfStorageKey());
        model.setFinalizedAt(entity.getFinalizedAt());
        model.setFinalizedBy(entity.getFinalizedBy());
        return model;
    }
}
