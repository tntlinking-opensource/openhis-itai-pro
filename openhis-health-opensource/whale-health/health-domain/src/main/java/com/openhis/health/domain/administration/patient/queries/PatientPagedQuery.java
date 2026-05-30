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

package com.openhis.health.domain.administration.patient.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页检索患者查询入参。
 */
@Getter
@Setter
public class PatientPagedQuery {

    /**
     * 姓名关键字。
     */
    private String name;

    /**
     * 身份证号关键字。
     */
    private String idCard;

    /**
     * 院内编码关键字。
     */
    private String busNo;

    /**
     * 性别编码。
     */
    private String genderEnum;

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 启用标记。
     */
    private Boolean activeFlag;

    /**
     * 临时标记。
     */
    private Boolean tempFlag;

    /**
     * 建档机构标识。
     */
    private Long organizationId;

    /**
     * 页码（从 1 开始）。
     */
    private Integer pageNo;

    /**
     * 每页大小。
     */
    private Integer pageSize;
}

