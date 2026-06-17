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

package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.DispenseMedicationLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院发药（核发）请求（IF-IPP-05）。
 */
@Data
@Accessors(chain = true)
/** 住院发药请求。 */
@Schema(description = "住院发药请求")
public class DispenseInpatientMedicationsRequest {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 待发药行列表。 */
    @Schema(description = "发药行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DispenseMedicationLineSubmitDto> lines;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 是否上传医保进销存 3505。 */
    @Schema(description = "是否上传医保进销存 3505")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo（上传进销存 3505 时使用）。 */
    @Schema(description = "医保契约 busNo（上传进销存时使用）")
    private String contractBusNo;
}
