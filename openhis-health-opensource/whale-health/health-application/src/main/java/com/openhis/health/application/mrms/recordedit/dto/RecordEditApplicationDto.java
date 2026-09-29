/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.recordedit.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 编辑申请统一出参（文书/首页共用展示形状）。
 */
@Getter
@Setter
public class RecordEditApplicationDto {

    /** 应用层路由键 document/homepage。 */
    private String targetKind;

    /** 申请主键。 */
    private Long applicationId;

    /** 文书主键（document）。 */
    private Long documentId;

    /** 病案主键（homepage）。 */
    private Long caseRecordId;

    /** 状态。 */
    private String statusEnum;

    /** 申请原因。 */
    private String applyReason;

    /** 驳回原因。 */
    private String rejectReason;

    /** from 版本。 */
    private Integer fromVersion;

    /** to 版本。 */
    private Integer toVersion;

    /** 旧 JSON（详情）。 */
    private String oldContentJson;

    /** 新 JSON（详情/工作稿）。 */
    private String newContentJson;

    /** 申请人。 */
    private Long applicantId;

    /** 申请时间。 */
    private LocalDateTime appliedAt;

    /** 交审时间。 */
    private LocalDateTime submittedAt;

    /** 审核人。 */
    private Long reviewerId;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;
}
