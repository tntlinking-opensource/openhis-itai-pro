/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.patientstudent.service;

import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 学生档案更新结果，暴露组织字段是否变更。
 */
@Getter
@RequiredArgsConstructor
public class PatientStudentUpdateResult {

    private final PatientStudent student;
    private final boolean orgChanged;
}
