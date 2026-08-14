/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatientemr.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊归档/解档病例请求（按就诊锁定 EMR 文书）。
 */
@Getter
@Setter
public class ArchiveOutpatientClinicalCaseRequest {

    /**
     * 门诊就诊主键；必填。
     */
    private Long encounterId;
}
