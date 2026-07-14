/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application;

/**
 * 平台角色声明：角色管理能力（人员维护页可编辑块；持久化 claim_type 仍为 StaffScope）。
 *
 * <p>写入 {@code whale_role_claims.claim_type} / {@code claim_value}，由用户维护页读取并控制表单区块可见性。</p>
 */
public interface RoleStaffScopeConstants {

    /** 声明类型：人员维护范围。 */
    String CLAIM_TYPE = "StaffScope";

    /** 出诊科室。 */
    String DOCTOR_VISIT_ORG = "DOCTOR_VISIT_ORG";

    /** 管理库房（物理位置）。 */
    String WAREHOUSE_LOCATION = "WAREHOUSE_LOCATION";

    /** 管理药房（物理位置）。 */
    String PHARMACY_LOCATION = "PHARMACY_LOCATION";

    /** 护士管理科室。 */
    String NURSE_ORG = "NURSE_ORG";

    /** 管理病区（物理位置）。 */
    String NURSE_WARD_LOCATION = "NURSE_WARD_LOCATION";
}
