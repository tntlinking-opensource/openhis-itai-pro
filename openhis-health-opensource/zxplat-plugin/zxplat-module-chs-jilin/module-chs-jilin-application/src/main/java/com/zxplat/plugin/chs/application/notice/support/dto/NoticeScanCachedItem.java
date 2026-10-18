/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import lombok.Data;

/**
 * 扫描会话内单个标准包子目录缓存（含入库所需预览上下文）。
 */
@Data
public class NoticeScanCachedItem {

    /** 相对 root 的子目录名。 */
    private String packDir;

    /** NEW / DUPLICATE / FAILED。 */
    private String status;

    /** 重复时已有文件头主键。 */
    private Long existingDocId;

    /** 失败或提示文案。 */
    private String message;

    /** NEW/DUPLICATE 时物化的入库上下文；FAILED 为空。 */
    private NoticePreviewContext preview;
}
