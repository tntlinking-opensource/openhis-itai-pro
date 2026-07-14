/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.homepage.mapper;

import com.openhis.health.application.mrms.homepage.dto.CaseRecordDetailDto;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import org.springframework.stereotype.Component;

/**
 * 病案主档详情 DTO 映射（homepage IF 拥有，lookup/qc 复用）。
 */
@Component
public class CaseRecordDetailMapper {

    /**
     * 主档实体转详情 DTO。
     *
     * @param record 病案主档
     * @return 详情 DTO
     */
    public CaseRecordDetailDto toDetail(CaseRecord record) {
        CaseRecordDetailDto dto = new CaseRecordDetailDto();
        dto.setCaseRecordId(record.getId());
        dto.setPatientId(record.getPatientId());
        dto.setEncounterId(record.getEncounterId());
        dto.setMedcasno(record.getMedcasno());
        dto.setStatusCode(record.getStatusCode());
        dto.setPatnIptCnt(record.getPatnIptCnt());
        dto.setReturnReason(record.getReturnReason());
        dto.setArchivedAt(record.getArchivedAt());
        dto.setQualityGradeCode(record.getQualityGradeCode());
        CaseRecordStatus status = record.getStatusEnum();
        // 状态码可解析时填充展示名
        if (status != null) {
            dto.setStatusLabel(status.getDisplay());
        }
        return dto;
    }
}
