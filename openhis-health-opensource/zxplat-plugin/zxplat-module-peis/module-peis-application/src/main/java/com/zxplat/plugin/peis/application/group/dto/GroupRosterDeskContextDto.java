/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 登记台团检名册上下文（按人）。
 */
@Data
public class GroupRosterDeskContextDto {

    private Long rosterId;
    private Long batchId;
    private Long patientId;
    private String fullName;
    private Long companyId;
    private String companyName;
    private String batchName;
    private Long packageActivityId;
    private String packageName;
    private Long encounterId;
    private String payStatusCode;
    private String arrivalStatusCode;
    /** 是否已完成建单（含免收费 ENROLLED 与收费 PAID）。 */
    private Boolean enrolled;
    /** 是否已到检。 */
    private Boolean arrived;

    /** 合同主键。 */
    private Long contractId;

    /** 可选套餐列表。 */
    private List<PackageOptionDto> optionalPackages = new ArrayList<>();

    /** 已选定套餐 AD。 */
    private Long selectedPackageActivityId;

    /** 是否已选定套餐。 */
    private Boolean packageSelected;

    /** 到检前是否可换套餐。 */
    private Boolean canChangePackage;
}
