/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.query;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 医嘱/目录开立分页入参（读主表；由应用层 {@code ProductDefinitionPageQuery} 转入）。
 *
 * <p>诊疗表 {@code pageForCatalog} 会排除已被套餐子表引用的子项目录行；
 * 组套/历史回填依赖 {@link #prepickedProductKeys} 豁免。</p>
 */
@Getter
@Setter
public class ProductDefinitionCatalogPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** product表名Name（ProductDefinitionCatalogPageQuery）。 */
    private String productTableName;
    /** product表名Names（ProductDefinitionCatalogPageQuery）。 */
    private List<String> productTableNames;
    /** 类别编码（ProductDefinitionCatalogPageQuery）。 */
    private String categoryCode;
    /**
     * 诊疗业务类型（{@code wkf_activity_definition.type_enum} / 术语 {@code activity_type_code}）。
     * 仅活动定义分页消费；药/耗忽略。
     */
    private String typeEnum;
    /** 排除药品 medication_category_code（开立西药医嘱排除中草药等）。 */
    private List<String> excludeMedicationCategoryCodes;

    /**
     * 排除诊疗/耗材等定义的 {@code category_code}（如门诊禁开嘱托传 {@code ["52"]}）。
     *
     * <p>仅作用于活动定义等使用本字段的 QueryService；药品侧仍用 {@link #excludeMedicationCategoryCodes}。</p>
     */
    private List<String> excludeCategoryCodes;

    private Boolean activeFlag;
    /** 供应商主键（ProductDefinitionCatalogPageQuery）。 */
    private Long supplierId;
    /**
     * 生产厂家主键筛选（cus_supplier.id）；优先于 {@link #manufacturer} 字符串。
     */
    private Long manufacturerId;
    /**
     * 生产厂家主键集合筛选（IN）；非空时与 {@link #manufacturerId} 合并为 OR/IN 口径由 QueryService 决定。
     */
    private List<Long> manufacturerIds;
    /**
     * 厂家名称（兼容旧入参；QueryService 仅在可解析为 Long 时按 manufacturerId 等值过滤，
     * 否则忽略——名称→id 解析由 AppService 负责后写入 {@link #manufacturerId}）。
     */
    private String manufacturer;
    /** 划价目录：{@code 1} 时排除药品表（与 RM 口径一致）。 */
    private Integer pricingFlag;
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;

    /**
     * 执行科室组织主键（可选；仅活动定义分页消费）。
     *
     * <p>医技划价诊疗选品：传入登录 TECH 科 id，下推 {@code wkf_activity_definition.org_id}。</p>
     */
    private Long orgId;

    /**
     * 仅返回账面可用库存大于 0 的药/耗定义（分页前过滤）；诊疗表忽略本字段。
     */
    private Boolean positiveQuantityOnly;

    /**
     * 「只看库存&gt;0」时 EXISTS 限定的库位集合（与目录可开立库存展示候选库位对齐）。
     *
     * <p>{@code null}：不按库位收窄；非 null（含空列表）：仅这些库位有正库存才命中。</p>
     */
    private List<Long> positiveQuantityLocationIds;

    /** sortField（ProductDefinitionCatalogPageQuery）。 */
    private String sortField;
    /** sortOrder（ProductDefinitionCatalogPageQuery）。 */
    private String sortOrder;
}
