/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;

/**
 * 通用ID请求
 * 用于只需要ID参数的方法
 */
@Data
public class IdRequest {

    /**
     * ID
     */
    private Long id;
}
