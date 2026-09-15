/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MiniappBindPatientRequest {

    private Long patientId;
    private Boolean asDefault;
}
