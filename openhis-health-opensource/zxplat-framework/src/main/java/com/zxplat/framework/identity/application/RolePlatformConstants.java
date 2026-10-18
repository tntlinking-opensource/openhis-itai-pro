/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application;

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

    /**
     * 平台内置运维账号用户名（登录名；医务人员展示名兜底与此同口径，比较时忽略大小写）。
     */
    String ADMIN_USERNAME = "admin";

    /** 自定义角色内部 name 前缀。 */
    String CUSTOM_NAME_PREFIX = "custom_";
}
