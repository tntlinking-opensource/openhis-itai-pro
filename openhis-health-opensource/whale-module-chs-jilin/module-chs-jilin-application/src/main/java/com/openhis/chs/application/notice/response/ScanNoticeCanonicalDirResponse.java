/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import com.openhis.chs.application.notice.dto.NoticeScanItemDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描标准落地包目录响应（IF-CHS-CAT-005-W-01）。
 */
@Data
public class ScanNoticeCanonicalDirResponse {

    /** 二次确认入库用会话令牌（TTL 30min）。 */
    private String scanToken;

    /** 实际扫描根目录。 */
    private String scanRoot;

    /** 可新入库条数。 */
    private int newCount;

    /** 与库内 hash 重复条数。 */
    private int duplicateCount;

    /** 解析失败条数。 */
    private int failedCount;

    /** 扫描明细。 */
    private List<NoticeScanItemDto> items = new ArrayList<>();
}
