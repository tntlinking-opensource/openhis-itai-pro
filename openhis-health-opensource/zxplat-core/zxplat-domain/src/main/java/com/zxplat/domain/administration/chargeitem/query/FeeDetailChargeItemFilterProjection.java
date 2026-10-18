/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 费用明细硬筛选用 ChargeItem 轻量投影（仅本聚合 Criteria 选出；禁止当业务实体预载）。
 *
 * <p>供应用层按项目关键字 / 细分类 / 费用性质过滤就诊白名单，避免一个月全日窗全量装载
 * {@link com.zxplat.domain.administration.chargeitem.entity.ChargeItem}。
 * 细分类（fin_type）硬筛仅需 {@code definition_id} 与挂号识别字段，<strong>不</strong>选
 * {@code unit_price}（避免为筛选项全窗装单价，也禁止用「投影无单价」冒充脏数据）。</p>
 */
@Getter
@Setter
public class FeeDetailChargeItemFilterProjection {

    /** 收费明细主键 {@code adm_charge_item.id}。 */
    private Long id;

    /** 就诊主键 {@code encounter_id}。 */
    private Long encounterId;

    /** 产品来源表 {@code product_table}。 */
    private String productTable;

    /** 产品主键 {@code product_id}。 */
    private Long productId;

    /** 挂账账户主键 {@code account_id}。 */
    private Long accountId;

    /** 定价定义主键 {@code definition_id}（细分类 fin_type 锚点）。 */
    private Long definitionId;

    /**
     * 生成来源枚举 code（{@code generate_source_enum}）；与 {@link #serviceTable} 供挂号强制诊察费识别。
     */
    private String generateSourceEnum;

    /** 上游业务表名锚点（{@code service_table}）；挂号行为 {@code adm_healthcare_service}。 */
    private String serviceTable;

    /** 行售价 {@code total_price}（净口径页脚代数和用）。 */
    private BigDecimal totalPrice;
}
