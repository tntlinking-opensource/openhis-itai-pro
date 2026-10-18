/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量放号响应（2.8）。
 */
@Getter
@Setter
public class ProvisionSlotBatchResponse {

    /** 新建号源主键列表。 */
    private List<Long> slotIds;

    /** 生成数量。 */
    private Integer generatedCount;
}
