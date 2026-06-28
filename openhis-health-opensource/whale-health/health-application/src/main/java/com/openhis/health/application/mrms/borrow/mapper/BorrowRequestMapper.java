/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.borrow.mapper;

import com.openhis.health.application.mrms.borrow.dto.BorrowRequestItemDto;
import com.openhis.health.domain.mrms.borrowrequest.entity.BorrowRequest;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import org.springframework.stereotype.Component;

/**
 * 借阅申请 DTO 映射（borrow IF）。
 */
@Component
public class BorrowRequestMapper {

    /**
     * 借阅申请实体转列表行 DTO，附带病案号与姓名展示。
     *
     * @param entity 借阅申请
     * @param record 关联病案主档，可为 null
     * @return 列表行 DTO
     */
    public BorrowRequestItemDto toItemDto(BorrowRequest entity, CaseRecord record) {
        BorrowRequestItemDto dto = new BorrowRequestItemDto();
        if (entity.getId() != null) {
            dto.setId(String.valueOf(entity.getId()));
        }
        if (entity.getCaseRecordId() != null) {
            dto.setCaseRecordId(String.valueOf(entity.getCaseRecordId()));
        }
        if (record != null) {
            // 列表行附带病案号/姓名，主档缺失时仅展示借阅字段
            dto.setMedcasno(record.getMedcasno());
            dto.setPsnName(record.getPsnName());
        }
        dto.setRequestTypeCode(entity.getRequestTypeCode());
        dto.setApplicantName(entity.getApplicantName());
        dto.setApplicantDeptName(entity.getApplicantDeptName());
        dto.setPurpose(entity.getPurpose());
        dto.setScopeDesc(entity.getScopeDesc());
        dto.setCopyCount(entity.getCopyCount());
        dto.setStatusCode(entity.getStatusCode());
        dto.setApproveComment(entity.getApproveComment());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setBorrowedAt(entity.getBorrowedAt());
        dto.setDueReturnAt(entity.getDueReturnAt());
        dto.setReturnedAt(entity.getReturnedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
