/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.transferout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 驳回药房调出单请求（IF-PHA-TR-07）。
 *
 * <p>原因码与展示文案的合并与落库规则见 Mapper 装配注释。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class RejectTransferOutRequest {

    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因码。 */
    @Schema(description = "驳回原因码")
    private String changeReasonCode;

    /** 驳回原因展示。 */
    @Schema(description = "驳回原因展示")
    private String changeReason;
}
