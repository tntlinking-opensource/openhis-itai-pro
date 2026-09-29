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

    /**
     * 技师：检验、检查等医技执行。
     * 工作科室只能选机构分类 {@code TECH}，与护士站 {@code NS} 分开。
     */
    String ROLE_MEDICAL_TECH = "ROLE_MEDICAL_TECH";

/** WhalePlatformRoleCodeConstants：ROLE_PHARMACIST。药师负责发药，以及门诊/住院药房交账的提交与撤回；审批或驳回不在本角色。 */
    String ROLE_PHARMACIST = "ROLE_PHARMACIST";

/** WhalePlatformRoleCodeConstants：ROLE_WAREHOUSE，值 {@code ROLE_WAREHOUSE}。 */
    String ROLE_WAREHOUSE = "ROLE_WAREHOUSE";

    /**
     * 财务：看财务报表，并审批或驳回（进货、退供应商、损益、盘点、调价、交账审批）。
     * <p>不提交、不撤回交账。收款交账是 {@link #ROLE_CASHIER}；药房交账是 {@link #ROLE_PHARMACIST}。不再另设财务审批角色。</p>
     */
    String ROLE_ACCOUNTANT = "ROLE_ACCOUNTANT";

    /**
     * 病案管理员：病案管理全菜单；门诊/住院「归档病例 / 解档病例」权限。
     * <p>与病案主档归档（质控/上传）区分：本角色操作的是临床病例文书锁定。</p>
     */
    String ROLE_MRMS_ADMIN = "ROLE_MRMS_ADMIN";

    /**
     * 款员：门诊/住院收款页面权限。
     * <p>替换院点自定义角色「收款款员」。收款台与交账提交/撤回用本角色，不用 {@link #ROLE_ACCOUNTANT}。</p>
     */
    String ROLE_CASHIER = "ROLE_CASHIER";
}
