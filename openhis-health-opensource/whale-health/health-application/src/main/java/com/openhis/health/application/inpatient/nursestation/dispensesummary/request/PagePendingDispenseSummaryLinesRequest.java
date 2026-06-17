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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分页查询待汇总明细请求（IF-IP-SUM-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 分页查询待汇总明细 IF-IP-SUM-01。 */
@Schema(description = "分页查询待汇总明细 IF-IP-SUM-01", requiredProperties = {
    "encounterIds", "medicineCategoryTab", "therapyFilter", "pageNo", "pageSize"
})
public class PagePendingDispenseSummaryLinesRequest extends AbstractHealthPageRequest {

    /** 左侧多选就诊（BR-IP-SUM-001）。 */
    @NotEmpty(message = "请选择至少一名患者")
    /** 左侧多选就诊主键。 */
    @Schema(description = "左侧多选就诊主键", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 中西药 Tab：west / tcm。 */
    @NotNull(message = "药品分类 Tab 不能为空")
    /** 中西药 Tab：west / tcm。 */
    @Schema(description = "中西药 Tab：west / tcm", required = true)
    private String medicineCategoryTab;

    /** 长临筛选：all / long / temp。 */
    @NotNull(message = "长临筛选不能为空")
    /** 长临筛选：all / long / temp。 */
    @Schema(description = "长临筛选：all / long / temp", required = true)
    private String therapyFilter;

    /** 计划发药截止（含）；空则不过滤。 */
    @Schema(description = "计划发药截止时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime queryDeadline;
}
