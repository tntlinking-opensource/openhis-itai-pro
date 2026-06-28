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

package com.openhis.health.domain.mrms.caserecord.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病案列表分页查询入参（DM-MRMS-001 §6）。
 *
 * <p>分页与关键字见基类 {@link AbstractPageQuery#getPageNo()}、{@link AbstractPageQuery#getKeyword()}。</p>
 */
@Getter
@Setter
public class CaseRecordPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 租户主键 {@code tenant_id}，条件下推必填。 */
    private Long tenantId;

    /** 病案状态单值过滤（与 {@link #statusCodes} 二选一，存 {@link com.openhis.health.domain.share.enums.CaseRecordStatus} code）。 */
    private String statusCode;

    /** 病案状态多值 IN 过滤（优先级高于 {@link #statusCode}）。 */
    private List<String> statusCodes;

    /** 入院科室名称精确匹配（主档冗余 {@code adm_caty_name}）。 */
    private String admCatyName;

    /** 主治医师 {@code adm_practitioner.id}（主档冗余 {@code atddr_practitioner_id}）。 */
    private Long attendingPractitionerId;

    /** 出院时间下限（含，主档冗余 {@code dscg_time}）。 */
    private LocalDateTime dscgFrom;

    /** 出院时间上限（含，主档冗余 {@code dscg_time}）。 */
    private LocalDateTime dscgTo;
}
