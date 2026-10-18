/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方状态查询结果。
 */
@Data
@Accessors(chain = true)
public class ElepQueryExtensionOut {

    /** 平台调用是否成功。 */
    private boolean success;

    /** 处方状态码（平台 rx_stas_codg）。 */
    private String rxStasCodg;

    /** 处方使用状态码（平台 rx_used_stas_codg，是否已取药等）。 */
    private String rxUsedStasCodg;

    /** 失败或提示信息。 */
    private String message;
}
