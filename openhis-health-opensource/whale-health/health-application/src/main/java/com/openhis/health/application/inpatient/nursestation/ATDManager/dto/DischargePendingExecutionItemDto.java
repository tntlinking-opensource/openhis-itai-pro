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

package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-IP-NUR-ATD-03：出院前待执行医嘱行。
 */
@Data
@Accessors(chain = true)
/** 出院前待执行医嘱行。 */
@Schema(description = "出院前待执行医嘱行")
public class DischargePendingExecutionItemDto {

    @Schema(description = "药品或诊疗请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 来源表：{@code med_medication_request} / {@code wkf_service_request}。 */
    @Schema(description = "请求来源表名")
    private String requestTable;

    /** 长临属性 code。 */
    @Schema(description = "长临属性 code")
    private String therapyEnum;

    /** 请求状态 code。 */
    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 药品名或诊疗名。 */
    @Schema(description = "药品名或诊疗名")
    private String displayName;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqAuthoredTime;

    /** 频次 code。 */
    @Schema(description = "频次 code")
    private String rateCode;

    /** 剂量/数量展示。 */
    @Schema(description = "剂量或数量展示文本")
    private String doseText;

    /** 阻塞说明：{@code LONG_NOT_STOPPED} / {@code TEMP_NOT_COMPLETED}。 */
    @Schema(description = "阻塞出院原因码")
    private String blockingReason;
}
