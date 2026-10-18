/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.valueobject;

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
