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
 * 住院发药就诊队列分页查询（存在药品发放事实且队列可见就诊的主键分页）。
 *
 * <p>对齐 AS-IP-PHA-001 IF-IPP-02：库房集合、发放状态子集、可选病区与关键字；
 * 可见态含在院与出院轨（PENDING/COMPLETED/DISCHARGED）；不按 {@code status_changed_time} 时间窗筛选。</p>
 */
@Getter
@Setter
public class InpatientPharmacyPendingEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 发药库房库位主键集合（与 {@code MedicationDispense.location_id} 对齐）；非空时按库房 {@code IN} 收敛。
     */
    private List<Long> locationIds;

    /**
     * 参与收敛的 {@code DispenseStatus#getCode()} 集合；空表示不按发放状态过滤。
     */
    private List<String> dispenseStatusEnumIn;

    /**
     * 病区主键（与 {@code InpatientInfo.ward_id} 对齐）；为空表示不按病区过滤。
     */
    private Long wardId;

    /**
     * 应用层预解析的队列可见就诊主键集合（含病区/关键字收窄）；由 {@code PharmacyEncounterQueueLoader} 写入，领域层仅 {@code IN} 过滤。
     */
    private List<Long> resolvedEncounterScope;
}
