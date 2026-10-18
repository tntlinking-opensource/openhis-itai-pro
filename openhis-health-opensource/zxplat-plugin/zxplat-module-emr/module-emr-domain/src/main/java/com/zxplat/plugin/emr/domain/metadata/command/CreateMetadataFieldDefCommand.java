/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建元数据字段定义命令（DM-EMR-003）。
 */
@Getter
@Setter
public class CreateMetadataFieldDefCommand {

    private String fieldKey;

    private String label;

    private String dataType;

    private Boolean editableDefault;

    private String valueSource;

    private String sourcePath;

    /** 已序列化的 JSON；可空。 */
    private String sourceConfigJson;

    private String defaultValue;

    private String maskRule;

    private String formatPattern;

    private Integer sortOrder;

    private Boolean enabled;

    private String remark;
}
