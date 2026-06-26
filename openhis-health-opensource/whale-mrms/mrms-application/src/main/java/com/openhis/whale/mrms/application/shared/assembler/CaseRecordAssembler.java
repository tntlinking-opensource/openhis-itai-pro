/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.shared.assembler;

import com.openhis.whale.mrms.application.homepage.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.list.dto.CaseRecordListItemDto;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import org.springframework.stereotype.Component;

/**
 * 病案主档实体与 DTO 装配器。
 */
@Component
public class CaseRecordAssembler {

    /**
     * 主档转列表行 DTO。
     *
     * @param record 主档实体
     * @return 列表行
     */
    public CaseRecordListItemDto toListItem(CaseRecord record) {
        CaseRecordListItemDto dto = new CaseRecordListItemDto();
        dto.setCaseRecordId(record.getId());
        dto.setMedcasno(record.getMedcasno());
        dto.setPsnName(record.getPsnName());
        dto.setIptNo(record.getIptNo());
        dto.setGendCode(record.getGendCode());
        dto.setAdmTime(record.getAdmTime());
        dto.setDscgTime(record.getDscgTime());
        dto.setAdmCatyName(record.getAdmCatyName());
        dto.setDscgCatyName(record.getDscgCatyName());
        dto.setChfdrName(record.getChfdrName());
        dto.setMainDiagName(record.getMainDiagName());
        dto.setStatusCode(record.getStatusCode());
        CaseRecordStatus status = record.getStatusEnum();
        if (status != null) {
            dto.setStatusLabel(status.getLabel());
        }
        return dto;
    }

    /**
     * 主档转详情 DTO。
     *
     * @param record 主档实体
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
        if (status != null) {
            dto.setStatusLabel(status.getLabel());
        }
        return dto;
    }
}
