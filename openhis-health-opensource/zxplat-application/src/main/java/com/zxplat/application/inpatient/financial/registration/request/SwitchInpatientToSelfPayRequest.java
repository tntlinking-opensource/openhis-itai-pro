/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院医保转自费请求（IF-IP-REG-09）。
 *
 * <p>整次住院从医保入院改为自费结算：撤销平台入院，未结费用改挂个人现金户。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医保转自费请求")
public class SwitchInpatientToSelfPayRequest {

    /** 住院就诊主键。须为待入科、在院或挂起，且当前是医保入院。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 操作人主键。空时由登录会话解析。 */
    @Schema(description = "操作人 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
}
