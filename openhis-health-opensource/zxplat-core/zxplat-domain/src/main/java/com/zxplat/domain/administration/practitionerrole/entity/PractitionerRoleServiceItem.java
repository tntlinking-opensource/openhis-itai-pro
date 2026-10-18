/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位服务项子实体，对应表 {@code adm_pr_assign_service}。
 *
 * <p>表达岗位角色（assignment）可提供的就诊类型集合：
 * 一个岗位角色可关联多个医疗服务（HealthcareService）。</p>
 *
 * <p>对应 DM-ADM-006 §3.2 与 SDD §4.3。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_pr_assign_service")
public class PractitionerRoleServiceItem extends BaseAuditedEntity {
    /** 岗位角色标识，关联主表 {@code adm_pr_assign.id}。 */
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    /** 医疗服务标识，关联 {@code adm_healthcare_service.id}。 */
    @Column(name = "healthcare_service_id", nullable = false)
    private Long healthcareServiceId;
}
