/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新元数据字段定义命令（DM-EMR-003）；fieldKey 不可改。
 */
@Getter
@Setter
public class UpdateMetadataFieldDefCommand {

    private Long id;

    private String label;

    private String dataType;

    private Boolean editableDefault;

    private String valueSource;

    private String sourcePath;

    private boolean sourcePathSpecified;

    private String sourceConfigJson;

    private boolean sourceConfigSpecified;

    private String defaultValue;

    private boolean defaultValueSpecified;

    private String maskRule;

    private String formatPattern;

    private Integer sortOrder;

    private Boolean enabled;

    private String remark;

    private boolean remarkSpecified;
}
