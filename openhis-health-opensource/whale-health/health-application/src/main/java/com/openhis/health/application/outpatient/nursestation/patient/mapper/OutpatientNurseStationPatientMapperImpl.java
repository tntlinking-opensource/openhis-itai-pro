/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.mapper;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.nursestation.patient.dto.NurseStationPatientQueueItemDto;
import com.openhis.health.application.outpatient.nursestation.patient.request.PageNurseStationPatientQueueRequest;
import com.openhis.health.application.outpatient.nursestation.patient.response.PageNurseStationPatientQueueResponse;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutpatientNurseStationPatientMapper} 默认实现。
 */
@Component
public class OutpatientNurseStationPatientMapperImpl implements OutpatientNurseStationPatientMapper {

    @Override
    public AbstractPageQuery toNurseStationOutpatientQueueQuery(PageNurseStationPatientQueueRequest request) {
        return PageQueryBinder.copyToPlainQuery(request);
    }

    @Override
    public PageNurseStationPatientQueueResponse toPageNurseStationPatientQueueResponse(
        Page<OutpatientEncounterContextQueryModel> page) {
        List<NurseStationPatientQueueItemDto> content = new ArrayList<>();
        if (page != null) {
            for (OutpatientEncounterContextQueryModel row : page.getContent()) {
                NurseStationPatientQueueItemDto dto = this.toNurseStationPatientQueueItemDto(row);
                if (dto != null) {
                    content.add(dto);
                }
            }
        }
        return PagedResultBuilder.build(new PageNurseStationPatientQueueResponse(), content, page);
    }

    /**
     * 门诊护士站就诊上下文 → 患者队列项 DTO（含性别展示）。
     *
     * @param row 上下文行
     * @return 队列项；null 输入返回 null
     */
    private NurseStationPatientQueueItemDto toNurseStationPatientQueueItemDto(OutpatientEncounterContextQueryModel row) {
        if (row == null) {
            return null;
        }
        NurseStationPatientQueueItemDto dto = new NurseStationPatientQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setPatientName(row.getPatientName());
        dto.setGenderEnum(row.getGenderEnum());
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, row.getGenderEnum());
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        if (row.getBirthDate() != null) {
            dto.setBirthDate(row.getBirthDate().toLocalDate());
        }
        dto.setAgeText(row.getAgeText());
        dto.setSlipNo(row.getEncounterBusNo());
        dto.setEncounterTime(row.getStartTime());
        return dto;
    }
}
