/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 护士工作量统计读查询（DM-CLN-004 §6.2.10～6.2.11、DM-WKF-007 §6.2.20～6.2.22；AS-RPT-002）。
 *
 * <p>就诊 scope 与 SR/耗材定义 enrich 由
 * {@link com.zxplat.application.report.nurseworkload.support.NurseWorkloadLoader#prepareNurseWorkloadQueryScopes}
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

    /** 门诊/住院范围（{@link com.zxplat.domain.share.constants.NurseWorkloadConstants}）。 */
    private String encounterScope;

    /** 可选单护士：诊疗取 Procedure 主执行人；耗材取 preparer_id 或 performer_id（随开关）。优先于 {@link #practitionerIds}。 */
    private Long practitionerId;

    /**
     * 默认登录科室下的护士主键集合（科室统计）。
     *
     * <p>{@code null} = 不按护士集合收窄；空列表 = 该科无护士，读侧须返回空结果（禁止回落全院）。
     * 由应用层按 {@code Practitioner.org_id}（默认登录科室）解析后写入。</p>
     */
    private List<Long> practitionerIds;

    /**
     * 开立科室（{@code ChargeItem.requesting_org_id}）；非空时金额 enrich 仅匹配该科室账单行。
     *
     * <p>科室工作量按默认登录科室统计时由应用层置空，金额随执行护士归属汇总，不再按开立科室二次收窄。</p>
     */
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

    /**
     * 同窗内诊疗净执行事实缓存（{@code Procedure} 实体列表）。
     *
     * <p>由 {@link com.zxplat.domain.clinical.procedure.service.ProcedureQueryService}
     * 在首次加载后写入；同一次 HTTP 请求内汇总/页脚/卡片禁止重复扫库。禁止跨请求依赖。</p>
     */
    private transient List<?> cachedNetCompletedProceduresForRequest;

    /**
     * 同窗内耗材候选发放事实缓存（{@code DeviceDispense} 实体列表）。
     *
     * <p>由 {@link com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService}
     * 写入；类型为 workflow 聚合实体，故用 {@code List<?>} 避免 clinical 包依赖 workflow 实体。</p>
     */
    private transient List<?> cachedNurseConsumableCandidatesForRequest;
}
