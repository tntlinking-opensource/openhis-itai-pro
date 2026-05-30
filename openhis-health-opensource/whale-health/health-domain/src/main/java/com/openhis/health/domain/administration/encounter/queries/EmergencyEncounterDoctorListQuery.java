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

package com.openhis.health.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 急诊医生站：按就诊主数据（{@code adm_encounter}）分页检索入参。
 * <p>组织收窄由应用层写入会话机构标识；本对象仅承载已解析后的谓词条件。</p>
 */
@Getter
@Setter
public class EmergencyEncounterDoctorListQuery {

    private Integer pageNo;

    private Integer pageSize;

    /**
     * 科室/机构标识；可空表示不按组织列过滤（与门诊信息读模型会话缺省语义一致）。
     */
    private Long organizationId;

    /**
     * 就诊开始时间下限（含）；可空表示不按起始时刻下限过滤。
     */
    private LocalDateTime startTimeFrom;

    /**
     * 就诊开始时间上限（含）；可空表示不按起始时刻上限过滤。
     */
    private LocalDateTime startTimeTo;

    /**
     * 就诊状态编码列表（与 {@code EncounterStatus#code} 一致）；必填且非空由应用层保证。
     */
    private List<String> statusEnumIn;

    /**
     * 关键字：就诊业务号模糊，或命中患者姓名/院内号/证件/拼音/五笔（患者主档子查询）。
     */
    private String keyword;
}
