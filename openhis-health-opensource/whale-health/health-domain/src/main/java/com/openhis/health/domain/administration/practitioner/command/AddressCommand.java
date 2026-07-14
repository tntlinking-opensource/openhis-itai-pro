/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitioner.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 地址命令。
 */
@Getter
@Setter
public class AddressCommand {
    /** 省。 */
    private String province;
    /** 市。 */
    private String city;
    /** 区。 */
    private String district;
    /** 街道。 */
    private String street;
    /** 地址JSON。 */
    private String addressJson;
}
