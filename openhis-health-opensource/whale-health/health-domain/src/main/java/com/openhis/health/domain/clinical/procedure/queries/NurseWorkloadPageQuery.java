/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 护士工作量统计读查询（DM-CLN-004 §6.2.10～6.2.11、DM-WKF-007 §6.2.20～6.2.22；AS-RPT-002）。
 *
 * <p>就诊 scope 与 SR/耗材定义 enrich 由
 * {@link com.openhis.health.application.report.nurseworkload.support.NurseWorkloadLoader#prepareNurseWorkloadQueryScopes}
 * 预解析后写入本对象；Procedure/DeviceDispense QueryService 仅读本聚合表（B.6.4）。</p>
 */
@Getter
@Setter
public class NurseWorkloadPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 统计日起（闭区间；诊疗映射 {@code Procedure.recorded_time}，耗材映射业务时间自然日）。 */
    private LocalDate dateFrom;

    /** 统计日止（闭区间）。 */
    private LocalDate dateTo;

    /** 门诊/住院范围（{@link com.openhis.health.domain.share.constants.NurseWorkloadConstants}）。 */
    private String encounterScope;

    /** 可选护士：诊疗取 Procedure 主执行人；耗材取 preparer_id 或 performer_id（随开关）。 */
    private Long practitionerId;

    /** 开立科室（{@code ChargeItem.requesting_org_id}）；非空时金额 enrich 仅匹配该科室账单行。 */
    private Long requestingOrgId;

    /** 解析后的就诊类别存储码（AMB / IMP）；由 Support 写入。 */
    private String resolvedEncounterClassCode;

    /** 解析后的就诊 id 集合（该域全部就诊，无科室收窄）；由 Support 写入。 */
    private List<Long> resolvedEncounterScope;

    /**
     * 耗材是否自动扣库（全局 {@code deviceAutoInventoryDeductSwich}）；由 Support 写入。
     *
     * <p>{@code false} → 统计 {@code PREPARED} + {@code preparer_id} + {@code prepare_time}；
     * {@code true} → {@code COMPLETED} + {@code performer_id} + {@code dispense_time}（空则回落 prepare_time）。</p>
     */
    private Boolean deviceAutoInventoryDeductEnabled;

    /** {@code serviceRequestId → category_enum}；Support 经 {@code ServiceRequestQueryService} 批量 enrich。 */
    private Map<Long, String> categoryEnumByServiceRequestId;

    /** {@code deviceDefId → hvcm_flag}；Support 经 {@code DeviceDefinitionQueryService} 批量 enrich。 */
    private Map<Long, Boolean> hvcmFlagByDeviceDefId;
}
