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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IF-IP-ARG-02：护理级别只读区（不可写入配置表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理级别只读信息")
public class NurseStationAutoRollNursingReadonlyDto {

    /** 源护理级别医嘱主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理医嘱主键")
    private Long serviceRequestId;

    /** 护理级别 activityId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理活动定义主键")
    private Long activityId;

    /** 护理级别展示名。 */
    @Schema(description = "护理级别名称")
    private String activityName;

    /** 医嘱数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 数量单位 code。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位名称")
    private String unitName;

    /** 护士校对时刻。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "校对时刻")
    private LocalDateTime checkTime;

    /** 医嘱状态持久化码。 */
    @Schema(description = "医嘱状态码")
    private String statusEnum;

    /** 医嘱状态展示名。 */
    @Schema(description = "医嘱状态展示名")
    private String statusDictText;

    /** 解析出的默认定价 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键")
    private Long chargeDefinitionId;

    /** 定价展示名。 */
    @Schema(description = "定价名称")
    private String chargeDefinitionName;

    /** 本次解析使用的业务日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日")
    private LocalDate billDate;
}
