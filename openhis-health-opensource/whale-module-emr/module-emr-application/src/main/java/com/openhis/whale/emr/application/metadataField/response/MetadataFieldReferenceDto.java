/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.metadataField.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MetadataFieldReferenceDto {

    /** 是否被模板引用 */
    private boolean referenced;
    /** 引用该 fieldKey 的模板名称列表 */
    private List<String> templateNames = new ArrayList<>();
}
