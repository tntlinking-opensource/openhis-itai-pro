/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材发放写前门控读模型。
 */
@Getter
@Setter
/**
 * DeviceDispenseValidationModel。
 */
public class DeviceDispenseValidationModel {

    /** allow。 */
    /**
     * allow。
     */
    private Boolean allow;

    /** 原因编码。 */
    /**
     * reasonCode。
     */
    private String reasonCode;
}
