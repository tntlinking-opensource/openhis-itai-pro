/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 住院登记页初始化响应（IF-IP-REG-00）。 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记页初始化响应")
public class LoadInpatientRegistrationPageInitResponse {

    /** 当前用户可选的支付渠道选项（ADR-PAY-001 语义码 ∩ 477 active）。 */
    @Schema(description = "支付渠道下拉选项")
    private List<OptionDto> paymentChannelOptions;
}
