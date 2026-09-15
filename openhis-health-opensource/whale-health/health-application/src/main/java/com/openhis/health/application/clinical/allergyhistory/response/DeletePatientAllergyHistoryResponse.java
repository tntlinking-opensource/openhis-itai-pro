/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除过敏史响应。
 */
@Getter
@Setter
public class DeletePatientAllergyHistoryResponse {

    /** 是否成功。 */
    private Boolean success;
}
