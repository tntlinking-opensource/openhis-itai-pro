/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地追溯码行（3512/3513 差异比对出参） */
@Data
@Accessors(chain = true)
public class LocalInvUploadTraceCompareResult {

    private Long traceId;
    private String drugTracCodg;
    private Long uploadId;
    private String contractBusNo;
    private String infno;
    private String fixmedinsBchno;
    private String medListCodg;
    private String uploadStatus;
    private String createdAt;
}
