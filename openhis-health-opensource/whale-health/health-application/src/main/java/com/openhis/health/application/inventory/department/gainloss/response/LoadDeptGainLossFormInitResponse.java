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

package com.openhis.health.application.inventory.department.gainloss.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 科室盘点页初始化出参（IF-DEP-ST-01；仅科室位置列表）。
 */
@Data
@Accessors(chain = true)
/** 盘点页初始化 IF-DEP-ST-01。 */
@Schema(description = "盘点页初始化 IF-DEP-ST-01")
public class LoadDeptGainLossFormInitResponse {

    @Schema(description = "科室下拉 deptLocationOptions")
    private List<OptionDto> deptLocationOptions;

    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态选项。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 护士人员选项。 */
    @Schema(description = "护士下拉 nurseOptions")
    private List<OptionDto> nurseOptions;

    /** 库管人员选项。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;
}
