/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.org;

import com.zxplat.plugin.campus.domain.org.view.CampusCollegeView;
import com.zxplat.plugin.campus.domain.org.view.CampusDepartmentView;
import com.zxplat.plugin.campus.domain.org.view.CampusSiteView;

import java.util.List;
import java.util.Optional;

public interface CampusOrgQueryReader {

    List<CampusSiteView> listActiveCampusSites(Long tenantId);

    Optional<CampusSiteView> resolveCampusByCode(Long tenantId, String campusCode);

    List<CampusCollegeView> listActiveColleges(Long tenantId, String campusCodeOrNull);

    Optional<CampusCollegeView> getCollegeById(Long tenantId, Long collegeId);

    List<CampusDepartmentView> listDepartments(Long tenantId, Long collegeIdOrNull, String campusCodeOrNull);
}
