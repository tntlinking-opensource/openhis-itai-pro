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

package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

/**
 * 主路径 SUMMARIZED 发放触发的汇总重建上下文片段（应用层内部结构）。
 */
@Data
@Accessors(chain = true)
/** 汇总重建提示。 */
@Schema(description = "汇总重建提示（应用层内部）")
public class SummaryRebuildHint {

    @Schema(description = "原汇总单号")
    private String summaryNo;

    @Schema(description = "需剔除的发放行 id 集合")
    private Set<Long> excludedDispenseIds = new HashSet<>();
}
