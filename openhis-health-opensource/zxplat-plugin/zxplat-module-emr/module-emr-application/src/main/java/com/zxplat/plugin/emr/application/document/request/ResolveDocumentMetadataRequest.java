/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ResolveDocumentMetadataRequest {

    /** 文书主键 id */
    private Long documentId;
    /** 限定解析的 fieldKey 列表；空表示全部启用字段 */
    private List<String> fieldKeys;
    /** 请求侧覆盖的元数据值 */
    private Map<String, String> metadataOverrides;
}
