/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionquery.response;

import com.zxplat.application.outpatient.pharmacy.prescriptionquery.dto.OutpatientPharmacyPrescriptionDrugLineDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询门诊处方明细响应（IF-OPQ-02）。
 *
 * <p>分页元数据沿用框架 {@link PagedResult}：{@code totalCount}/{@code pageNo}/{@code pageSize}/{@code content}。
 * 其中 {@code totalCount} 对齐 AS-SDD 契约中的明细行总数（文档曾写作 {@code total}）。
 * 另附筛选窗内去重汇总：总处方量 / 就诊人数（轻量 COUNT DISTINCT，非小计行）。</p>
 */
@Getter
@Setter
public class PageOutpatientPharmacyPrescriptionQueryResponse
    extends PagedResult<OutpatientPharmacyPrescriptionDrugLineDto> {

    /** 总处方量：筛选窗内 {@code prescription_no} 去重。 */
    private Integer prescriptionCount;

    /** 就诊人数：筛选窗内 {@code encounter_id} 去重。 */
    private Integer visitPatientCount;
}
