/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import com.openhis.chs.application.notice.dto.NoticeScanImportResultDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确认扫描入库响应（IF-CHS-CAT-005-W-02）。
 */
@Data
public class ConfirmNoticeScanImportResponse {

    /** 成功入库条数。 */
    private int importedCount;

    /** 跳过条数（非 NEW、未勾选等）。 */
    private int skippedCount;

    /** 失败条数。 */
    private int failedCount;

    /** 逐包结果。 */
    private List<NoticeScanImportResultDto> results = new ArrayList<>();
}
