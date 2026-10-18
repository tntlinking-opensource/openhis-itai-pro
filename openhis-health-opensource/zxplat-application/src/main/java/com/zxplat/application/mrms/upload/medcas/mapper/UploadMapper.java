/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.mapper;

import com.zxplat.application.mrms.upload.medcas.dto.CaseRecordUploadSummaryDto;
import com.zxplat.application.mrms.upload.medcas.dto.UploadLogDto;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.upload.entity.UploadLog;
import org.springframework.stereotype.Component;

/**
 * 4401 上传日志 DTO 映射（upload IF）。
 */
@Component
public class UploadMapper {

    /**
     * 上传日志实体转 DTO。
     *
     * @param log 上传日志
     * @return 日志 DTO
     */
    public UploadLogDto toLogDto(UploadLog log) {
        UploadLogDto dto = new UploadLogDto();
        // Long 主键序列化为字符串，避免前端精度丢失
        if (log.getId() != null) {
            dto.setId(String.valueOf(log.getId()));
        }
        if (log.getCaseRecordId() != null) {
            dto.setCaseRecordId(String.valueOf(log.getCaseRecordId()));
        }
        dto.setContractBusNo(log.getContractBusNo());
        dto.setInfno(log.getInfno());
        dto.setStatusCode(log.getStatusCode());
        dto.setInfcode(log.getInfcode());
        dto.setErrMsg(log.getErrMsg());
        dto.setRequestDigest(log.getRequestDigest());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    /**
     * 病案主档转上传待传/已传列表行 DTO。
     *
     * @param row 病案主档（含 chs_upload 冗余列）
     * @return 上传摘要 DTO
     */
    public CaseRecordUploadSummaryDto toUploadSummaryDto(CaseRecord row) {
        CaseRecordUploadSummaryDto dto = new CaseRecordUploadSummaryDto();
        if (row.getId() != null) {
            dto.setCaseRecordId(String.valueOf(row.getId()));
        }
        dto.setMedcasno(row.getMedcasno());
        dto.setPsnName(row.getPsnName());
        dto.setDscgTime(row.getDscgTime());
        dto.setArchivedAt(row.getArchivedAt());
        dto.setChsUploadStatusCode(row.getChsUploadStatusCode());
        dto.setChsUploadError(row.getChsUploadError());
        dto.setSettleUploadStatusCode(row.getSettleUploadStatusCode());
        dto.setSettleUploadError(row.getSettleUploadError());
        dto.setSetlListId(row.getSetlListId());
        return dto;
    }
}
