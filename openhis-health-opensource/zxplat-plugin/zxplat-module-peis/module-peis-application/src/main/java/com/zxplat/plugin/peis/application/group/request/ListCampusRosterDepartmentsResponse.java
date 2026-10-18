/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ListCampusRosterDepartmentsResponse {

    private List<com.zxplat.plugin.peis.application.group.dto.CampusRosterDepartmentOptionDto> departments =
        new ArrayList<>();
}
