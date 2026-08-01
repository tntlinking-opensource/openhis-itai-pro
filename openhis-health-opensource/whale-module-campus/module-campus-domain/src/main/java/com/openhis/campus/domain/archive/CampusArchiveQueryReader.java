/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archive;

import com.openhis.campus.domain.archive.view.ArchiveTransferView;
import com.openhis.campus.domain.archive.view.CampusProfileView;
import com.openhis.campus.domain.archive.view.StudentCampusView;

import java.util.List;
import java.util.Optional;

public interface CampusArchiveQueryReader {

    Optional<StudentCampusView> resolveStudentCampus(Long tenantId, Long patientId);

    Optional<StudentCampusView> resolveStudentCampusByStudentNo(Long tenantId, String studentNo);

    Optional<CampusProfileView> resolveCampusProfileByPatientId(Long tenantId, Long patientId);

    List<ArchiveTransferView> listTransfers(Long tenantId, String subjectType, Long subjectId);

    /**
     * 按学院+可选行政班列出在校且已绑定患者的学生（团检名册）。
     */
    List<StudentCampusView> listStudentsForRoster(Long tenantId, Long collegeId, String className);
}
