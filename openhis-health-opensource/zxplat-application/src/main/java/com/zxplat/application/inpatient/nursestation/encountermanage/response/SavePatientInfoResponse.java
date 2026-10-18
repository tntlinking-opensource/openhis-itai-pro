/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.encountermanage.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者信息维护响应（IF-IP-NUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者信息维护响应")
public class SavePatientInfoResponse {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 是否保存成功。 */
    @Schema(description = "是否保存成功")
    private Boolean saved;
}
