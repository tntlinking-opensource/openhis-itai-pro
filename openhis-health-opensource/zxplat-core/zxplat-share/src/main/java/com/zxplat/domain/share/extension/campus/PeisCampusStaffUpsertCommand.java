/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.campus;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 团检名册导入 → Campus 教职工 upsert 命令。
 */
@Getter
@Setter
public class PeisCampusStaffUpsertCommand {

    private Long tenantId;
    private Long operatorId;
    private String employeeNo;
    private Long patientId;
    private String fullName;
    private String genderEnum;
    private String idCardNo;
    private String mobilePhone;
    private Long departmentId;
    private String departmentName;
    private Long collegeId;
    private Long campusSiteId;
}
