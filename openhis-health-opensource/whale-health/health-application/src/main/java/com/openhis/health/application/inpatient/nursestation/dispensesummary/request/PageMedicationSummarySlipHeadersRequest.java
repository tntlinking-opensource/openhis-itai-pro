/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
/** 分页查询汇总单头 IF-IP-SUM-02。 */
@Schema(description = "分页查询汇总单头 IF-IP-SUM-02", requiredProperties = {"pageNo", "pageSize"})
public class PageMedicationSummarySlipHeadersRequest extends AbstractHealthPageRequest {

    @Schema(description = "发放药房主键，0 或空表示可见全部药房")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;
}
