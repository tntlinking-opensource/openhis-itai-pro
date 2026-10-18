/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量放号请求（2.8）。
 */
@Getter
@Setter
public class ProvisionSlotBatchRequest {

    /** 日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 机构主键（冗余写入 Slot）。 */
    @NotNull
    private Long organizationId;

    /** 放号策略主键（无模板时按策略生成）。 */
    private Long releasePolicyId;

    /** 时段模板列表（可选；空时读 releasePolicyId 对应策略）。 */
    @Valid
    private List<SlotTemplateRequest> slotTemplates;
}
