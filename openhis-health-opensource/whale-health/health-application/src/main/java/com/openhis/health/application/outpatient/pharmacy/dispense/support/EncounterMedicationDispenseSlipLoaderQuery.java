/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 就诊药品发药单 slip 分页读入参（AppService / report-bridge → {@link OutpatientEncounterMedicationDispenseSlipPageLoader}）。
 */
@Getter
@Setter
public class EncounterMedicationDispenseSlipLoaderQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 就诊主键（当前医嘱 Tab 查询锚点；历史 Tab 作排除锚点）。 */
    private Long encounterId;

    /** 患者主键（历史 Tab 查询锚点；与医生站患者门控一致）。 */
    private Long patientId;

    /**
     * 医生站医嘱查询范围 Tab（{@link com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab}）。
     *
     * <p>{@code CURRENT_ADVICE}（默认）当前就诊；{@code HISTORY} 患者历史并排除当前 {@code encounterId}。</p>
     */
    private String adviceScopeTabCode;

    /** 开单科室主键；{@code null} 表示不按科室过滤。 */
    private Long orderingDepartmentId;

    /** 发药库房（库位）主键（单库位精确匹配；与 {@link #locationIds} 互斥时优先本字段）。 */
    private Long locationId;

    /**
     * 发药库房库位主键集合（药房工作站 IF-OPP-03/IF-IPP-03 与队列 IF-OPP-02/IF-IPP-02 同源收窄）。
     */
    private List<Long> locationIds;

    /** 发放状态 code 集合。 */
    private List<String> dispenseStatusEnumIn;

    /** 医嘱发布态 RequestStatus code 集合（门诊口径）。 */
    private List<String> requestStatusEnumIn;

    /** 仅中药饮片（IF-OPP-03 / IF-IPP-03 装配层按 {@code tcmFlag} 过滤）。 */
    private Boolean tcmOnly;

    /** 仅西药中成药（与 {@code tcmOnly} 互斥，二者均空表示不过滤）。 */
    private Boolean westernOnly;

    /** 仅门诊药品发放。 */
    private Boolean outpatientMedicationOnly;

    /** 仅住院药品发放。 */
    private Boolean inpatientMedicationOnly;
}
