/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.borrow.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 借阅申请列表项 DTO。
 */
@Getter
@Setter
public class BorrowRequestItemDto {

    /** 借阅申请主键（字符串序列化）。 */
    private String id;

    /** 病案主档主键（字符串序列化）。 */
    private String caseRecordId;

    /** 院内病案号。 */
    private String medcasno;

    /** 患者姓名。 */
    private String psnName;

    /** 借阅类型码（查阅/复印等）。 */
    private String requestTypeCode;

    /** 申请人姓名。 */
    private String applicantName;

    /** 申请人科室名称。 */
    private String applicantDeptName;

    /** 借阅目的说明。 */
    private String purpose;

    /** 借阅范围描述。 */
    private String scopeDesc;

    /** 复印份数。 */
    private Integer copyCount;

    /** 借阅状态码。 */
    private String statusCode;

    /** 审批意见。 */
    private String approveComment;

    /** 审批通过时间。 */
    private LocalDateTime approvedAt;

    /** 实际借出时间。 */
    private LocalDateTime borrowedAt;

    /** 应归还时间。 */
    private LocalDateTime dueReturnAt;

    /** 实际归还时间。 */
    private LocalDateTime returnedAt;

    /** 申请创建时间。 */
    private LocalDateTime createdAt;
}
