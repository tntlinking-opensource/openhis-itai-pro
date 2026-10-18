/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

/**
 * 确认扫描入库单条结果。
 */
@Data
public class NoticeScanImportResultDto {

    /** 相对 root 的子目录名。 */
    private String packDir;

    /** 入库成功时的文件头主键。 */
    private Long docId;

    /** IMPORTED / SKIPPED / FAILED。 */
    private String status;

    /** 说明。 */
    private String message;
}
