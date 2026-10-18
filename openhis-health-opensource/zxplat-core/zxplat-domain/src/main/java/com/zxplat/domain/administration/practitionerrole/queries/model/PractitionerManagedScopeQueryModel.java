/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 医务人员岗位分配派生的管辖范围读模型（{@code adm_pr_assign}）。
 */
@Getter
@Setter
public class PractitionerManagedScopeQueryModel {

    /** 管辖库位（药库/药房）。 */
    private List<Long> managedLocationIds = new ArrayList<>();

    /** 管辖科室。 */
    private List<Long> managedOrganizationUnitIds = new ArrayList<>();

    /** 管辖病区位置。 */
    private List<Long> managedWardLocationIds = new ArrayList<>();
}
