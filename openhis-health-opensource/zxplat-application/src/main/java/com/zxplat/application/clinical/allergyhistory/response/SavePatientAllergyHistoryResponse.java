/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.clinical.allergyhistory.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存过敏史响应。
 */
@Getter
@Setter
public class SavePatientAllergyHistoryResponse {

    /** 记录主键。 */
    private Long allergyRecordId;
}
