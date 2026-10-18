/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MetadataResolveRequest {

    private final String patientId;
    private final String encounterId;
    private final Long documentId;
    private final List<String> fieldKeys;
    /** 调用方覆盖（REQUEST 来源），优先级最高 */
    private final Map<String, String> metadataOverrides;
}
