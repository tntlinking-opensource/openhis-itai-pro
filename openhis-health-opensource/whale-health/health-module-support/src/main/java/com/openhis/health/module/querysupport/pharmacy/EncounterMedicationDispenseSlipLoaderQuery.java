/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 就诊药品发药单 slip 分页读入参（AppService / report-bridge → {@link EncounterMedicationDispenseSlipPageLoader}）。
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

    /** 发药库房（库位）主键。 */
    private Long locationId;

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
