/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.application.dto;

import lombok.Data;

/**
 * 权限查询请求
 */
@Data
public class PermissionQueryRequest {

    private String keyword;

    private String parentName;
}
