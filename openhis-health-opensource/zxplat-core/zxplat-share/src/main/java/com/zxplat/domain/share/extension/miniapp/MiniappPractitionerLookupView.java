/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.miniapp;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序医护绑定查档视图。
 */
@Getter
@Setter
public class MiniappPractitionerLookupView {

    private Long practitionerId;
    private String name;
    /** 院内工号（adm_practitioner.bus_no）。 */
    private String busNo;
}
