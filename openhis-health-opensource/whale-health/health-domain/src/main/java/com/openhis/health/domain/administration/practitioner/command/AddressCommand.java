/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
