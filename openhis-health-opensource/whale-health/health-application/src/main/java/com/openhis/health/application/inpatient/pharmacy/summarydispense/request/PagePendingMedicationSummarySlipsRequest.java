/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 分页查询待核发汇总单头请求（IF-IPS-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页查询待核发汇总单头 IF-IPS-01", requiredProperties = {"pageNo", "pageSize"})
public class PagePendingMedicationSummarySlipsRequest extends AbstractHealthPageRequest {

    /** 发放药房主键；0 或空表示当前用户可见全部可操作药房。 */
    @Schema(description = "发放药房主键，0 或空表示可见全部可操作药房")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;

    /**
     * 是否仅查询发放表已终态（{@code COMPLETED}）的汇总单（IF-IPS-01 已发药 Tab）。
     * <p>{@code false}/空=待发核对（发放行 {@code SUMMARIZED}）；列表仅以发放表门控，不接受 Supply 审批态入参。</p>
     */
    @Schema(description = "true=仅已发药（发放表 COMPLETED）；false/空=待发核对（SUMMARIZED）")
    private Boolean summaryDispenseCompletedOnly;
}
