/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.campus;

import java.util.List;

/**
 * PEIS 团检筹备从 Campus 拉取名册读侧 Extension。
 */
public interface PeisCampusRosterQueryExtension {

    List<PeisCampusDepartmentOptionView> listDepartments(Long tenantId, Long collegeIdOrNull);

    List<String> listStudentClassNames(Long tenantId, Long collegeId);

    List<PeisCampusStudentRosterView> listStudentsForRoster(Long tenantId, Long collegeId, String className);

    List<PeisCampusStaffRosterView> listStaffForRoster(Long tenantId, Long departmentId);
}
