/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 校园学生绑定（学号 + 姓名）。
 */
@Getter
@Setter
public class MiniappBindCampusStudentRequest {

    private String studentNo;
    private String name;
    private Boolean asDefault;
}
