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

package com.openhis.health.application.emergency.patientmanagement.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者管理工作站列表分页筛选（应用层读侧入参，无独立领域聚合）。
 */
@Getter
@Setter
public class PatientManagementPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    /** 分诊主去向 location_id；空表示全部区域。 */
    private Long locationId;

    /** 距挂号时间：ALL / WITHIN_60 / WITHIN_120 / OVER_30 / OVER_60。 */
    private String registrationTimeFilter;

    private boolean exactSearch;

    private Long patientId;

    private String patientName;

    private String bedLabel;

    private String keyword;
}
