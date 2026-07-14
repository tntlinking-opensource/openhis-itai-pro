/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 分页查询汇总单头请求（IF-IP-SUM-02）。
 *
 * <p>分页与时段字段继承 {@link AbstractHealthPageRequest}（{@code pageNo}/{@code pageSize}/{@code keyword}/{@code startDate}/{@code endDate}）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页查询汇总单头 IF-IP-SUM-02", requiredProperties = {"pageNo", "pageSize"})
public class PageMedicationSummarySlipHeadersRequest extends AbstractHealthPageRequest {

    @Schema(description = "发放药房主键，0 或空表示可见全部药房")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 发放药房主键，0 或空表示可见全部药房。 */
    private Long purposeLocationId;
}
