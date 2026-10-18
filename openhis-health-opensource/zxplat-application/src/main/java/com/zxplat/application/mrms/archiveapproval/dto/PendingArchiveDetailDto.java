/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.archiveapproval.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 待归档审批详情。
 */
@Getter
@Setter
public class PendingArchiveDetailDto {

    /** 业务类别。 */
    private String bizCategory;

    /** 业务主键。 */
    private Long bizId;

    /** 标题。 */
    private String title;

    /** 状态。 */
    private String statusCode;

    /** 版本。 */
    private Integer versionNo;

    /** 文书正文 JSON（文书类别）。 */
    private String documentContent;

    /** 首页 JSON 预览（首页类别；只读，本次提交）。 */
    private String homepagePreviewJson;

    /** 最近一次已通过编辑申请的首页 JSON（再次提交时的历史对照；没有申请记录则为空）。 */
    private String homepageBaselineJson;

    /** 驳回原因（若有）。 */
    private String rejectReason;
}
