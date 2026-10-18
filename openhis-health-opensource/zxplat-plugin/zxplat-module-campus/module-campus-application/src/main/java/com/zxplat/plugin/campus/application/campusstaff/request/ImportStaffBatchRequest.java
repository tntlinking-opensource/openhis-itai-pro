/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 教职工 Excel/批量导入（IF-CMP-STF-14）。
 */
@Getter
@Setter
public class ImportStaffBatchRequest {

    private Long tenantId;

    @NotNull
    private Long defaultDepartmentId;

    @NotNull
    private Long defaultCampusSiteId;

    @NotEmpty
    private List<ImportStaffRow> rows = new ArrayList<>();

    @Getter
    @Setter
    public static class ImportStaffRow {
        private String name;
        private String employeeNo;
        private String idCard;
        private String phone;
        private String genderEnum;
        private String medicalCode;
        private Long departmentId;
        private Long campusSiteId;
        private Long patientId;
    }
}
