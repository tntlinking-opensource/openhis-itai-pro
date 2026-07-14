/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院结算页初始化响应（IF-IP-STL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算页初始化响应")
public class LoadInpatientSettlePageInitResponse {

    /** 结算队列页签选项（业务字段）。 */
    @Schema(description = "结算队列页签选项")
    private List<OptionDto> settleQueueTabOptions;

    /** 费用呈现模式选项。 */
    @Schema(description = "费用呈现模式选项")
    private List<OptionDto> presentationModeOptions;

    /** 支付渠道选项。 */
    @Schema(description = "支付渠道选项")
    private List<OptionDto> paymentChannelOptions;

    /** 默认队列页签 code。 */
    @Schema(description = "默认队列页签 code")
    private String defaultSettleQueueTab;

    /** 默认费用呈现模式 code。 */
    @Schema(description = "默认费用呈现模式 code")
    private String defaultPresentationMode;
}
