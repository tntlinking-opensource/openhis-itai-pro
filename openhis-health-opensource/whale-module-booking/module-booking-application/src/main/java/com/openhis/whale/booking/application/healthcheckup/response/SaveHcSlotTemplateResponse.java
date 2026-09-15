/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存体检号源模板响应。
 */
@Getter
@Setter
public class SaveHcSlotTemplateResponse {

    /** 模板主键。 */
    private Long id;
}
