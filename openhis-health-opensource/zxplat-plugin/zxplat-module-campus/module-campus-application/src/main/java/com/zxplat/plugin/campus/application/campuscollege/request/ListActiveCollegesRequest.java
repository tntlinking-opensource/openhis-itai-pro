/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campuscollege.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListActiveCollegesRequest {

    private Long tenantId;
    private String campusCode;
}
