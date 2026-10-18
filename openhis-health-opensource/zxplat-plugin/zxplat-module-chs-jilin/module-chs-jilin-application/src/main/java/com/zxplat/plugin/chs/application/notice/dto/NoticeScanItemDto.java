/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 扫描结果行：一个标准化子目录。
 */
@Data
public class NoticeScanItemDto {

    /** 相对 root 的子目录名。 */
    private String packDir;

    /** 局发原文件名（来自 meta）。 */
    private String sourceFilename;

    /** 文件种类。 */
    private String docKind;

    /** 品类。 */
    private String category;

    /** 标题。 */
    private String title;

    /** 通知日期。 */
    private LocalDate noticeDate;

    /** 默认生效日。 */
    private LocalDate effectiveDate;

    /** 明细行数。 */
    private Integer lineCount;

    /** ADD 行数。 */
    private int addCount;

    /** STOP 行数。 */
    private int stopCount;

    /** MODIFY 行数。 */
    private int modifyCount;

    /** SET_PAY_CLASS 行数。 */
    private int setPayClassCount;

    /** NEW / DUPLICATE / FAILED。 */
    private String status;

    /** 重复时已有 docId。 */
    private Long existingDocId;

    /** 失败/提示文案。 */
    private String message;

    /** SHA-256 hex（确定性 zip）。 */
    private String sourceFileHash;
}
