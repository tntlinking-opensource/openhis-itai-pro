/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.encounterMetadata.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ResolveEncounterMetadataRequest {

    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /** 文书主键 id */
    private Long documentId;
    /** 限定解析的 fieldKey 列表；空表示全部启用字段 */
    private List<String> fieldKeys;
    /** 调用方覆盖，对应 REQUEST 来源 */
    private Map<String, String> metadataOverrides;
}
