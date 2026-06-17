/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.metadataField.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateMetadataFieldRequest {

    /** 主键 id */
    private Long id;
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
