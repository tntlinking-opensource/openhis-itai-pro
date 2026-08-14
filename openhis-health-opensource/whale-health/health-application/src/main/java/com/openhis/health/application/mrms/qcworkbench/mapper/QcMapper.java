/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qcworkbench.mapper;

import com.openhis.health.application.mrms.caserecord.homepage.mapper.CaseRecordDetailMapper;
import com.openhis.health.application.mrms.qcworkbench.dto.QcDefectDto;
import com.openhis.health.application.mrms.qcworkbench.dto.QcRunResultDto;
import com.openhis.health.application.mrms.qcworkbench.dto.QcRunSummaryDto;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.qc.entity.QcDefect;
import com.openhis.health.domain.mrms.qc.entity.QcRun;
import com.openhis.health.domain.mrms.qc.model.QcRunResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 质控领域实体与 REST DTO 映射（qc IF）。
 */
@Component
public class QcMapper {

    /**
     * 单条缺陷实体转 DTO。
     *
     * @param defect 质控缺陷实体
     * @return 缺陷 DTO
     */
    public QcDefectDto toDefectDto(QcDefect defect) {
        QcDefectDto dto = new QcDefectDto();
        dto.setDefectId(defect.getId());
        dto.setCaseRecordId(defect.getCaseRecordId());
        dto.setRuleCode(defect.getRuleCode());
        dto.setDefectMessage(defect.getDefectMessage());
        dto.setSeverityCode(defect.getSeverityCode());
        dto.setStatusCode(defect.getStatusCode());
        dto.setResponsiblePractitionerId(defect.getResponsiblePractitionerId());
        dto.setRectifiedAt(defect.getRectifiedAt());
        dto.setClosedAt(defect.getClosedAt());
        dto.setCreatedAt(defect.getCreatedAt());
        return dto;
    }

    /**
     * 缺陷列表转 DTO 列表。
     *
     * @param defects 缺陷实体列表，可为 null
     * @return DTO 列表，不为 null
     */
    public List<QcDefectDto> toDefectDtos(List<QcDefect> defects) {
        List<QcDefectDto> items = new ArrayList<>();
        if (defects == null) {
            return items;
        }
        for (QcDefect defect : defects) {
            items.add(toDefectDto(defect));
        }
        return items;
    }

    /**
     * 质控批次实体转摘要 DTO。
     *
     * @param run 质控执行批次
     * @return 批次摘要 DTO
     */
    public QcRunSummaryDto toRunSummaryDto(QcRun run) {
        QcRunSummaryDto dto = new QcRunSummaryDto();
        dto.setQcRunId(run.getId());
        dto.setTriggerCode(run.getTriggerCode());
        dto.setPassed(run.getPassed());
        dto.setDefectOpenCount(run.getDefectOpenCount());
        dto.setDefectWarnCount(run.getDefectWarnCount());
        dto.setFinishedAt(run.getFinishedAt());
        return dto;
    }

    /**
     * 批次列表转摘要 DTO 列表。
     *
     * @param runs 批次实体列表，可为 null
     * @return 摘要 DTO 列表
     */
    public List<QcRunSummaryDto> toRunSummaryDtos(List<QcRun> runs) {
        List<QcRunSummaryDto> items = new ArrayList<>();
        if (runs == null) {
            return items;
        }
        for (QcRun run : runs) {
            items.add(toRunSummaryDto(run));
        }
        return items;
    }

    /**
     * 质控执行结果转详情 DTO，可选附带主档详情。
     *
     * @param result                  领域执行结果
     * @param record                  病案主档，可为 null
     * @param caseRecordDetailMapper  主档详情映射器，可为 null
     * @return 质控结果 DTO
     */
    public QcRunResultDto toRunResultDto(
            QcRunResult result,
            CaseRecord record,
            CaseRecordDetailMapper caseRecordDetailMapper) {
        QcRunResultDto dto = new QcRunResultDto();
        dto.setQcRunId(result.getQcRunId());
        dto.setPassed(result.isPassed());
        dto.setOpenBlockCount(result.getOpenBlockCount());
        dto.setOpenWarnCount(result.getOpenWarnCount());
        dto.setDefects(toDefectDtos(result.getDefects()));
        // 主档与 mapper 均可用时回填 caseRecord 摘要
        if (record != null && caseRecordDetailMapper != null) {
            dto.setCaseRecord(caseRecordDetailMapper.toDetail(record));
        }
        return dto;
    }
}
