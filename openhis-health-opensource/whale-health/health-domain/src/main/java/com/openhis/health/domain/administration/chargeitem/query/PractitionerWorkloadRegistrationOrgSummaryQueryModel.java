/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 挂号费按挂号科室 + 号源类型净笔数（DM-ADM-011 §6.2.29；AS-RPT-001 IF-RPT-WKL-02 下半区）。
 *
 * <p>应用层未选科室时按 {@link #serviceTypeId} **跨 {@link #organizationId} 合并**为
 * {@code registrationRows[]}（BR-RPT-WKL-016）；本模型仅承载领域层 org 粒度事实。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadRegistrationOrgSummaryQueryModel {

    /** 挂号科室 {@code adm_encounter.organization_id}（方案 C，非 requesting_org_id）。 */
    private Long organizationId;

    /** 号源类型 {@code adm_encounter.service_type_id}。 */
    private Long serviceTypeId;

    /** 净收费笔数（SUCCESS payment +1；REFUNDED payment −1；已退号就诊排除在域层过滤）。 */
    private int netChargeCount;
}
