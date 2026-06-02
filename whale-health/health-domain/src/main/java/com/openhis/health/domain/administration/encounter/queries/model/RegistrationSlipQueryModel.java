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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 挂号单查询行（替代 {@code RegistrationSlipReadModel}）。
 */
@Getter
@Setter
public class RegistrationSlipQueryModel {

    private String slipNo;
    private Long encounterId;
    private String patientName;
    private String organizationName;
    private String doctorName;
    private String serviceTypeName;
    private String statusEnumText;
    /** 挂号费金额；由应用层按收费明细回填时可 null。 */
    private BigDecimal amount;
    /** 打印次数；主表未持久化时为 null（降级）。 */
    private Integer printCount;
}
