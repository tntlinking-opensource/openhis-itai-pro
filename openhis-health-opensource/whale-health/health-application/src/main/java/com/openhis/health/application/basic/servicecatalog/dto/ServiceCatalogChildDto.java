/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 诊疗目录子项（组合项目中的子服务/子收费单元）在创建与更新请求中的行结构。
 */
@Data
public class ServiceCatalogChildDto {

    /** 子项行主键（编辑回填时非空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属父活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 子项引用主数据标识（如子诊疗/耗材定义主键）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long childrenId;

    /** 子项数量或次数。 */
    private Integer childrenNum;

    /** 子项展示名称。 */
    private String childrenName;
}
