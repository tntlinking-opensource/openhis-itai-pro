/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.queries.model;

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
