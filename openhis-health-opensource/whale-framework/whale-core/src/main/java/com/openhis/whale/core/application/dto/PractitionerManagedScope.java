/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import java.util.Collections;
import java.util.List;

/**
 * 医务人员岗位分配派生的数据管辖范围（来源 {@code adm_pr_assign}）。
 *
 * @param managedLocationIds         可操作药库 + 药房（{@code adm_location.id}；
 *                                   {@code WAREHOUSE_ADMIN}/{@code PHARMACIST} 之 location_id）
 * @param managedOrganizationUnitIds 管辖科室（{@code adm_organization.id}；
 *                                   {@code DOCTOR}/{@code NURSE}/{@code ADMINISTRATIVE} 之 org_id）
 * @param managedWardLocationIds     管辖病区（{@code adm_location.id}，{@code form_enum=WARD}；
 *                                   {@code NURSE} 之 location_id）
 */
public record PractitionerManagedScope(
    List<Long> managedLocationIds,
    List<Long> managedOrganizationUnitIds,
    List<Long> managedWardLocationIds) {

    /** 未绑定医务人员或无岗位分配时的空范围。 */
    public static final PractitionerManagedScope EMPTY = new PractitionerManagedScope(
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());

    public PractitionerManagedScope {
        if (managedLocationIds == null) {
            managedLocationIds = Collections.emptyList();
        }
        if (managedOrganizationUnitIds == null) {
            managedOrganizationUnitIds = Collections.emptyList();
        }
        if (managedWardLocationIds == null) {
            managedWardLocationIds = Collections.emptyList();
        }
    }
}
