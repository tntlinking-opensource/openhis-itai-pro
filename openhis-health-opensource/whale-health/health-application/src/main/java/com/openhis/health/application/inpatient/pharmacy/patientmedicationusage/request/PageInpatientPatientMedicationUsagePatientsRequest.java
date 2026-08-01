/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-PMU-02：分页检索住院患者请求。
 *
 * <p>{@code keyword}（姓名/住院号/床号模糊）与 {@code pageNo}/{@code pageSize} 继承自
 * {@link PageQueryBinder.AbstractHealthPageRequest}；分页窗口必填且从 1 起（BR-IP-PMU-010）。</p>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "住院患者药品使用-患者分页检索请求（keyword/pageNo/pageSize）",
    requiredProperties = {"pageNo", "pageSize"})
public class PageInpatientPatientMedicationUsagePatientsRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;
}
