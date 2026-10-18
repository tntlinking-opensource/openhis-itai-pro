/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交住院登记响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交住院登记结果")
public class SubmitRegistrationResponse {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 登记后状态 code。 */
    @Schema(description = "登记后状态 code（REGISTERED，待护士分床）")
    private String statusEnum;

    /** 预交金支付单 ID；未收款为 null。 */
    @Schema(description = "预交金支付单 ID；未收款为 null")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 是否本次新开账户。 */
    @Schema(description = "是否本次新开账户")
    private Boolean accountOpened;

    /** 医保就诊 ID。 */
    @Schema(description = "医保就诊 ID（2401 成功时）")
    private String ybMdtrtId;
}
