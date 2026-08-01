/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 通知文件头只读视图（QueryService 出参）。
 */
@Data
public class NoticeDocView {

    /** 文件头主键。 */
    private Long id;

    /** 契约主键。 */
    private Long contractId;

    /** 文件种类。 */
    private String docKind;

    /** 品类。 */
    private String category;

    /** 展示标题。 */
    private String title;

    /** 局发原文件名。 */
    private String sourceFilename;

    /** 内容 SHA-256。 */
    private String sourceFileHash;

    /** 标准包 URI。 */
    private String canonicalPackUri;

    /** 通知日期。 */
    private LocalDate noticeDate;

    /** 默认生效日。 */
    private LocalDate effectiveDate;

    /** 解析状态。 */
    private String parseStatus;

    /** 应用状态。 */
    private String applyStatus;

    /** 明细行数。 */
    private Integer lineCount;

    /** 导入时间。 */
    private LocalDateTime importedAt;
}
