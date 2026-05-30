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
 * IF-EMG-10：待二次分诊队列。
 *
 * <p>分页字段继承 {@link AbstractPageQuery}（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "急诊分诊-待二次分诊队列请求")
public class ListPendingSecondaryTriageRequest extends AbstractPageQuery {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室/机构", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "SLA 分钟阈值（业务语义占位）")
    private Integer slaMinutes;

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
