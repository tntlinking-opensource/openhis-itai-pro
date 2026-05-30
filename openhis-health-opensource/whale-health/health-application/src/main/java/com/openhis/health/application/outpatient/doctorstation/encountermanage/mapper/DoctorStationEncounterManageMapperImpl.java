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

package com.openhis.health.application.outpatient.doctorstation.encountermanage.mapper;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.CompleteOutpatientEncounterRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.MarkOutpatientLeaveRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.PageOutpatientInfoForDoctorStationRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.StartOutpatientReceptionRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.CompleteOutpatientEncounterResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.MarkOutpatientLeaveResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.PageOutpatientInfoForDoctorStationResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.StartOutpatientReceptionResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.dto.OutpatientInfoItemDto;
import com.openhis.health.application.common.converter.TextValueConverter;
import com.openhis.health.domain.administration.encounter.command.CompleteOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.MarkOutpatientLeaveCommand;
import com.openhis.health.domain.administration.encounter.command.StartOutpatientReceptionCommand;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterListFinancialTotals;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DoctorStationEncounterManageMapper} 实现：医生站就诊管理应用层字段映射器。
 *
 * <p><b>职责</b>：将医生站就诊队列、接诊、暂离、完诊等 HTTP 请求装配为领域命令，
 * 将门诊信息读模型装配为列表/详情响应；不包含业务状态机推演。</p>
 *
 * <p><b>对应接口规格</b>：IF-DOR-01 ～ IF-DOR-04（医生站就诊队列、接诊、暂离、完诊）。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>队列查询默认状态并集（已挂号/在诊/暂离/诊毕）与医生站首页默认视图对齐，
 *       单状态筛选时按请求参数精确过滤。</li>
 *   <li>金额字段优先以收费汇总映射覆盖读模型快照，保证列表展示与账单事实一致。</li>
 *   <li>性别、就诊类别等枚举的码→展示文本转换使用 {@link EnumUtil}，与前端码表约定一致。</li>
 * </ul></p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationEncounterManageMapperImpl implements DoctorStationEncounterManageMapper {

    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    /**
     * 将医生站列表请求映射为读模型分页查询入参（IF-DOR-01 Pre）。
     *
     * @param request 应用请求（调用方保证非空）
     * @return 读模型查询 DTO；字段与 Q-OPI-001 对齐
     */
    @Override
    public OutpatientEncounterPageQuery toOutpatientEncounterPageQuery(PageOutpatientInfoForDoctorStationRequest request) {
        OutpatientEncounterPageQuery query = new OutpatientEncounterPageQuery();
        PageQueryBinder.bind(request, query);
        // 状态筛选分支：前端未显式筛选时，默认展示医生站首页「本科室全部候诊患者」并集。
        if (request.getEncounterStatus() == null) {
            // 医生站首页缺省：本科室下已挂号/在诊/暂离/诊毕并集，不按接诊医师过滤（与单队列「在诊」区分）。
            List<String> defaultStatuses = new ArrayList<>();
            defaultStatuses.add(EncounterStatus.REGISTERED.getCode());
            defaultStatuses.add(EncounterStatus.IN_PROGRESS.getCode());
            defaultStatuses.add(EncounterStatus.ON_HOLD.getCode());
            defaultStatuses.add(EncounterStatus.DISCHARGED.getCode());
            query.setStatusEnumIn(defaultStatuses);
            query.setStatusEnum(null);
            query.setDoctorId(null);
        } else {
            // 显式状态筛选：单状态精确匹配，仅在「在诊」时追加接诊医师过滤。
            query.setStatusEnumIn(null);
            query.setStatusEnum(request.getEncounterStatus().getCode());
            if (EncounterStatus.IN_PROGRESS.equals(request.getEncounterStatus())) {
                query.setDoctorId(request.getStaffPractitionerId());
            } else {
                query.setDoctorId(null);
            }
        }
        return query;
    }

    /**
     * 将读模型分页结果装配为医生站列表响应（IF-DOR-01 Asm；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数。</p>
     *
     * @param page 读模型分页（允许空页，不允 null Page 引用）
     * @return 列表响应；content 为空列表时表示无数据
     */
    @Override
    public PageOutpatientInfoForDoctorStationResponse toPageOutpatientInfoForDoctorStationResponse(
        Page<OutpatientEncounterContextQueryModel> page) {
        return this.toPageOutpatientInfoForDoctorStationResponse(page, null);
    }

    /**
     * 将读模型分页与收费汇总映射为医生站列表响应；见接口契约（IF-DOR-01 Asm；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数；Spring Data 页码换算由 {@link PagedResultBuilder} 完成。</p>
     *
     * @param page                        读模型分页
     * @param chargeTotalsByEncounterId  就诊 → 收费汇总，可为 null
     * @return 分页响应体
     */
    @Override
    public PageOutpatientInfoForDoctorStationResponse toPageOutpatientInfoForDoctorStationResponse(
        Page<OutpatientEncounterContextQueryModel> page,
        Map<Long, OutpatientEncounterListFinancialTotals> chargeTotalsByEncounterId) {
        List<OutpatientInfoItemDto> content = new ArrayList<>();
        for (OutpatientEncounterContextQueryModel outpatientInfoRow : page.getContent()) {
            // 先按读模型全量搬运，再在存在收费汇总时覆盖三列金额，避免 Asm 与账单事实脱节。
            OutpatientInfoItemDto listRowDto = this.toOutpatientInfoItemDto(outpatientInfoRow);
            if (chargeTotalsByEncounterId != null && outpatientInfoRow.getEncounterId() != null) {
                OutpatientEncounterListFinancialTotals chargeTotals =
                    chargeTotalsByEncounterId.get(outpatientInfoRow.getEncounterId());
                if (chargeTotals != null) {
                    listRowDto.setTotalAmount(chargeTotals.getTotalAmount());
                    listRowDto.setPaidAmount(chargeTotals.getPaidAmount());
                    listRowDto.setRefundAmount(chargeTotals.getRefundAmount());
                }
            }
            content.add(listRowDto);
        }
        return PagedResultBuilder.build(new PageOutpatientInfoForDoctorStationResponse(), content, page);
    }

    /**
     * 读模型行 → 列表行 DTO。
     *
     * @param row 读模型行；null 时返回 null
     * @return 列表 DTO 或 null
     */
    @Override
    public OutpatientInfoItemDto toOutpatientInfoItemDto(OutpatientEncounterContextQueryModel row) {
        // 空读模型防御：上游分页可能包含 null 行（如左关联结果），直接返回 null 由调用方过滤。
        if (row == null) {
            return null;
        }
        OutpatientInfoItemDto dto = new OutpatientInfoItemDto();
        // 就诊标识与业务号
        dto.setEncounterId(row.getEncounterId());
        dto.setEncounterBusNo(row.getEncounterBusNo());
        // 就诊类别与状态：类别码转整型展示，状态使用读模型已投影的码与文案。
        dto.setClassEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(EncounterClass.class, row.getClassEnum()));
        dto.setStatusEnum(row.getStatusEnum());
        dto.setStatusEnumText(row.getStatusEnumText());
        // 患者基本信息
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(row.getPatientName());
        dto.setGenderEnum(row.getGenderEnum());
        // 性别码转展示文本：读模型存 code，工作站列表需要中文性别。
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, row.getGenderEnum());
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        dto.setBirthDate(row.getBirthDate());
        dto.setAgeText(row.getAgeText());
        dto.setIdCard(row.getIdCard());
        dto.setPhone(row.getPhone());
        // 科室与医生信息
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(row.getOrganizationName());
        dto.setDoctorId(row.getDoctorId());
        dto.setDoctorName(row.getDoctorName());
        dto.setServiceTypeId(row.getServiceTypeId());
        dto.setServiceTypeName(row.getServiceTypeName());
        // 就诊时间线
        dto.setStartTime(row.getStartTime());
        dto.setReceptionTime(row.getReceptionTime());
        dto.setEndTime(row.getEndTime());
        dto.setDisplayOrder(row.getDisplayOrder());
        dto.setFirstEnumText(row.getFirstEnumText());
        // 财务快照（由读模型投影或外部收费汇总覆盖）
        dto.setTotalAmount(row.getTotalAmount());
        dto.setPaidAmount(row.getPaidAmount());
        dto.setRefundAmount(row.getRefundAmount());
        dto.setDiagnoses(doctorStationDiagnosisMapper.parseDiagnosisJson(row.getDiagnosisJson()));
        // 分诊信息
        dto.setTriageLevelEnum(TextValueConverter.toNullableInteger(row.getTriageLevelEnum()));
        dto.setTriageTime(row.getTriageTime());
        return dto;
    }

    /**
     * 接诊请求 → 领域命令（IF-DOR-02 Cmd 入参）。
     *
     * @param request 请求（非空）
     * @param doctorDisplayName 接诊医师展示名（应用层已解析主数据）
     * @return 接诊命令（单 item）
     */
    @Override
    public StartOutpatientReceptionCommand toStartOutpatientReceptionCommand(
        StartOutpatientReceptionRequest request,
        String doctorDisplayName
    ) {
        StartOutpatientReceptionCommand command = new StartOutpatientReceptionCommand();
        command.setOperatorId(request.getOperatorId());
        StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand item =
            new StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setDoctorId(request.getDoctorId());
        item.setDoctorName(doctorDisplayName);
        item.setReceptionTime(request.getReceptionTime());
        item.setDirectFromPlanned(request.getDirectFromPlanned());
        command.setItem(item);
        return command;
    }

    /**
     * 暂离请求 → 领域命令（IF-DOR-03）。
     *
     * @param request 请求（非空）
     * @return 暂离命令
     */
    @Override
    public MarkOutpatientLeaveCommand toMarkOutpatientLeaveCommand(MarkOutpatientLeaveRequest request) {
        MarkOutpatientLeaveCommand command = new MarkOutpatientLeaveCommand();
        command.setOperatorId(request.getOperatorId());
        MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand item =
            new MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setLeaveReason(request.getLeaveReason());
        item.setOccurredAt(request.getOccurredAt());
        command.setItem(item);
        return command;
    }

    /**
     * 完诊请求 → 领域命令（IF-DOR-04）。
     *
     * @param request 请求（非空）
     * @return 完诊命令
     */
    @Override
    public CompleteOutpatientEncounterCommand toCompleteOutpatientEncounterCommand(CompleteOutpatientEncounterRequest request) {
        CompleteOutpatientEncounterCommand command = new CompleteOutpatientEncounterCommand();
        command.setOperatorId(request.getOperatorId());
        CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand item =
            new CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setCompletedAt(request.getCompletedAt());
        item.setSummary(request.getSummary());
        command.setItem(item);
        return command;
    }

    /**
     * 接诊结果装配（IF-DOR-02 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success       是否成功
     * @return 响应体
     */
    @Override
    public StartOutpatientReceptionResponse toStartOutpatientReceptionResponse(Long encounterId, boolean success) {
        StartOutpatientReceptionResponse response = new StartOutpatientReceptionResponse();
        response.setEncounterId(encounterId);
        response.setSuccess(success);
        return response;
    }

    /**
     * 暂离结果装配（IF-DOR-03 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success     是否成功
     * @return 响应体
     */
    @Override
    public MarkOutpatientLeaveResponse toMarkOutpatientLeaveResponse(Long encounterId, boolean success) {
        MarkOutpatientLeaveResponse response = new MarkOutpatientLeaveResponse();
        response.setEncounterId(encounterId);
        response.setSuccess(success);
        return response;
    }

    /**
     * 完诊结果装配（IF-DOR-04 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success     是否成功
     * @return 响应体
     */
    @Override
    public CompleteOutpatientEncounterResponse toCompleteOutpatientEncounterResponse(Long encounterId, boolean success) {
        CompleteOutpatientEncounterResponse response = new CompleteOutpatientEncounterResponse();
        response.setEncounterId(encounterId);
        response.setSuccess(success);
        return response;
    }
}
