/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsagePatientRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-PMU-02：住院患者分页检索响应。
 *
 * <p>继承 {@link PagedResult}（content/totalCount/pageNo/pageSize）；与 AS-SDD 字面
 * {@code total}/{@code records} 语义等价，采纳工程统一分页壳。</p>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "住院患者药品使用-患者分页响应")
public class PageInpatientPatientMedicationUsagePatientsResponse
    extends PagedResult<InpatientPatientMedicationUsagePatientRowDto> {

    private static final long serialVersionUID = 1L;
}
