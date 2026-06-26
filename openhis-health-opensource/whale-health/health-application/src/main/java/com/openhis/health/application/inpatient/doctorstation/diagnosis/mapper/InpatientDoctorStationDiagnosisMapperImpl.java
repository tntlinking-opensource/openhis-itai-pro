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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.shared.diagnosis.DiagnosisJsonSnapshotParser;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisChildRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.DiagnosisKindPresentationUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link InpatientDoctorStationDiagnosisMapper} 实现：住院医生站诊断录入应用层字段映射（AS-IP-DOR-002）。
 *
 * <p><b>对应接口规格</b>：IF-IP-DOR-02（就诊诊断列表）～ IF-IP-DOR-06（删除诊断）。</p>
 *
 * <p><b>职责</b>：读模型 {@link FieldNameConstants#DIAGNOSIS_JSON} 快照解析、HTTP 请求/响应装配、
 * 保存/删除前 Gate、诊断事实与就诊关联领域命令搬运；不承载事务与领域状态机。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>JSON 键名统一经 {@link FieldNameConstants} 收口，与 {@code EncounterDomainService} 物化快照一致。</li>
 *   <li>住院读模型表仅投影 IMP 就诊，能加载快照即视为就诊类别合法（{@link #validateInpatientEncounterClassImp}）。</li>
 *   <li>可写状态仅 IN_PROGRESS / ON_HOLD（BR-IP-DOD-007）；患者疾病诊断类型码须合法（BR-IP-DOD-008）。</li>
 * </ul></p>
 */
@Component
public class InpatientDoctorStationDiagnosisMapperImpl implements InpatientDoctorStationDiagnosisMapper {

    /** 读模型 {@code diagnosisJson} 快照解析，与 bridge 展示路径共用同一解析器。 */
    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;

    /**
     * 构造：注入诊断快照 JSON 解析器。
     *
     * @param diagnosisJsonSnapshotParser 与 {@code module.shared.diagnosis} 共用的解析组件
     */
    public InpatientDoctorStationDiagnosisMapperImpl(DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser) {
        this.diagnosisJsonSnapshotParser = diagnosisJsonSnapshotParser;
    }

    /**
     * Gate：校验请求患者与读模型快照患者一致，防止串患者上下文。
     *
     * <p>实现要点：读模型缺失或患者 id 缺失时与「未选患者」同一用户提示，避免暴露内部 id 组合细节。</p>
     *
     * @param snapshot        住院信息读模型快照
     * @param requestPatientId 请求携带的患者标识
     * @throws com.openhis.whale.core.application.exception.BusinessException 不一致或上下文缺失时抛出（医生站口径 EX-IP-DOD-104）
     */
    @Override
    public void validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel snapshot, Long requestPatientId) {
        // 未加载到住院读模型行：医生站统一视为未选有效就诊，避免继续解析空快照。
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "请先选择患者");
        }
        // 请求未带患者：无法与读模型做交叉校验，直接阻断（与卡片上下文缺失同口径）。
        if (requestPatientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先选择患者");
        }
        // 读模型患者锚点必须与请求一致，避免在错误患者卡片上展示或写入诊断。
        if (!requestPatientId.equals(snapshot.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先选择患者");
        }
    }

    /**
     * 将读模型中的诊断快照 JSON 反序列化为列表。
     *
     * <p>实现要点：根节点非数组时返回空列表（兼容脏数据而不抛错）；单元素内缺键则对应 DTO 字段保持 null，
     * 由前端或后续保存逻辑处理必填项。</p>
     *
     * @param diagnosisJson 读模型 {@code diagnosisJson} 列原文；空白同空列表
     * @return 快照行列表，永不为 {@code null}
     * @throws com.openhis.whale.core.application.exception.BusinessException 解析异常时包装为医生站可读错误，不向外抛出原始 Jackson 栈
     */
    @Override
    public List<EncounterDiagnosisRowDto> parseDiagnosisJson(String diagnosisJson) {
        List<EncounterDiagnosisRowDto> result = new ArrayList<>();
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row
                : diagnosisJsonSnapshotParser.parseStrict(diagnosisJson)) {
            result.add(toEncounterDiagnosisRowDto(row));
        }
        return result;
    }

    private static EncounterDiagnosisRowDto toEncounterDiagnosisRowDto(
            DiagnosisJsonSnapshotParser.ParsedDiagnosisRow source) {
        EncounterDiagnosisRowDto dto = new EncounterDiagnosisRowDto();
        dto.setConditionId(source.getConditionId());
        dto.setEncounterDiagnosisId(source.getEncounterDiagnosisId());
        dto.setConditionDefinitionId(source.getConditionDefinitionId());
        dto.setConditionCode(source.getConditionCode());
        dto.setConditionDefinitionName(source.getConditionDefinitionName());
        dto.setDiagnosisKind(source.getDiagnosisKind());
        dto.setDiagnosisKindText(source.getDiagnosisKindText());
        dto.setVerificationStatusEnum(source.getVerificationStatusEnum());
        dto.setVerificationStatusEnumText(source.getVerificationStatusEnumText());
        dto.setMainDiagnosisFlag(source.getMainDiagnosisFlag());
        dto.setDiagnosisSortNo(source.getDiagnosisSortNo());
        dto.setMedicalTypeCode(source.getMedicalTypeCode());
        dto.setDiagnosisDesc(source.getDiagnosisDesc());
        dto.setIptDiseaseTypeCode(source.getIptDiseaseTypeCode());
        dto.setIptDiseaseTypeText(source.getIptDiseaseTypeText());
        dto.setYbNo(source.getYbNo());
        return dto;
    }

    /**
     * 将门诊包诊断行拷贝为住院包 DTO（字段一一对应；新读路径请直接用 {@link #parseDiagnosisJson}）。
     *
     * @param source 门诊侧诊断行，允许 null 或空
     * @return 住院侧行列表，永不为 null
     */
    @Override
    public List<EncounterDiagnosisRowDto> copyDiagnosisRows(
        List<com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto> source) {
        List<EncounterDiagnosisRowDto> target = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return target;
        }
        for (com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto row : source) {
            if (row == null) {
                continue;
            }
            EncounterDiagnosisRowDto copy = new EncounterDiagnosisRowDto();
            copy.setConditionId(row.getConditionId());
            copy.setEncounterDiagnosisId(row.getEncounterDiagnosisId());
            copy.setConditionDefinitionId(row.getConditionDefinitionId());
            copy.setConditionCode(row.getConditionCode());
            copy.setConditionDefinitionName(row.getConditionDefinitionName());
            copy.setDiagnosisKind(row.getDiagnosisKind());
            copy.setDiagnosisKindText(row.getDiagnosisKindText());
            copy.setVerificationStatusEnum(row.getVerificationStatusEnum());
            copy.setVerificationStatusEnumText(row.getVerificationStatusEnumText());
            copy.setMainDiagnosisFlag(row.getMainDiagnosisFlag());
            copy.setDiagnosisSortNo(row.getDiagnosisSortNo());
            copy.setMedicalTypeCode(row.getMedicalTypeCode());
            copy.setDiagnosisDesc(row.getDiagnosisDesc());
            copy.setIptDiseaseTypeCode(row.getIptDiseaseTypeCode());
            copy.setIptDiseaseTypeText(row.getIptDiseaseTypeText());
            copy.setYbNo(row.getYbNo());
            target.add(copy);
        }
        return target;
    }

    /**
     * 装配 IF-IP-DOR-02 响应（当前就诊诊断列表包壳）。
     *
     * <p>实现要点：{@code null} 入参按空列表处理，保证响应体字段非 null，前端表格无需判 null。</p>
     *
     * @param rows 诊断行集合，允许为 {@code null}
     * @return 非 null 响应，含 {@code diagnosisList} 与 {@code totalCount}
     */
    @Override
    public EncounterDiagnosisResponse toEncounterDiagnosisResponse(List<EncounterDiagnosisRowDto> rows) {
        EncounterDiagnosisResponse response = new EncounterDiagnosisResponse();
        if (rows == null) {
            response.setDiagnosisList(new ArrayList<>());
            response.setTotalCount(0);
            return response;
        }
        response.setDiagnosisList(rows);
        response.setTotalCount(rows.size());
        return response;
    }

    /**
     * 装配 IF-IP-DOR-03 响应（四类常用诊断树数据）。
     *
     * <p>实现要点：四类列表由上游已装配完毕，此处仅做属性赋值，不做防御性拷贝（调用方不应事后修改传入集合）。</p>
     *
     * @param patientHistoryList  病人历史诊断候选
     * @param doctorCommonUseList 医生个人模板展开后的候选
     * @param userPersonalList    登录用户个人模板候选
     * @param organizationList    当前科室模板候选
     * @return 聚合后的响应体
     */
    @Override
    public CommonDiagnosesResponse toCommonDiagnosesResponse(
        List<CommonDiagnosisRowDto> patientHistoryList,
        List<CommonDiagnosisRowDto> doctorCommonUseList,
        List<CommonDiagnosisRowDto> userPersonalList,
        List<CommonDiagnosisRowDto> organizationList,
        List<OptionDto> iptDiseaseTypeOptions) {
        CommonDiagnosesResponse response = new CommonDiagnosesResponse();
        response.setPatientHistoryList(patientHistoryList);
        response.setDoctorCommonUseList(doctorCommonUseList);
        response.setUserPersonalList(userPersonalList);
        response.setOrganizationList(organizationList);
        response.setIptDiseaseTypeOptions(iptDiseaseTypeOptions);
        return response;
    }

    /**
     * 将诊断中西医维度筛选入参解析为目录 {@code sourceEnum} 字符串集合。
     *
     * <p>实现要点：返回 {@code null} 表示查询层「不按 source 收敛」；与空列表不同，避免生成 {@code IN ()} 非法 SQL。</p>
     *
     * @param diagnosisKindCode {@code 1}（西医）/{@code 2}（中医）或空白
     * @return {@code null} 表示不按来源过滤；非 null 时为下推 SQL 的 IN 列表
     */
    @Override
    public List<String> parseDiagnosisKindSourceFilter(String diagnosisKindCode) {
        if (StringUtils.isBlank(diagnosisKindCode)) {
            return null;
        }
        String trimmed = diagnosisKindCode.trim();
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(trimmed)) {
            return new ArrayList<>(DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN);
        }
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(trimmed)) {
            return new ArrayList<>(DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM);
        }
        return null;
    }

    /**
     * 将读侧行模型装配为分页行（补充展示字段依赖外部传入字典映射）。
     *
     * <p>实现要点：目录来源展示依赖术语服务下发的码表映射；映射缺失时 {@code sourceEnumText} 可为 null，
     * 由前端决定是否回退为码值。中西医标签由 {@link #resolveDiagnosisKind} 与展示工具一致推导。</p>
     *
     * @param model               诊断定义读模型行
     * @param sourceDisplayByCode 目录来源码到展示文案的映射
     * @return 装配后的响应行；{@code model==null} 时为 {@code null}
     */
    @Override
    public ConditionDefinitionRowResponse toConditionDefinitionRowResponse(
        DoctorStationConditionDefinitionRowModel model,
        Map<String, String> sourceDisplayByCode) {
        if (model == null) {
            return null;
        }
        ConditionDefinitionRowResponse definitionRow = new ConditionDefinitionRowResponse();
        definitionRow.setConditionDefinitionId(model.getId());
        definitionRow.setConditionCode(model.getConditionCode());
        definitionRow.setConditionDefinitionName(model.getName());
        definitionRow.setPyStr(model.getPyStr());
        definitionRow.setWbStr(model.getWbStr());
        String diagnosisKind = this.resolveDiagnosisKind(model.getSourceEnum());
        definitionRow.setDiagnosisKind(diagnosisKind);
        definitionRow.setDiagnosisKindText(DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
        Integer sourceInt = this.tryParseInt(model.getSourceEnum());
        definitionRow.setSourceEnum(sourceInt);
        String sourceText = null;
        if (sourceDisplayByCode != null && model.getSourceEnum() != null) {
            sourceText = sourceDisplayByCode.get(model.getSourceEnum());
        }
        definitionRow.setSourceEnumText(sourceText);
        // 医保目录命中：仅当目录行明确 ybFlag=true 视为「是」，其余含 null 一律按未命中展示，避免歧义。
        if (Boolean.TRUE.equals(model.getYbFlag())) {
            definitionRow.setYbFlag(DoctorStationDiagnosisConstants.YB_FLAG_INT_YES);
            definitionRow.setYbFlagText(DoctorStationDiagnosisConstants.YB_FLAG_TEXT_YES);
        } else {
            definitionRow.setYbFlag(DoctorStationDiagnosisConstants.YB_FLAG_INT_NO);
            definitionRow.setYbFlagText(DoctorStationDiagnosisConstants.YB_FLAG_TEXT_NO);
        }
        // 分页接口仅展示已启用定义；非 ACTIVE 行理论上不会进入本装配，此处仍防御性清空 status 展示。
        if (PublicationStatus.ACTIVE.getCode().equals(model.getStatusEnum())) {
            Long sort = PublicationStatus.ACTIVE.getSortOrder();
            definitionRow.setStatusEnum(sort.intValue());
        } else {
            definitionRow.setStatusEnum(null);
        }
        return definitionRow;
    }

    /**
     * 装配分页包装（IF-IP-DOR-04 列表元数据 + 当前页行；B.10.11）。
     *
     * <p>使用 {@link PagedResultBuilder} 统一构建分页响应：
     * {@code page==null} 时返回空分页壳，避免 NPE；
     * Spring Data 0-based 页码换算仅在 Builder 内完成（B.10.11）。</p>
     *
     * @param page    读侧分页对象，允许为 {@code null}
     * @param records 已装配的当前页行列表
     * @return 非 null 分页响应
     */
    @Override
    public ConditionDefinitionPageResponse toConditionDefinitionPageResponse(
        Page<DoctorStationConditionDefinitionRowModel> page,
        List<ConditionDefinitionRowResponse> records) {
        List<ConditionDefinitionRowResponse> body;
        if (page == null) {
            // 读侧未返回分页壳：按空页处理，避免 Builder NPE。
            body = new ArrayList<>();
        } else if (records != null) {
            body = records;
        } else {
            // 调用方未传当前页行：用空列表占位，仍保留 page 元数据。
            body = List.of();
        }
        return PagedResultBuilder.build(new ConditionDefinitionPageResponse(), body, page);
    }

    /**
     * Gate：保存批次业务校验（非空、主诊唯一、排序唯一等）。
     *
     * <p>实现要点：主诊与排序唯一性为应用层门控，领域仍可能做二次校验；排序号 null 表示不参与去重校验。</p>
     *
     * @param request 全量保存请求
     * @throws com.openhis.whale.core.application.exception.BusinessException 批次为空、主诊多于 1 条或排序号重复时抛出
     */
    @Override
    public void validateSaveEncounterDiagnosisRequest(SaveEncounterDiagnosisRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "诊断批次不能为空");
        }
        if (request.getDiagnosisChildList() == null || request.getDiagnosisChildList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "诊断批次不能为空");
        }
        int mainCount = 0;
        Set<Integer> sortSeen = new HashSet<>();
        for (SaveEncounterDiagnosisChildRequest child : request.getDiagnosisChildList()) {
            if (child.getMainDiagnosisFlag() != null
                && child.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                mainCount++;
            }
            if (child.getDiagnosisSortNo() != null) {
                // add=false 表示该排序号已在批次内出现，继续保存会导致列表顺序语义混乱。
                if (!sortSeen.add(child.getDiagnosisSortNo())) {
                    throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "排序号不能重复");
                }
            }
        }
        if (mainCount > 1) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "主诊标记只能有一个");
        }
    }

    /**
     * 将保存请求装配为「诊断事实」领域批量命令。
     *
     * <p>实现要点：子项顺序与领域批量保存返回 id 顺序一致，后续关联命令依赖同一顺序，禁止在此处重排。</p>
     *
     * @param request    医生站保存批次
     * @param operatorId 当前操作人，写入命令审计字段
     * @return 待调用的 {@link SaveDoctorDiagnosisCommand}
     */
    @Override
    public SaveDoctorDiagnosisCommand toSaveDoctorDiagnosisCommand(SaveEncounterDiagnosisRequest request, Long operatorId) {
        SaveDoctorDiagnosisCommand cmd = new SaveDoctorDiagnosisCommand();
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setOperatorId(operatorId);
        List<SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand> items = new ArrayList<>();
        for (SaveEncounterDiagnosisChildRequest child : request.getDiagnosisChildList()) {
            SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand item = new SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand();
            item.setId(child.getConditionId());
            item.setDefinitionId(child.getConditionDefinitionId());
            item.setVerificationStatusEnum(this.resolveVerificationCode(child.getVerificationStatusEnum()));
            item.setYbNo(child.getYbNo());
            item.setDescription(child.getDiagnosisDesc());
            item.setCategoryEnum(child.getCategoryCode());
            item.setSeverityEnum(child.getSeverityCode());
            items.add(item);
        }
        cmd.setItems(items);
        return cmd;
    }

    /**
     * 在诊断事实保存成功后，装配「就诊诊断关联」批量 upsert 命令。
     *
     * <p>实现要点：{@code savedConditionIds.get(i)} 与 {@code diagnosisChildList.get(i)} 一一对应，含新建行在领域分配 id 后的顺序；
     * 条数不一致说明领域返回与请求严重偏离，必须阻断以免错挂就诊诊断关联。</p>
     *
     * @param request            与保存事实时相同的请求批次
     * @param savedConditionIds  与请求子项顺序一致的诊断事实 id 列表
     * @param operatorId         操作人
     * @return 就诊侧批量关联命令
     * @throws com.openhis.whale.core.application.exception.BusinessException 条数与请求不一致时抛出
     */
    @Override
    public BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertEncounterDiagnosisAssociationCommand(
        SaveEncounterDiagnosisRequest request,
        List<Long> savedConditionIds,
        Long operatorId) {
        if (request.getDiagnosisChildList().size() != savedConditionIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "诊断保存结果与入参条数不一致");
        }
        BatchUpsertEncounterDiagnosisAssociationCommand cmd = new BatchUpsertEncounterDiagnosisAssociationCommand();
        cmd.setOperatorId(operatorId);
        BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand batchItem =
            new BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand();
        batchItem.setEncounterId(request.getEncounterId());
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> diagnosisItems = new ArrayList<>();
        for (int i = 0; i < request.getDiagnosisChildList().size(); i++) {
            SaveEncounterDiagnosisChildRequest child = request.getDiagnosisChildList().get(i);
            Long conditionId = savedConditionIds.get(i);
            BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand d =
                new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
            d.setConditionId(conditionId);
            // 主诊标记：仅整型 1 视为主诊断，其余含 null 均写 false，与 Gate 主诊唯一规则一致。
            if (child.getMainDiagnosisFlag() != null
                && child.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                d.setPrimaryFlag(Boolean.TRUE);
            } else {
                d.setPrimaryFlag(Boolean.FALSE);
            }
            d.setRank(child.getDiagnosisSortNo());
            d.setDescription(child.getDiagnosisDesc());
            // 就诊诊断关联 med_type：未传时默认普通门诊（医保 med_type=11）；住院 diag_type_code 仍由 iptDiseaseTypeCode 写入。
            String medTypeCode = trimToNull(child.getMedicalTypeCode());
            if (medTypeCode == null) {
                medTypeCode = YbMedType.GENERAL_OUTPATIENT.getCode();
            } else if (EnumUtil.fromCode(YbMedType.class, medTypeCode) == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请重新选择医疗类别");
            }
            d.setMedType(medTypeCode);
            d.setDiagTypeCode(trimToNull(child.getIptDiseaseTypeCode()));
            // IF-IP-DOR-05 仅处理西医关联；中医病证组由 IF-IP-TCM-32 专管。
            d.setTcmFlag(Boolean.FALSE);
            diagnosisItems.add(d);
        }
        batchItem.setDiagnosisItems(diagnosisItems);
        cmd.setItem(batchItem);
        return cmd;
    }

    /**
     * 装配保存诊断后的接口响应（条数与 id 列表）。
     *
     * @param savedCount         成功持久化的诊断事实条数
     * @param savedConditionIds 与入参顺序一致的诊断事实主键列表
     * @return 非 null 响应
     */
    @Override
    public SaveEncounterDiagnosisResponse toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedConditionIds) {
        SaveEncounterDiagnosisResponse response = new SaveEncounterDiagnosisResponse();
        response.setSavedCount(savedCount);
        response.setSavedConditionIds(savedConditionIds);
        return response;
    }

    /**
     * Gate：删除单条就诊诊断前校验主键非空。
     *
     * @param conditionId 诊断事实 id
     * @param encounterId 就诊 id
     * @throws com.openhis.whale.core.application.exception.BusinessException 任一端主键为空时抛出
     */
    @Override
    public void validateDeleteEncounterDiagnosisRequest(Long conditionId, Long encounterId) {
        if (conditionId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "诊断标识不能为空");
        }
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
    }

    /**
     * 装配删除就诊诊断后的接口响应。
     *
     * @param success 是否视为成功（含幂等成功）
     * @param message 面向用户的提示文案
     * @return 非 null 响应
     */
    @Override
    public DeleteEncounterDiagnosisResponse toDeleteEncounterDiagnosisResponse(boolean success, String message) {
        DeleteEncounterDiagnosisResponse response = new DeleteEncounterDiagnosisResponse();
        response.setSuccess(success);
        response.setMessage(message);
        return response;
    }

    /**
     * 将诊断定义读模型行转为常用诊断快捷行（IF-IP-DOR-03 树节点）。
     *
     * <p>实现要点：常用树不展示验证状态等就诊级字段，仅保留定义锚点与中西医维度，减少 payload。</p>
     *
     * @param model               目录读模型行
     * @param sourceDisplayByCode 来源码到展示文案的映射
     * @return 常用行；{@code model==null} 时为 {@code null}
     */
    @Override
    public CommonDiagnosisRowDto toCommonDiagnosisRowDto(
        DoctorStationConditionDefinitionRowModel model,
        Map<String, String> sourceDisplayByCode) {
        if (model == null) {
            return null;
        }
        CommonDiagnosisRowDto row = new CommonDiagnosisRowDto();
        row.setConditionDefinitionId(model.getId());
        row.setConditionCode(model.getConditionCode());
        row.setConditionDefinitionName(model.getName());
        String diagnosisKind = this.resolveDiagnosisKind(model.getSourceEnum());
        row.setDiagnosisKind(diagnosisKind);
        row.setDiagnosisKindText(DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
        row.setYbNo(model.getYbNo());
        row.setSourceEnum(this.tryParseInt(model.getSourceEnum()));
        if (sourceDisplayByCode != null && model.getSourceEnum() != null) {
            row.setSourceEnumText(sourceDisplayByCode.get(model.getSourceEnum()));
        }
        return row;
    }

    /**
     * 将界面整数码映射为持久化验证状态编码串（FHIR Condition.verificationStatus 语义子集）。
     *
     * @param verificationStatusEnum 可为 null，表示前端未改验证态，按「已确认」落库以降低噪声
     * @return 非空编码，供 {@link com.openhis.health.domain.clinical.condition.service.ConditionDomainService} 持久化
     */
    private String resolveVerificationCode(Integer verificationStatusEnum) {
        if (verificationStatusEnum == null) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.UNCONFIRMED) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.UNCONFIRMED;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.PROVISIONAL) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.PROVISIONAL;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.DIFFERENTIAL) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.DIFFERENTIAL;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.REFUTED) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.REFUTED;
        }
        if (verificationStatusEnum == DoctorStationDiagnosisConstants.VerificationUiCode.ENTERED_IN_ERROR) {
            return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.ENTERED_IN_ERROR;
        }
        return DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED;
    }

    /**
     * 由诊断定义目录来源推导中西医维度标签（与快照 {@code diagnosisKind}、{@link DoctorStationDiagnosisConstants} 分组一致）。
     *
     * @param sourceEnumRaw 持久化来源串，多为数字串；不可解析为非数字时返回 null
     * @return {@link DoctorStationDiagnosisConstants#DIAGNOSIS_KIND_TAG_WESTERN} /
     *         {@link DoctorStationDiagnosisConstants#DIAGNOSIS_KIND_TAG_TCM} / {@code null}
     */
    private String resolveDiagnosisKind(String sourceEnumRaw) {
        Integer v = this.tryParseInt(sourceEnumRaw);
        if (v == null) {
            return null;
        }
        String asSourceCode = String.valueOf(v);
        if (DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN.contains(asSourceCode)) {
            return DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_WESTERN;
        }
        if (DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM.contains(asSourceCode)) {
            return DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM;
        }
        return null;
    }

    /**
     * 安全解析整数。
     *
     * @param raw 原始串
     * @return 整数或 null
     */
    private Integer tryParseInt(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 校验就诊类别为住院（IMP）。
     *
     * <p>{@link InpatientInfoReadModel} 表 {@code hisrm_inpatient_info} 仅投影 IMP 住院就诊，读模型无
     * {@code class_enum} 列；能由 {@code mustGetInpatientInfoByEncounterId} 加载到快照即视为 IMP。</p>
     *
     * @param snapshot 住院信息读模型快照
     */
    @Override
    public void validateInpatientEncounterClassImp(InpatientEncounterContextQueryModel snapshot) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "请先选择患者");
        }
        if (snapshot.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "请先选择患者");
        }
    }

    /**
     * 校验就诊处于医生站可写状态（IN_PROGRESS 或 ON_HOLD，BR-IP-DOD-007）。
     *
     * @param snapshot 住院信息读模型快照
     */
    @Override
    public void validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel snapshot) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "请先选择患者");
        }
        String statusCode = snapshot.getStatusEnum();
        // BR-IP-DOD-007：在院可写 = 在科(IN_PROGRESS) 或 留观/暂停(ON_HOLD)。
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            return;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            return;
        }
        // 已出院、待入科等状态禁止改诊断，避免与护士站流转冲突。
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前患者不可编辑诊断");
    }

    /**
     * 校验批次内非空患者疾病诊断类型码均合法（BR-IP-DOD-008）。
     *
     * @param request 保存诊断批次请求
     */
    @Override
    public void validateIptDiseaseTypeCodesInBatch(SaveEncounterDiagnosisRequest request) {
        if (request == null || request.getDiagnosisChildList() == null) {
            return;
        }
        for (SaveEncounterDiagnosisChildRequest child : request.getDiagnosisChildList()) {
            if (child == null) {
                continue;
            }
            String typeCode = trimToNull(child.getIptDiseaseTypeCode());
            if (typeCode == null) {
                continue;
            }
            IptPatientDiseaseDiagType parsed = EnumUtil.fromCode(IptPatientDiseaseDiagType.class, typeCode);
            if (parsed == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请重新选择诊断类型");
            }
        }
    }
}