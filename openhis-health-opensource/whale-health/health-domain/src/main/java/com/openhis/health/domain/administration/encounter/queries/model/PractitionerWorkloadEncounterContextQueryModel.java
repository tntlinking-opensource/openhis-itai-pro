/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 工作量统计用就诊读上下文（DM-ADM-002 §6.2.31；AS-RPT-001 挂号号源分组）。
 *
 * <p>挂号统计科室键为 {@link #organizationId}（方案 C）；应用层未选科室时按 {@link #serviceTypeId} 跨科室合并。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadEncounterContextQueryModel {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 挂号科室 {@code adm_encounter.organization_id}。 */
    private Long organizationId;

    /** 号源类型 {@code adm_encounter.service_type_id}。 */
    private Long serviceTypeId;

    /** 就诊状态存储码（{@code CANCELLED} 挂号 chargeItem 在域层排除）。 */
    private String statusEnum;

    /** 就诊类别存储码（AMB / IMP，与 encounterScope 一致）。 */
    private String classEnum;
}
