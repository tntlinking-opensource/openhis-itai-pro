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

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊挂号列表分页查询入参（查 {@code adm_encounter} 主表，AMB）。
 */
@Getter
@Setter
public class ClinicRegisterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    private String statusEnum;
    /** 挂号发生时刻窗下限（含）；语义 {@code coalesce(startTime, createdAt)}。 */
    private LocalDateTime startTime;
    /** 挂号发生时刻窗上限（含）。 */
    private LocalDateTime endTime;
}
