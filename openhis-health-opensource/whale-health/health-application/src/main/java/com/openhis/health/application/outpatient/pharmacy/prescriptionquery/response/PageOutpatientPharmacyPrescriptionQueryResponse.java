/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionquery.response;

import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.dto.OutpatientPharmacyPrescriptionDrugLineDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 分页查询门诊处方明细响应（IF-OPQ-02）。
 *
 * <p>分页元数据沿用框架 {@link PagedResult}：{@code totalCount}/{@code pageNo}/{@code pageSize}/{@code content}。
 * 其中 {@code totalCount} 对齐 AS-SDD 契约中的明细行总数（文档曾写作 {@code total}）。</p>
 */
public class PageOutpatientPharmacyPrescriptionQueryResponse
    extends PagedResult<OutpatientPharmacyPrescriptionDrugLineDto> {
}
