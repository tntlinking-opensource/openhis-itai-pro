/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.clinical.allergyhistory.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按患者查询过敏史列表请求。
 */
@Getter
@Setter
public class ListPatientAllergyHistoryRequest {

    /** 患者主键。 */
    private Long patientId;

    /** 当前就诊主键（一致性门控）。 */
    private Long encounterId;
}
