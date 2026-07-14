/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-02：查询是否已存在住院申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-查询是否已存在住院申请响应")
public class CheckInpatientAdmissionExistsResponse {

    @Schema(description = "是否已存在未取消的住院就诊")
    /** 是否已存在未取消的住院就诊。 */
    private boolean exists;

    /** 住院就诊主键；不存在时为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊主键；不存在时为空。 */
    @Schema(description = "住院就诊主键；不存在时为空")
    private Long inpatientEncounterId;

    /** 住院就诊状态枚举编码。 */
    @Schema(description = "住院就诊状态枚举编码")
    private String statusEnum;
}
