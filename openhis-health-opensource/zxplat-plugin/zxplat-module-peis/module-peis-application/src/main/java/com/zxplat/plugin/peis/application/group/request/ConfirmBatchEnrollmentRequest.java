/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import com.zxplat.plugin.peis.application.group.dto.RosterEnrollmentItemDto;
import lombok.Data;

import java.util.List;

/**
 * 团检批量选定建单请求（P0-1 · 须 rosterId + packageActivityId 成对）。
 */
@Data
public class ConfirmBatchEnrollmentRequest {

    /** 建单条目（名册 + 选定套餐）。 */
    private List<RosterEnrollmentItemDto> items;

    /** 兼容旧版：仅名册 id（已废弃，须带 items.packageActivityId）。 */
    private List<Long> rosterIds;

    /** 机构主键（可选，默认取分组/合同）。 */
    private Long orgId;
}
