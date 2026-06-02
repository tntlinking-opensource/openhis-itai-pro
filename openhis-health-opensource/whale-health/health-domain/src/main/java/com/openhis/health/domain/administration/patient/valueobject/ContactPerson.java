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
