/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
@Schema(description = "住院发药请求")
public class DispenseInpatientMedicationsRequest {

    /** 住院就诊主键；提交行须全部归属同一 encounter，否则 Gate 拒绝核发。 */
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
