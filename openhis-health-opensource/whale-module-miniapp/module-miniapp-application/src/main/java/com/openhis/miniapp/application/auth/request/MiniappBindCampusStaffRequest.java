/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 校园教职工绑定（工号 + 姓名）。
 */
@Getter
@Setter
public class MiniappBindCampusStaffRequest {

    private String employeeNo;
    private String name;
    private Boolean asDefault;
}
