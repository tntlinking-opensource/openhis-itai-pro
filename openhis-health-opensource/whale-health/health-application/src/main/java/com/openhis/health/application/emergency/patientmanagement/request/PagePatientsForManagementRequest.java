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

package com.openhis.health.application.emergency.patientmanagement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 患者管理工作站：分页列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "患者管理-分页列表请求")
public class PagePatientsForManagementRequest extends AbstractPageQuery {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "去向区域 locationId；空表示全部")
    private Long locationId;

    @Schema(description = "距挂号时间：ALL/WITHIN_60/WITHIN_120/OVER_30/OVER_60")
    private String registrationTimeFilter;

    @Schema(description = "是否精准搜索（患者ID/姓名/床号）")
    private Boolean exactSearch;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者ID（精准）")
    private Long patientId;

    @Schema(description = "患者姓名（精准，忽略大小写）")
    private String patientName;

    @Schema(description = "床号（精准，匹配位置名称或编码）")
    private String bedLabel;

    @Override
    @Schema(description = "模糊关键字（非精准时）；使用基类 keyword")
    public String getKeyword() {
        return super.getKeyword();
    }

    @Override
    @NotNull(message = "页码不能为空")
    public Integer getPageNo() {
        return super.getPageNo();
    }

    @Override
    @NotNull(message = "每页条数不能为空")
    public Integer getPageSize() {
        return super.getPageSize();
    }
}
