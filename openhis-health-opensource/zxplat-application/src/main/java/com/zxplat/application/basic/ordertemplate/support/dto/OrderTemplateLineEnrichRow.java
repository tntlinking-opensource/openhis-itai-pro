/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.support.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 医嘱模板行目录富化中间行（querysupport 只读侧）。
 * <p>
 * 由 {@code OrderTemplateLineCatalogEnrichAssembler} 批量填充展示字段；
 * AppService 在边界将应用层 DTO 映射为本类型，富化后再写回 DTO。
 * </p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class OrderTemplateLineEnrichRow {

    /** 行种类 code（药品/诊疗/材料，对应 {@code OrderTemplateLineKind}）。 */
    private String lineKindEnum;

    /** 药品定义主键（行种类为药品时）。 */
    private Long medicationDefinitionId;

    /** 诊疗活动定义主键（行种类为诊疗时）。 */
    private Long activityDefinitionId;

    /** 材料定义主键（行种类为材料时）。 */
    private Long deviceDefinitionId;

    /** 产品定义表名（enrich 后回填）。 */
    private String productTableName;

    /** 产品展示名称（enrich 后回填）。 */
    private String productName;

    /** 产品规格展示文本（enrich 后回填）。 */
    private String productSpec;

    /** 产品类别 code（enrich 后回填）。 */
    private String categoryCode;

    /** 默认单位 code（入参可带，缺省时由产品定义回填）。 */
    private String defaultUnitCode;

    /** 默认单位展示名（入参可带，缺省时由产品定义回填）。 */
    private String defaultUnitName;

    /** 频次定义主键。 */
    private Long frequencyId;

    /** 频次展示文本（enrich 后回填）。 */
    private String frequencyText;

    /** 给药途径 code。 */
    private String routeCode;

    /** 给药途径展示名（enrich 后回填）。 */
    private String routeName;

    /** 默认执行科室主键。 */
    private Long defaultPerformOrgId;

    /** 默认执行科室展示名（enrich 后回填）。 */
    private String defaultPerformOrgName;
}
