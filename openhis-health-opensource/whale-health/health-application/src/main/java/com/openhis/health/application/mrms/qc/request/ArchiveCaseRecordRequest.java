/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案归档请求。
 */
@Getter
@Setter
public class ArchiveCaseRecordRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 病案质量等级（甲/乙/丙，可选）。 */
    private String qualityGradeCode;
}
