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

package com.openhis.health.domain.administration.patient.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者主要地址值对象。
 */
@Getter
@Setter
@Embeddable
public class Address {

    /**
     * 地址全文。
     */
    @Column(name = "address")
    private String text;

    /**
     * 省级行政区。
     */
    @Column(name = "address_province")
    private String province;

    /**
     * 市级行政区。
     */
    @Column(name = "address_city")
    private String city;

    /**
     * 区县级行政区。
     */
    @Column(name = "address_district")
    private String district;

    /**
     * 街道详细地址。
     */
    @Column(name = "address_street")
    private String street;
}
