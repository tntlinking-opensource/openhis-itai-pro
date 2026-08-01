/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 平台角色 {@code whale_roles.code} 字面量（角色真源唯一，禁止另建业务 roleCode 字典）。
 */
public interface WhalePlatformRoleCodeConstants {

/** WhalePlatformRoleCodeConstants：ROLE_ADMIN，值 {@code ROLE_ADMIN}。 */
    String ROLE_ADMIN = "ROLE_ADMIN";

/** WhalePlatformRoleCodeConstants：ROLE_DOCTOR，值 {@code ROLE_DOCTOR}。 */
    String ROLE_DOCTOR = "ROLE_DOCTOR";

/** WhalePlatformRoleCodeConstants：ROLE_NURSE，值 {@code ROLE_NURSE}。 */
    String ROLE_NURSE = "ROLE_NURSE";

/** WhalePlatformRoleCodeConstants：ROLE_PHARMACIST，值 {@code ROLE_PHARMACIST}。 */
    String ROLE_PHARMACIST = "ROLE_PHARMACIST";

/** WhalePlatformRoleCodeConstants：ROLE_WAREHOUSE，值 {@code ROLE_WAREHOUSE}。 */
    String ROLE_WAREHOUSE = "ROLE_WAREHOUSE";

/** WhalePlatformRoleCodeConstants：ROLE_ACCOUNTANT，值 {@code ROLE_ACCOUNTANT}。 */
    String ROLE_ACCOUNTANT = "ROLE_ACCOUNTANT";

    /** 财务审批：财务审批族供应单（进货/退供应商/损益/盘点）与调价单的审批/驳回岗位。 */
    String ROLE_FINANCE_APPROVER = "ROLE_FINANCE_APPROVER";
}
