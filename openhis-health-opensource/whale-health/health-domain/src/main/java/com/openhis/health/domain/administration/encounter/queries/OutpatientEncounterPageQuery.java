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

import java.util.List;

/**
 * 门诊就诊上下文分页入参（替代 RM {@code OutpatientInfoPageQuery}）。
 */
@Getter
@Setter
public class OutpatientEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    private Long patientId;
    private Long doctorId;
    /**
     * 门诊医生站 IF-DOR-01：当前登录医师主键；非空时在读侧启用 BR-DOR-009
     * 「状态 × 参与者角色」可见性谓词（与 {@link #doctorId} 互斥，医生站路径仅用本字段）。
     */
    private Long doctorStationViewerPractitionerId;
    private String statusEnum;
    private List<String> statusEnumIn;
}
