/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方取药结果查询结果。
 */
@Data
@Accessors(chain = true)
public class ElepMedExtensionOut {

    /** 平台调用是否成功。 */
    private boolean success;

    /** 取药结果原始 JSON（平台字段透传，供前端或审计解析）。 */
    private String medResultJson;

    /** 失败或提示信息。 */
    private String message;
}
