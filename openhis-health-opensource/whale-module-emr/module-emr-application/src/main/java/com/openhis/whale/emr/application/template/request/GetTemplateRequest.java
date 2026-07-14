/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetTemplateRequest {

    /** 雪花 ID 以字符串传递 */
    private String id;
}
