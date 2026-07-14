/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application;

/**
 * 平台角色编码与命名约定（仅服务端使用，不对业务用户展示）。
 */
public interface RolePlatformConstants {

    /** 内置平台角色 code 前缀。 */
    String BUILTIN_CODE_PREFIX = "ROLE_";

    /** 院方自定义角色 code 前缀。 */
    String CUSTOM_CODE_PREFIX = "ROLE_CUSTOM_";

    /** 系统管理员内置 code。 */
    String CODE_ADMIN = "ROLE_ADMIN";

    /** 自定义角色内部 name 前缀。 */
    String CUSTOM_NAME_PREFIX = "custom_";
}
