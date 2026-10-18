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
public class ImportRosterFromCampusRequest {

    private Long batchId;

    /** STUDENT / STAFF */
    private String memberKind;

    private Long companyId;

    private String className;

    private Long departmentId;

    /** 为空则导入预览范围内的全部人员 */
    private List<Long> patientIds = new ArrayList<>();

    private Boolean autoEnroll;
}
