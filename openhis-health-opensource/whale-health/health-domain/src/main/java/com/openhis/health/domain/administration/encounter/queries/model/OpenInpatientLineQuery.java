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

/**
 * 按门诊/急诊锚点查询「未取消住院线」的只读结果。
 *
 * <p>供住院申请防重（BR-IP-REQ-002）与医生站是否已申请展示。</p>
 */
@Getter
@Setter
public class OpenInpatientLineQuery {

    /** 是否存在未取消的住院就诊。 */
    private boolean exists;

    /** 住院就诊主键；无命中时为 null。 */
    private Long encounterId;

    /** 住院业务号；无命中时为 null。 */
    private String busNo;

    /** 住院就诊状态码。 */
    private String statusEnum;
}
