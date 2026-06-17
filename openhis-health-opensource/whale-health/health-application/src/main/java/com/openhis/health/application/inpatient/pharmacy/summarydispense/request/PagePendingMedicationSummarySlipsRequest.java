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

package com.openhis.health.application.inpatient.pharmacy.summarydispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询待核发汇总单头请求（IF-IPS-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 分页查询待核发汇总单头 IF-IPS-01。 */
@Schema(description = "分页查询待核发汇总单头 IF-IPS-01", requiredProperties = {"pageNo", "pageSize"})
public class PagePendingMedicationSummarySlipsRequest extends AbstractHealthPageRequest {

    @Schema(description = "发放药房主键，0 或空表示可见全部可操作药房")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;

    /**
     * 供应审批态多选（持久化 code）；列表实际按首个合法值收窄为单态。
     * <p>待发药默认 {@code APPROVAL}；已核发传 {@code AGREE}（**BR-IPS-013**）。</p>
     */
    @Schema(description = "供应审批态多选：待发药 APPROVAL、已核发 AGREE；缺省 APPROVAL")
    private List<String> supplyStatusEnumIn;
}
