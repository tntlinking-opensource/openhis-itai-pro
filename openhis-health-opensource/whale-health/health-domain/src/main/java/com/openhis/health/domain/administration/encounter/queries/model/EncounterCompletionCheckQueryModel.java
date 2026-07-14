/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊完诊资格校验结果模型。
 */
@Getter
@Setter
public class EncounterCompletionCheckQueryModel {

    /**
     * 是否可完诊。
     */
    private Boolean completable;

    /**
     * 不可完诊原因码。
     */
    private String failureCode;

    /**
     * 不可完诊说明。
     */
    private String failureMessage;

    /**
     * 就诊标识。
     */
    private Long encounterId;
}
