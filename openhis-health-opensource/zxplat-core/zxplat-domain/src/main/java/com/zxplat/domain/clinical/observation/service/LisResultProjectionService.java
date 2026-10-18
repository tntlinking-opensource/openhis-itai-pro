/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.service;

import com.zxplat.domain.clinical.observation.command.ProjectFromPacsSummaryCommand;
import com.zxplat.domain.clinical.observation.command.RevokeFromPacsSummaryCommand;
import com.zxplat.domain.clinical.observation.entity.Observation;
import com.zxplat.domain.clinical.observation.repository.ObservationRepository;
import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.zxplat.domain.clinical.observationdefinition.repository.ObservationDefinitionRepository;
import com.zxplat.domain.custom.activityobservationlink.queries.model.ActivityObservationLinkModel;
import com.zxplat.domain.custom.activityobservationlink.service.ActivityObservationLinkQueryService;
import com.zxplat.domain.share.constants.DiagnosticReportIndexConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ObservationStatusConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LIS/PACS → CLN Observation 投影服务（DM-CLN-007 §6.3）。
 *
 * <p>P0：先落地 {@link #projectFromPacsSummary}；{@code projectFromLis} 另随 LIS AUD 关闭。</p>
 */
@Service
@RequiredArgsConstructor
public class LisResultProjectionService {

    private final ObservationRepository observationRepository;
    private final ObservationDefinitionRepository observationDefinitionRepository;
    private final ActivityObservationLinkQueryService activityObservationLinkQueryService;

    /**
     * 将 PACS 报告结论投影为 CLN Observation 摘要行。
     *
     * <p>无 AOL 绑定时跳过（不抛错），保证 REP-07 主路径可完成；有 AOL 时按首个小项幂等 upsert。</p>
     *
     * @param cmd 投影命令
     * @return 写入的观察实例；跳过时 empty
     */
    public Optional<Observation> projectFromPacsSummary(ProjectFromPacsSummaryCommand cmd) {
        if (cmd == null || cmd.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "serviceRequestId 不能为空");
        }
        if (cmd.getEncounterId() == null) {
            // 无就诊不投影（对称 BR：仅有 encounter 时必须调）
            return Optional.empty();
        }
        if (cmd.getActivityId() == null) {
            return Optional.empty();
        }
        List<ActivityObservationLinkModel> links =
            activityObservationLinkQueryService.listByActivityId(cmd.getActivityId());
        if (links == null || links.isEmpty()) {
            return Optional.empty();
        }
        Long definitionId = links.get(0).getObservationDefinitionId();
        if (definitionId == null) {
            return Optional.empty();
        }
        ObservationDefinition definition = observationDefinitionRepository
            .findByIdAndIsDeletedFalse(definitionId)
            .orElse(null);
        if (definition == null) {
            return Optional.empty();
        }

        String summary = null;
        if (StringUtils.isNotEmpty(cmd.getConclusionText())) {
            summary = cmd.getConclusionText().trim();
        } else if (cmd.getFindingsText() != null) {
            summary = cmd.getFindingsText().trim();
        }
        if (StringUtils.isEmpty(summary)) {
            summary = "检查报告已发布";
        }

        Optional<Observation> existing = observationRepository
            .findByServiceRequestIdAndDefinitionIdAndIsDeletedFalse(cmd.getServiceRequestId(), definitionId);
        Observation entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            entity = new Observation();
            entity.setTenantId(cmd.getTenantId());
            entity.setOrgId(cmd.getOrgId());
            entity.setEncounterId(cmd.getEncounterId());
            entity.setServiceRequestId(cmd.getServiceRequestId());
            entity.setDefinitionId(definitionId);
            entity.setCode(definition.getCode());
            if (entity.getIsDeleted() == null) {
                entity.setIsDeleted(Boolean.FALSE);
            }
        }
        entity.applyValue(null, summary, null, null);
        entity.setSourceModuleEnum(DiagnosticReportIndexConstants.MODULE_PACS);
        entity.setSourceObservationId(cmd.getPacsReportId());
        entity.setStatusEnum(ObservationStatusConstants.STATUS_FINAL);
        entity.setPerformerId(cmd.getOperatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(cmd.getOperatorId());
        return Optional.of(observationRepository.save(entity));
    }

    /**
     * 取消审核：将该 PACS 报告投影出的 Observation 标为 cancelled（不软删，便于再投影 upsert）。
     *
     * @param cmd 吊销命令
     * @return 受影响行数
     */
    public int revokeFromPacsSummary(RevokeFromPacsSummaryCommand cmd) {
        if (cmd == null || cmd.getPacsReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "pacsReportId 不能为空");
        }
        List<Observation> rows = observationRepository
            .findBySourceModuleEnumAndSourceObservationIdAndIsDeletedFalse(
                DiagnosticReportIndexConstants.MODULE_PACS, cmd.getPacsReportId());
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Observation row : rows) {
            if (row == null) {
                continue;
            }
            // 已 cancelled 则跳过（幂等）
            if (ObservationStatusConstants.STATUS_CANCELLED.equals(row.getStatusEnum())) {
                continue;
            }
            row.setStatusEnum(ObservationStatusConstants.STATUS_CANCELLED);
            row.setUpdatedAt(LocalDateTime.now());
            if (cmd.getOperatorId() != null) {
                row.setUpdatedBy(cmd.getOperatorId());
            }
            observationRepository.save(row);
            count++;
        }
        return count;
    }
}
