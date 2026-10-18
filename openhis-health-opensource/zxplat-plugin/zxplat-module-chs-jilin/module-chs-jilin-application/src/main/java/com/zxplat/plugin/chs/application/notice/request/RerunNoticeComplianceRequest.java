/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 再次合规校验请求（IF-CHS-CAT-005-W-05）。
 */
@Data
public class RerunNoticeComplianceRequest {
    /** 契约主键过滤；空表示扫描全部契约 overlay。 */
    private Long contractId;
    /** 目录类别过滤；空表示不限。 */
    private String listType;
    /** 操作者用户主键（写入报告 ran_by）；可空。 */
    private Long operatorId;
}
