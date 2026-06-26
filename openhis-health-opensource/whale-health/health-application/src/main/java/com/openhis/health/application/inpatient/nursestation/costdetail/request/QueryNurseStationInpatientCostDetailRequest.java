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

package com.openhis.health.application.inpatient.nursestation.costdetail.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-CDT-02：查询住院费用明细/汇总请求。
 */
@Data
@Accessors(chain = true)
/** 住院护士站费用明细查询请求。 */
@Schema(description = "住院护士站费用明细查询请求", requiredProperties = {
    "encounterId", "patientId", "dateFrom", "dateTo", "presentationMode"
})
public class QueryNurseStationInpatientCostDetailRequest {

    /** 当前选中就诊（BR-IP-CDT-014 单就诊）。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者 id，与就诊读模型交叉校验（BR-IP-CDT-001）。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 业务日期下界（闭区间，BR-IP-CDT-002/005）。 */
    @NotNull(message = "查询开始日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 业务日期下界。 */
    @Schema(description = "业务日期下界", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 业务日期上界（闭区间；须 dateFrom ≤ dateTo）。 */
    @NotNull(message = "查询结束日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 业务日期上界。 */
    @Schema(description = "业务日期上界", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 展示模式：DETAIL 明细 / SUMMARY 汇总。 */
    @NotNull(message = "展示模式不能为空")
    /** 展示模式：DETAIL 或 SUMMARY。 */
    @Schema(description = "展示模式：DETAIL 或 SUMMARY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;
}
