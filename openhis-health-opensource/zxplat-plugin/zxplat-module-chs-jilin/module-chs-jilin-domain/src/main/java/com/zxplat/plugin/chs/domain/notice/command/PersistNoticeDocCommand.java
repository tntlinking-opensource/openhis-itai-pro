/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.command;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 确认入库写命令：持久化标准包 + 文件头 + 明细。
 * <p>由应用层 Preview 物化后提交；领域负责 hash 幂等与落库顺序。</p>
 */
@Data
public class PersistNoticeDocCommand {

    /** 租户主键。 */
    private Long tenantId;

    /** 契约主键；可空。 */
    private Long contractId;

    /** 文件种类。 */
    private String docKind;

    /** 品类。 */
    private String category;

    /** 展示标题。 */
    private String title;

    /** 局发原文件名。 */
    private String sourceFilename;

    /** 标准包内容 SHA-256。 */
    private String sourceFileHash;

    /** 标准包原始字节（用于 PackStore）。 */
    private byte[] packBytes;

    /** 存储时建议的文件名（含 .zip）。 */
    private String packFileName;

    /** 通知日期。 */
    private LocalDate noticeDate;

    /** 默认生效日。 */
    private LocalDate effectiveDate;

    /** 各 sheet 行数摘要 JSON。 */
    private String sheetSummaryJson;

    /** 解析状态（PARSED/PARTIAL）。 */
    private String parseStatus;

    /** 为 true 时软删旧同 hash 文档后生成新 doc。 */
    private boolean force;

    /** 导入人。 */
    private Long importedBy;

    /** 明细行。 */
    private List<PersistNoticeLineCommand> lines = new ArrayList<>();
}
