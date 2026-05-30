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

package com.openhis.health.application.querysupport.encounter;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.EmergencyEncounterDoctorListQuery;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 急诊医生站就诊列表读组装：患者关键字在患者聚合解析，就诊分页仅查 {@code adm_encounter}（B.6.1）。
 */
@Component
@RequiredArgsConstructor
public class EmergencyEncounterListLoader {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 分页查询急诊就诊主数据（患者关键字在患者域预解析，分页在 {@link EncounterQueryService}）。
     *
     * @param query 查询入参（含 B.10.11 分页键）
     * @return 就诊实体分页
     */
    public Page<Encounter> pageEmergencyEncountersForDoctorStation(EmergencyEncounterDoctorListQuery query) {
        List<Long> keywordPatientIds = Collections.emptyList();
        if (query != null && StringUtils.isNotBlank(query.getKeyword())) {
            keywordPatientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(query.getKeyword().trim());
        }
        return this.encounterQueryService.pageEmergencyEncountersForDoctorStation(query, keywordPatientIds);
    }
}
