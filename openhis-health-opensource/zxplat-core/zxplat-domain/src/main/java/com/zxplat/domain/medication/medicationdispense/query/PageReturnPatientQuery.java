/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.query;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊药房退药患者队列分页查询（AS-OP-PHA-002-SDD IF-PHA-02-02）。
 *
 * <p>关键字使用父类 {@link #keyword}，与 {@code searchKey} 入参在应用层装配对齐；科室过滤为就诊快照科室主键。</p>
 */
@Getter
@Setter
public class PageReturnPatientQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 科室过滤（与门诊信息读模型 {@code organization_id} 对齐）；为空表示不按科室过滤。
     */
    private Long departmentId;

    /**
     * 发药库房库位主键集合（与 {@code MedicationDispense.location_id} 收敛）；非空时按库房 {@code IN} 收敛；空表示不按库房过滤。
     */
    private List<Long> locationIds;

    /**
     * 应用层预解析的门诊就诊主键收窄集合（科室/关键字）；{@code null} 表示不按就诊主键额外收窄。
     */
    private List<Long> resolvedEncounterScope;
}
