/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.request;

import lombok.Data;

/**
 * 列出患者在检体检单请求。
 */
@Data
public class ListActivePersonalRegistrationsRequest {

    /** 患者主键。 */
    private Long patientId;
}
