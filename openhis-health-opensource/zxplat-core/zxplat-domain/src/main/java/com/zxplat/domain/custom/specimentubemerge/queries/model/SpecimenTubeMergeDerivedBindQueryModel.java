/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 合管配置派生绑定读模型（采血费 activity 或合管耗材；不含组内检验项目 activity 行）。
 *
 * <p>供开嘱/执行绑定解析：同一 {@code deviceId + groupNo} 下耗材只带出一份；
 * 采血费由应用层同批全局去重为 1 份（跨设备/跨组）。</p>
 */
@Getter
@Setter
public class SpecimenTubeMergeDerivedBindQueryModel {

    /** 合管配置行主键 {@code cus_specimen_tube_merge.id}。 */
    private Long mergeConfigLineId;

    /** 合管检验设备主键 {@code adm_device.id}。 */
    private Long deviceId;

    /** 设备内合管组序号（从 1 起）。 */
    private Integer groupNo;

    /** 绑定目标表：{@code wkf_activity_definition} 或 {@code adm_device_definition}。 */
    private String targetTable;

    /** 绑定目标主键。 */
    private Long targetId;

    /** 耗材用量；采血费 activity 行为 null。 */
    private BigDecimal quantity;

    /** 耗材单位 code；采血费 activity 行为 null。 */
    private String unitCode;

    /** 同组内展示排序。 */
    private Integer displayOrder;

    /** 目标展示名（应用层 enrich）。 */
    private String targetName;

    /** 目标业务编码（应用层 enrich）。 */
    private String targetBusNo;

    /** 目标规格展示（应用层 enrich）。 */
    private String specificationText;

    /** 单位展示名（应用层 enrich）。 */
    private String unitName;
}
