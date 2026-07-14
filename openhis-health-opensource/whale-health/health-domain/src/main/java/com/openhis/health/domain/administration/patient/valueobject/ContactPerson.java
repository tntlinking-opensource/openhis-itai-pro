/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者联系人值对象。
 */
@Getter
@Setter
@Embeddable
public class ContactPerson {

    /**
     * 联系人姓名。
     */
    @Column(name = "link_name")
    private String name;

    /**
     * 关系编码。
     */
    @Column(name = "link_relation_code")
    private Integer relationCode;

    /**
     * 联系电话。
     */
    @Column(name = "link_telcom")
    private String telcom;
}
