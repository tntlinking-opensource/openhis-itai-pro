/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 根据编码系统名称与编码获取显示名请求（多参合并为单参）
 *
 */
@Data
public class GetDisplayNameRequest {

    /** 编码体系 name（机器名）。 */
    @NotBlank(message = "编码系统名称不能为空")
    private String codeSystemName;

    /** 概念编码。 */
    @NotBlank(message = "编码不能为空")
    private String code;
}
