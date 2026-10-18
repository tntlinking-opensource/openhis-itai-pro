/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 枚举字典查询请求（前端左侧枚举TAB使用）
 */
@Data
public class EnumDictRequest {

    /** 枚举简单类名（须落在术语枚举扫描包内）。 */
    @NotBlank(message = "枚举名称不能为空")
    private String enumName;
}
