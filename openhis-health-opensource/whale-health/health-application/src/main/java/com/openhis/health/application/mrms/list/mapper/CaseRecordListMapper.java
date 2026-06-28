/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.list.mapper;

import com.openhis.health.application.mrms.list.dto.CaseRecordListItemDto;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.stereotype.Component;

/**
 * 病案列表行 DTO 映射（list IF {@code mapper}）。
 *
 * <p>支持 {@link CaseRecordListItemQueryModel}（分页读模型）与 {@link CaseRecord} 实体两种入参。</p>
 */
@Component
public class CaseRecordListMapper {

    /**
     * 列表 QueryModel 转 DTO。
     *
     * @param row 列表 QueryModel
     * @return 列表行 DTO
     */
    public CaseRecordListItemDto toListItem(CaseRecordListItemQueryModel row) {
        CaseRecordListItemDto dto = new CaseRecordListItemDto();
        dto.setCaseRecordId(row.getId());
        dto.setMedcasno(row.getMedcasno());
        dto.setPsnName(row.getPsnName());
        dto.setIptNo(row.getIptNo());
        dto.setGendCode(row.getGendCode());
        dto.setAdmTime(row.getAdmTime());
        dto.setDscgTime(row.getDscgTime());
        dto.setAdmCatyName(row.getAdmCatyName());
        dto.setDscgCatyName(row.getDscgCatyName());
        dto.setChfdrName(row.getChfdrName());
        dto.setMainDiagName(row.getMainDiagName());
        dto.setStatusCode(row.getStatusCode());
        CaseRecordStatus status = EnumUtil.fromCode(CaseRecordStatus.class, row.getStatusCode());
        // 状态码可解析时填充展示名
        if (status != null) {
            dto.setStatusLabel(status.getDisplay());
        }
        return dto;
    }

    /**
     * 主档实体转列表行 DTO。
     *
     * @param record 病案主档
     * @return 列表行 DTO
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
        // 状态码可解析时填充展示名
        if (status != null) {
            dto.setStatusLabel(status.getDisplay());
        }
        return dto;
    }
}
