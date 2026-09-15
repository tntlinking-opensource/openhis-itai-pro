/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 过敏史表单枚举选项。
 */
@Getter
@Setter
public class PatientAllergyHistoryEnumOptionDto {

    /** 枚举持久化 code。 */
    private String code;

    /** 展示文案。 */
    private String displayText;
}
