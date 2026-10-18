/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载收费页初始化请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载收费页初始化请求")
public class LoadChargePageInitRequest {
    /**
     * 当前操作机构主键。
     */
    /** 机构 ID（业务字段）。 */
    @Schema(description = "机构 ID")
    private Long organizationId;
}
