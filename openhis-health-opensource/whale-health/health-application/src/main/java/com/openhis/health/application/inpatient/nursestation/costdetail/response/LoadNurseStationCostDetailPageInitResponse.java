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

package com.openhis.health.application.inpatient.nursestation.costdetail.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailPresentationOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/**
 * IF-IP-CDT-01：费用明细页初始化响应。
 */
@Data
@Accessors(chain = true)
/** 住院护士站费用明细页初始化响应。 */
@Schema(description = "住院护士站费用明细页初始化响应")
public class LoadNurseStationCostDetailPageInitResponse {

    /** 默认查询开始日期（当日）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "默认业务日期下界")
    private LocalDate dateFrom;

    /** 默认查询结束日期（当日）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 默认业务日期上界。 */
    @Schema(description = "默认业务日期上界")
    private LocalDate dateTo;

    /** 可选展示模式：明细 / 汇总。 */
    @Schema(description = "展示模式选项")
    private List<NurseStationCostDetailPresentationOptionDto> presentationOptions;

    /** 费用大类块顺序：MEDICATION → SERVICE → DEVICE。 */
    @Schema(description = "费用大类块顺序 code 列表")
    private List<String> expenseBlockOrder;
}
