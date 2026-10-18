/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用差异预览视图（不写库估算）。
 */
@Data
public class ApplyPreviewView {

    /** 预计 overlay upsert 次数。 */
    private int overlayUpsertCount;

    /** 预计落入待映射（仅物价码、尚未对上国家码）的行数。 */
    private int pendingMapCount;

    /** 预计跳过行数（无主键、脏码、未知动作）。 */
    private int skippedCount;

    /** 预计院内 lv 回写次数。 */
    private int writebackCount;

    /** 预计未收费同步次数。 */
    private int unchargedSyncCount;

    /** 样例行。 */
    private List<ApplyPreviewSampleView> samples = new ArrayList<>();
}
