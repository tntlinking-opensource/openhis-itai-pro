/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import com.openhis.chs.application.notice.dto.ApplyPreviewSampleDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用差异预览响应（IF-CHS-CAT-005-R-06）。
 */
@Data
public class PreviewNoticeApplyResponse {

    /** 预计 overlay upsert 次数。 */
    private int overlayUpsertCount;

    /** 预计院内 lv 回写次数。 */
    private int writebackCount;

    /** 预计未收费同步次数。 */
    private int unchargedSyncCount;

    /** 样例行。 */
    private List<ApplyPreviewSampleDto> samples = new ArrayList<>();
}
