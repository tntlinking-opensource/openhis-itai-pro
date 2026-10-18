/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2201 挂号平台登记结果。 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformExtensionOut {

    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
}
