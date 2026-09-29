/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消审核响应。
 */
@Getter
@Setter
@Schema(description = "报告审核-取消审核响应")
public class CancelAuditResponse {

    /** 报告主键。 */
    @Schema(description = "报告主键")
    private Long reportId;

    /** 回退后状态（待审 preliminary）。 */
    @Schema(description = "状态")
    private String statusEnum;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 读侧索引是否已标 cancelled。 */
    @Schema(description = "索引是否已吊销")
    private Boolean indexRevoked;
}
