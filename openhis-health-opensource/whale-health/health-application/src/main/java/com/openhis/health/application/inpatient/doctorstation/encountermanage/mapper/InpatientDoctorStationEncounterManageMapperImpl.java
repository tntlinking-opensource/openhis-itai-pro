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

package com.openhis.health.application.inpatient.doctorstation.encountermanage.mapper;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;

import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationPatientQueueItemDto;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link InpatientDoctorStationEncounterManageMapper} 实现：住院医生站就诊管理应用层字段映射（AS-IP-DOR-001）。
 *
 * <p><b>对应接口规格</b>：IF-IP-DOR-01（在院患者队列分页）。</p>
 *
 * <p><b>职责</b>：将列表分页请求装配为住院信息读模型查询入参（Q-IPI-006），将读模型行与账户/展示增补
 * 装配为医生站患者卡片 DTO；不包含 Gate、批量查询与诊断解析（由 AppService 与公共 Assembler 承担）。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>医生站队列读侧内置 IMP + IN_PROGRESS/ON_HOLD + 当前科室收窄（{@code doctorStationQueue=true}）。</li>
 *   <li>展示字段优先读模型列，缺失时回落 {@link InpatientInfoQueueDisplayAssembler} 覆盖层（BR-IP-DOR-008 状态文案）。</li>
 *   <li>入科时间读模型未投影，与护士站列表口径一致返回 null。</li>
 * </ul></p>
 */
@Component
public class InpatientDoctorStationEncounterManageMapperImpl implements InpatientDoctorStationEncounterManageMapper {

