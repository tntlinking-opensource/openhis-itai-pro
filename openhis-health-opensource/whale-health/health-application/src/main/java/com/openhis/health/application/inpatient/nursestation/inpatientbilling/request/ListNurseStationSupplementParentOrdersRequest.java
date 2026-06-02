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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-03：医嘱补费父医嘱列表请求。
 *
 * <p>仅返回医生开立（doctor-prescription）的药/诊父医嘱；含停嘱、无执行槽位展开。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费父医嘱列表请求")
public class ListNurseStationSupplementParentOrdersRequest {

    /** 左侧多选就诊；服务端收窄为管理病区可见集合。 */
    @NotEmpty(message = "就诊列表不能为空")
    @Schema(description = "多选就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> encounterIds;

    /** 长临筛选：{@code all} 不过滤 / {@code long} 长期 / {@code temp} 临时。 */
    @Schema(description = "长临筛选：all/long/temp")
    private String therapyType;

    /** 医嘱摘要名称关键字（不区分大小写包含匹配）。 */
    @Schema(description = "医嘱名称关键字")
    private String keyword;

    /** 开立科室 id（科室维度在 OrderInfo 查询已下推时，此处仅配合关键字过滤）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室筛选")
    private Long orgId;
}
