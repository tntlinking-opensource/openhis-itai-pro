/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FrameworkRuntimeIngestRequest {

    private String insightCode;
    private String category;
    private String runtimeVersion;
    private Long operatorUserId;
    private LocalDateTime sessionStartedAt;
    private String orgCategory;
    private String orgName;
    private String deploymentFingerprint;
    private String licenseCustomer;
    private LocalDateTime licenseNotAfter;
    private String payloadJson;
    private LocalDateTime observedAt;
}
