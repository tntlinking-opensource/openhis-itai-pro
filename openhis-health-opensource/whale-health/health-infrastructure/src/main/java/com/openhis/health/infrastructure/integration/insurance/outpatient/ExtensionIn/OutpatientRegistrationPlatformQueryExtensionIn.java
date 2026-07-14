/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** 批量查询就诊平台登记状态入参。 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformQueryExtensionIn {

    /** 就诊主键列表。 */
    private List<Long> encounterIds;

    /** 就诊 → 支付草稿（可为空 map）。 */
    private Map<Long, Long> paymentDraftIdByEncounter;
}
