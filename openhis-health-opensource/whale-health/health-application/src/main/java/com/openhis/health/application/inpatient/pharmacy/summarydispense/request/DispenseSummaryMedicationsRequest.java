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

package com.openhis.health.application.inpatient.pharmacy.summarydispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.SummaryDispenseTraceLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量汇总核发请求（IF-IPS-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 批量汇总核发 IF-IPS-03。 */
@Schema(description = "批量汇总核发 IF-IPS-03", requiredProperties = {"summaryNos", "receiverId", "preparerPractitionerId"})
public class DispenseSummaryMedicationsRequest {

    @Schema(description = "待核发汇总单号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> summaryNos;

    /** 领药人主键（写入 SupplyDelivery.receiver_id）。 */
    @Schema(description = "领药人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 调配药师主键（不得与当前发药药师同人）。 */
    @Schema(description = "调配药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preparerPractitionerId;

    /** 核发时补录/覆盖追溯码（可选）。 */
    @Schema(description = "核发时补录或覆盖追溯码")
    private List<SummaryDispenseTraceLineSubmitDto> traceLines;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 是否上传医保进销存。 */
    @Schema(description = "是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo（上传时必填）。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
