/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.metadataField.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CreateMetadataFieldRequest {

    /** 元数据字段键（模板引用标识，字母开头） */
    private String fieldKey;
    /** 显示名称 */
    private String label;
    /** 数据类型（string/number/date 等） */
    private String dataType;
    /** 新建文书时该字段默认可编辑 */
    private Boolean editableDefault;
    /** 取值来源枚举 code（CONST/SYSTEM/PATIENT 等） */
    private String valueSource;
    /** 上下文路径或 API 路径（依 valueSource） */
    private String sourcePath;
    /** 扩展 JSON 配置（表达式、字段引用等） */
    private Map<String, Object> sourceConfig;
    /** 无上下文时的默认值 */
    private String defaultValue;
    /** 脱敏规则 code：NONE/ID_CARD/PHONE */
    private String maskRule;
    /** 展示格式化模式 */
    private String formatPattern;
    /** 排序序号 */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 备注说明 */
    private String remark;
}
