/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 扫描标准落地包目录请求（IF-CHS-CAT-005-W-01）。
 */
@Data
public class ScanNoticeCanonicalDirRequest {

    /** 契约主键；可选。 */
    private Long contractId;

    /** 扫描根目录；空则使用配置 {@code chs.notice.canonical-root}。 */
    private String scanRoot;
}
