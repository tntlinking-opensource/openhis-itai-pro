/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import lombok.Data;

/**
 * 删除用户请求
 */
@Data
public class DeleteUserRequest {

    /**
     * 用户ID
     */
    private Long id;
}
