/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.peis;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册患者端上下文视图（登记台 / 小程序共用语义）。
 */
@Getter
@Setter
public class PeisGroupRosterDeskView {

    private Long rosterId;
    private Long batchId;
    private Long patientId;
    private String fullName;
    private Long companyId;
    private String companyName;
    private String batchName;
    private Long contractId;
    private Long packageActivityId;
    private String packageName;
    private Long encounterId;
    private String payStatusCode;
    private String arrivalStatusCode;
    private Boolean enrolled;
    private Boolean arrived;
    private Long selectedPackageActivityId;
    private Boolean packageSelected;
    private Boolean canChangePackage;
    private List<PeisGroupPackageOptionView> optionalPackages = new ArrayList<>();
}
