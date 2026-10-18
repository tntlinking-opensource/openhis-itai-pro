/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入预览缓存上下文：确认入库前持有物化命令所需的全部字段。
 */
@Data
public class NoticePreviewContext {

    /** 预览令牌。 */
    private String previewToken;

    /** 过期时刻。 */
    private Instant expireAt;

    /** 标准包原始字节。 */
    private byte[] packBytes;

    /** 包文件名。 */
    private String packFileName;

    /** SHA-256 hex。 */
    private String sourceFileHash;

    /** 文件种类。 */
    private String docKind;

    /** 品类。 */
    private String category;

    /** 标题。 */
    private String title;

    /** 局发原文件名。 */
    private String sourceFilename;

    /** 通知日期。 */
    private LocalDate noticeDate;

    /** 默认生效日。 */
    private LocalDate effectiveDate;

    /** 解析状态。 */
    private String parseStatus;

    /** sheet 行数摘要 JSON。 */
    private String sheetSummaryJson;

    /** 警告列表。 */
    private List<String> warnings = new ArrayList<>();

    /** ADD 行数。 */
    private int addCount;

    /** STOP 行数。 */
    private int stopCount;

    /** MODIFY 行数。 */
    private int modifyCount;

    /** SET_PAY_CLASS 行数。 */
    private int setPayClassCount;

    /** 明细总行数（扫描轻量缓存时可在释放 lines 后仍展示）。 */
    private int lineCount;

    /** 全部明细（入库用）。 */
    private List<PersistNoticeLineCommand> lines = new ArrayList<>();

    /** 样例行（预览展示，最多 N 条）。 */
    private List<PersistNoticeLineCommand> sampleLines = new ArrayList<>();
}
