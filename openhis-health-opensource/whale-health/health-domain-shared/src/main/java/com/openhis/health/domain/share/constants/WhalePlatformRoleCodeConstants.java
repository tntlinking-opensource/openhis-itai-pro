/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.share.constants;

/**
 * 平台角色 {@code whale_roles.code} 字面量（角色真源唯一，禁止另建业务 roleCode 字典）。
 */
public interface WhalePlatformRoleCodeConstants {

    String ROLE_ADMIN = "ROLE_ADMIN";

    String ROLE_DOCTOR = "ROLE_DOCTOR";

    String ROLE_NURSE = "ROLE_NURSE";

    String ROLE_PHARMACIST = "ROLE_PHARMACIST";

    String ROLE_WAREHOUSE = "ROLE_WAREHOUSE";

    String ROLE_ACCOUNTANT = "ROLE_ACCOUNTANT";
}
