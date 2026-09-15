/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.response;

import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者过敏史列表响应。
 */
@Getter
@Setter
public class ListPatientAllergyHistoryResponse {

    /** 是否存在有效过敏原。 */
    private Boolean historyFlag;

    /** 已确认无已知过敏。 */
    private Boolean nkdaFlag;

    /** 摘要：未登记 / 无 / 过敏原顿号拼接。 */
    private String summaryText;

    /** 过敏史行。 */
    private List<PatientAllergyHistoryRowDto> rows;
}
