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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-EMG-18：工作台列表分页。
 *
 * <p>分页字段继承 {@link AbstractPageQuery}（B.10.11：对外 {@code pageNo} 为 1-based；{@link AbstractPageQuery#springPageIndex()} 供内部转 0-based）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "急诊分诊-工作台分页请求")
public class PageEmgTriageWorkbenchRequest extends AbstractPageQuery {

    /** 可选；不传则不按机构过滤工作台列表（租户内全量 ACTIVE 分诊）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室/机构（可选）")
    private Long organizationId;

    @Schema(description = "姓名提示（占位，联接尚未落地）")
    private String patientNameHint;

    @Schema(description = "分级过滤")
    private Integer triageLevelEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识")
    private Long encounterId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识")
    private Long patientId;

    @Schema(
            description =
                    "为 true 且 patientId 非空时：不按 ACTIVE 过滤，返回该患者下全部状态分诊（含作废 VOIDED、已取代 SUPERSEDED）。"
                            + "默认 false，保持工作台仅有效分诊。")
    private Boolean includeAllTriageStatuses;

    @Override
    @NotNull(message = "页码不能为空")
    @Schema(description = "页码（从 1 开始）", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageNo() {
        return super.getPageNo();
    }

    @Override
    @NotNull(message = "每页条数不能为空")
    @Schema(description = "每页条数", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageSize() {
        return super.getPageSize();
    }
}
