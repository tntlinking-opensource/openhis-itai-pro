/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MiniappBindPractitionerRequest {

    /** 直接指定 adm_practitioner.id（测试/管理端）。 */
    private Long practitionerId;
    /** 院内工号（adm_practitioner.bus_no）；与 {@link #practitionerId} 二选一。 */
    private String employeeNo;
    /** 工号绑定时校验姓名（可选但推荐）。 */
    private String name;
    private Boolean asDefault;
}
