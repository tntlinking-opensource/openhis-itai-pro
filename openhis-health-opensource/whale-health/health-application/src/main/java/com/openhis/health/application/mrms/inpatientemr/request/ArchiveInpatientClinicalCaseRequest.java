/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院归档/解档病例请求（按就诊锁定 EMR 文书）。
 */
@Getter
@Setter
public class ArchiveInpatientClinicalCaseRequest {

    /**
     * 住院就诊主键；必填。
     */
    private Long encounterId;
}
