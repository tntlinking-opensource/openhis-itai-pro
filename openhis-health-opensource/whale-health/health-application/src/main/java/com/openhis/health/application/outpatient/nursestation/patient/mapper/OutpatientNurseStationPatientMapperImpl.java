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
import com.openhis.health.domain.administration.encounter.queries.NurseStationOutpatientQueueQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutpatientNurseStationPatientMapper} 默认实现：HTTP 请求 ↔ 护士站队列 Query/Response。
 */
@Component
public class OutpatientNurseStationPatientMapperImpl implements OutpatientNurseStationPatientMapper {

    /**
     * HTTP 队列请求 → 域侧护士站队列查询（分页窗 + 可选开立科室 + 仅待发耗材）。
     *
     * @param request HTTP 请求；可为 null（返回空查询壳）
     * @return 护士站队列查询入参
     */
    @Override
    public NurseStationOutpatientQueueQuery toNurseStationOutpatientQueueQuery(
        PageNurseStationPatientQueueRequest request) {
        NurseStationOutpatientQueueQuery query = new NurseStationOutpatientQueueQuery();
        // Pre：仅搬运分页窗（含就诊开始时间起止）；开立科室/待发筛选单独写入
        PageQueryBinder.bind(request, query);
        // 空请求只保留默认分页壳，避免 NPE
        if (request != null) {
            query.setOrgId(request.getOrgId());
            // 仅待发耗材：空视为 false，避免 null 在规约里无法判断
            query.setPendingDeviceOnly(Boolean.TRUE.equals(request.getPendingDeviceOnly()));
        }
        return query;
    }

    /**
     * 门诊就诊上下文分页 → 护士站队列响应（1-based 页码由 {@link PagedResultBuilder} 换算）。
     *
     * @param page 读模型分页；可为 null
     * @return 队列响应（行内 {@code hasPendingDevice} 缺省 false，由 AppService 再刷）
     */
    @Override
    public PageNurseStationPatientQueueResponse toPageNurseStationPatientQueueResponse(
        Page<OutpatientEncounterContextQueryModel> page) {
        List<NurseStationPatientQueueItemDto> content = new ArrayList<>();
        // 空页仍组装 total=0 的响应，避免前端拿不到分页壳
        if (page != null) {
            for (OutpatientEncounterContextQueryModel row : page.getContent()) {
                NurseStationPatientQueueItemDto dto = this.toNurseStationPatientQueueItemDto(row);
                // 跳过无法投影的空行
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
        // 空读模型无法投影卡片
        if (row == null) {
            return null;
        }
        NurseStationPatientQueueItemDto dto = new NurseStationPatientQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setPatientName(row.getPatientName());
        dto.setGenderEnum(row.getGenderEnum());
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, row.getGenderEnum());
        // 术语命中才写展示文案，避免把 code 当中文
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        // 出生时刻有值才切 LocalDate，避免 NPE
        if (row.getBirthDate() != null) {
            dto.setBirthDate(row.getBirthDate().toLocalDate());
        }
        dto.setAgeText(row.getAgeText());
        dto.setSlipNo(row.getEncounterBusNo());
        dto.setEncounterTime(row.getStartTime());
        // 待发放耗材红点由 AppService 批量回填；缺省 false 避免前端把 null 当有提醒
        dto.setHasPendingDevice(Boolean.FALSE);
        return dto;
    }
}
