/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application;

/**
 * 平台角色声明：分岗位时可绑定的组织/位置范围（AssignmentScope）。
 *
 * <p>写入 {@code zxplat_role_claims.claim_type} / {@code claim_value}，用于限制岗位分配 UI 与保存校验。</p>
 */
public interface RoleAssignmentScopeConstants {

    /** 声明类型：岗位范围。 */
    String CLAIM_TYPE = "AssignmentScope";

    /** 科室（{@code adm_organization.id}）。 */
    String ORG = "ORG";

    /** 病区类物理位置（{@code adm_location.id}，形态为病区）。 */
    String WARD = "WARD";

    /** 药房类物理位置。 */
    String PHARMACY = "PHARMACY";

    /** 库房类物理位置。 */
    String WAREHOUSE = "WAREHOUSE";

    /** 不限组织/位置（纯登录权限包，通常不分岗位）。 */
    String GLOBAL = "GLOBAL";
}