    /**
     * 列表分页请求 → 住院信息读模型分页入参（IF-IP-DOR-01 Pre / Q-IPI-006）。
     *
     * <p>实现要点：关键字 trim 后下推；科室 id 由 AppService Gate 保证非空后传入；
     * {@code doctorStationQueue} 固定 true 以激活读侧医生站专用过滤。</p>
     *
     * @param request HTTP 分页请求，允许为 null（仅装配默认队列条件）
     * @return 住院就诊上下文分页查询，永不为 null；科室收窄由 {@link com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver} 从会话解析
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(PagePatientQueueRequest request) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        if (request != null && StringUtils.isNotBlank(request.getKeyword())) {
            // 姓名/住院号等模糊检索：去首尾空白，避免全空格触发无效 LIKE。
            query.setKeyword(request.getKeyword().trim());
        }
        // 医生站专用队列：读侧内置 IMP + IN_PROGRESS/ON_HOLD + 科室收窄（Q-IPI-006）。
        query.setDoctorStationQueue(Boolean.TRUE);
        return query;
    }

    /**
     * 读模型行 + 账户与展示增补 → 医生站列表卡片 DTO（IF-IP-DOR-01 Asm）。
     *
     * <p>实现要点：读模型列优先，overlay/enrichment 作展示回落；状态中文须经
     * {@link InpatientEncounterStatusDisplayUtil} 推导（含转科/出院类型）；诊断列表由上游解析后传入，
     * 禁止在此透传 {@code diagnosisJson} 原文。</p>
     *
     * @param row               住院信息读模型当前页行
     * @param accountBalance    展示用账户余额（财务口径计算结果，非账户余额直出）
     * @param lowBalanceWarning 是否低余额预警（阈值 500 元，由 AppService 判定）
     * @param displayOverlay    队列展示覆盖层；可 null
     * @param priorityEnum      病情/优先级 code；读模型未投影时由就诊主档批量回填，可 null
     * @param diagnosisList     诊断展示行列表（快照解析结果），可 null
     * @param diagnosisSummary  诊断一行摘要（分号拼接），可 null
     * @param enrichment        病房名与检索码增补；可 null
     * @return 列表行 DTO；{@code row==null} 时返回 null
     */
    @Override
    public DoctorStationPatientQueueItemDto toDoctorStationPatientQueueItemDto(
        InpatientEncounterContextQueryModel row,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        String priorityEnum,
        List<String> diagnosisList,
        String diagnosisSummary,
        DoctorStationQueueDisplayEnrichment enrichment) {
        if (row == null) {
            return null;
        }
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayOrgName = null;
        String overlayWardName = null;
        String overlayBedName = null;
        String overlayStatusText = null;
        if (displayOverlay != null) {
            // 展示覆盖：读模型列缺失时由 Assembler 从主档/位置补全。
            overlayBusNo = displayOverlay.getEncounterBusNo();
            overlayPatientName = displayOverlay.getPatientName();
            overlayOrgName = displayOverlay.getOrganizationName();
            overlayWardName = displayOverlay.getWardName();
            overlayBedName = displayOverlay.getBedName();
            overlayStatusText = displayOverlay.getStatusEnumText();
        }
        String enrichmentHouseName = null;
        String enrichmentPinyin = null;
        String enrichmentWubi = null;
        if (enrichment != null) {
            enrichmentHouseName = enrichment.getHouseName();
            enrichmentPinyin = enrichment.getPinyinCode();
            enrichmentWubi = enrichment.getWubiCode();
        }
        DoctorStationPatientQueueItemDto dto = new DoctorStationPatientQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setEncounterBusNo(firstNonBlank(row.getEncounterBusNo(), overlayBusNo));
        dto.setStatusEnum(row.getStatusEnum());
        // BR-IP-DOR-008：IMP 状态展示须走 InpatientEncounterStatusDisplayUtil，读模型/overlay 缺省时回落。
        String statusEnumText = firstNonBlank(row.getStatusEnumText(), overlayStatusText);
        if (StringUtils.isBlank(statusEnumText)) {
            statusEnumText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                row.getStatusEnum(),
                row.getTransferOrgId(),
                row.getDischargeTypeEnum());
        }
        dto.setStatusEnumText(statusEnumText);
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(firstNonBlank(row.getPatientName(), overlayPatientName));
        dto.setGenderEnum(row.getGenderEnum());
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, row.getGenderEnum());
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        dto.setAgeText(row.getAgeText());
        dto.setWardId(row.getWardId());
        dto.setWardName(firstNonBlank(row.getWardName(), overlayWardName));
        dto.setHouseName(enrichmentHouseName);
        dto.setBedId(row.getBedId());
        dto.setBedName(firstNonBlank(row.getBedName(), overlayBedName));
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(firstNonBlank(row.getOrganizationName(), overlayOrgName));
        dto.setPriorityEnum(priorityEnum);
        dto.setStartTime(row.getStartTime());
        // 入科时间：读模型当前未投影独立列，与护士站列表口径一致返回 null。
        dto.setAdmissionTime(null);
        dto.setTotalAmount(row.getTotalAmount());
        dto.setPaidAmount(row.getPaidAmount());
        dto.setArrearsAmount(row.getArrearsAmount());
        dto.setAttendingDoctorName(row.getAttendingDoctorName());
        dto.setPrimaryNurseName(row.getPrimaryNurseName());
        dto.setDiagnosisList(diagnosisList);
        dto.setDiagnosisSummary(diagnosisSummary);
        dto.setPinyinCode(enrichmentPinyin);
        dto.setWubiCode(enrichmentWubi);
        return dto;
    }

    /**
     * 已装配列表行 + Spring 分页 → 分页响应（IF-IP-DOR-01 Post / B.10.11）。
     *
     * <p>实现要点：{@code rows} 允许为 null，由 {@link PagedResultBuilder} 归一为空列表；
     * {@code pageNo} 在 Builder 内完成 0-based → 1-based 换算。</p>
     *
     * @param rows 当前页已装配 DTO 列表，允许为 null
     * @param page 读模型 Spring 分页对象，允许为 null（返回空分页壳）
     * @return 非 null 分页响应
     */
    @Override
    public PagePatientQueueResponse toPagePatientQueueResponse(
        List<DoctorStationPatientQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page) {
        return PagedResultBuilder.build(new PagePatientQueueResponse(), rows, page);
    }
}
