/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 挂号按挂号科室 + 号源类型净人次（AS-RPT-001 IF-RPT-WKL-02 下半区；BR-RPT-WKL-009）。
 *
 * <p>事实源为 Encounter 直统（对齐 AS-RPT-007），非 ChargeItem/Payment 链路计次。
 * 应用层未选科室时按 {@link #serviceTypeId} **跨 {@link #organizationId} 合并**为
 * {@code registrationRows[]}（BR-RPT-WKL-016）；本模型仅承载领域层 org 粒度事实。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadRegistrationOrgSummaryQueryModel {

    /** 挂号科室 {@code adm_encounter.organization_id}（方案 C，非 requesting_org_id）。 */
    private Long organizationId;

    /** 号源类型 {@code adm_encounter.service_type_id}。 */
    private Long serviceTypeId;

    /** 净人次（一就诊一次；退号/待挂/错误态不计入）。 */
    private int netChargeCount;
}
