/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.shared.converter.TextValueConverter;
import com.zxplat.application.shared.diagnosis.DiagnosisJsonSnapshotParser;
import com.zxplat.application.shared.diagnosis.DoctorStationWesternDiagnosisSourceSettingResolver;
import com.zxplat.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.OpspFilingDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.zxplat.application.outpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisChildRequest;
import com.zxplat.application.outpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.PatientOpspFilingDiagnosisResponse;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.zxplat.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.OpspDiseCodeDefinitionMatchQueryModel;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.enums.IptPatientDiseaseDiagType;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.utils.DiagnosisKindPresentationUtil;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link DoctorStationDiagnosisMapper} 实现：字段搬运与门诊信息读模型 {@link FieldNameConstants#DIAGNOSIS_JSON} 快照解析。
 *
 * <p>JSON 键名统一经 {@link FieldNameConstants} 收口，与 {@code EncounterDomainService} 物化快照保持一致。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationDiagnosisMapperImpl implements DoctorStationDiagnosisMapper {

    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;

    private final DoctorStationWesternDiagnosisSourceSettingResolver westernDiagnosisSourceSettingResolver;

    /**
     * Gate：校验请求患者与读模型快照患者一致，防止串患者上下文。
     *
     * <p>实现要点：读模型缺失或患者 id 缺失时与「未选患者」同一用户提示，避免暴露内部 id 组合细节。</p>
     *
     * @param snapshot        门诊信息读模型快照
     * @param requestPatientId 请求携带的患者标识
     * @throws com.zxplat.framework.core.application.exception.BusinessException 不一致或上下文缺失时抛出（医生站口径 EX-DOD-104）
     */
    @Override
    public void validateEncounterPatientGate(OutpatientEncounterContextQueryModel snapshot, Long requestPatientId) {
        // 未加载到门诊读模型行：医生站统一视为未选有效就诊，避免继续解析空快照。
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
     * @throws com.zxplat.framework.core.application.exception.BusinessException 解析异常时包装为医生站可读错误，不向外抛出原始 Jackson 栈
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

    /**
     * JSON 快照解析行 → 就诊诊断列表 DTO。
     *
     * @param source 解析结果
     * @return 列表行 DTO
     */
    private static EncounterDiagnosisRowDto toEncounterDiagnosisRowDto(
            DiagnosisJsonSnapshotParser.ParsedDiagnosisRow source) {
        EncounterDiagnosisRowDto dto = new EncounterDiagnosisRowDto();
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
        dto.setTcmSyndromeGroup(source.getTcmSyndromeGroup());
        dto.setSourceEnum(source.getSourceEnum());
        return dto;
    }

    /**
     * 装配 IF-DOR-06 响应（当前就诊诊断列表包壳）。
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
     * 装配 IF-DOR-06A 响应（四类常用诊断树数据）。
     *
     * <p>实现要点：四类列表由上游已装配完毕，此处仅做属性赋值，不做防御性拷贝（调用方不应事后修改传入集合）。</p>
     *
     * @param patientHistoryList  病人历史诊断候选
     * @param doctorCommonUseList 医生个人模板展开后的候选
     * @param userPersonalList    登录用户个人模板候选
     * @param organizationList    当前科室模板候选
     * @param medicalTypeOptions  门诊诊断类型下拉
     * @return 聚合后的响应体
     */
    @Override
    public CommonDiagnosesResponse toCommonDiagnosesResponse(
        List<CommonDiagnosisRowDto> patientHistoryList,
        List<CommonDiagnosisRowDto> doctorCommonUseList,
        List<CommonDiagnosisRowDto> userPersonalList,
        List<CommonDiagnosisRowDto> organizationList,
        List<OptionDto> medicalTypeOptions) {
        CommonDiagnosesResponse response = new CommonDiagnosesResponse();
        response.setPatientHistoryList(patientHistoryList);
        response.setDoctorCommonUseList(doctorCommonUseList);
        response.setUserPersonalList(userPersonalList);
        response.setOrganizationList(organizationList);
        response.setMedicalTypeOptions(medicalTypeOptions);
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
        if (StringUtils.isEmpty(diagnosisKindCode)) {
            return null;
        }
        String trimmed = diagnosisKindCode.trim();
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(trimmed)) {
            return westernDiagnosisSourceSettingResolver.resolveWesternSearchSources();
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
        Integer sourceInt = TextValueConverter.toNullableInteger(model.getSourceEnum());
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
        // 医保诊断编码：透传目录对码，供开诊弹窗列表展示；空串由前端显示「-」。
        definitionRow.setYbNo(model.getYbNo());
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
     * 装配分页包装（IF-DOR-07 列表元数据 + 当前页行；B.10.11）。
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
            // 读侧未分页时仍返回空壳，避免 Builder NPE。
            body = new ArrayList<>();
        } else if (records != null) {
            body = records;
        } else {
            // 有页对象但无行列表：按空页处理。
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
     * @throws com.zxplat.framework.core.application.exception.BusinessException 批次为空、主诊多于 1 条或排序号重复时抛出
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
     * 将保存请求装配为就诊诊断关联批量 upsert 命令（不经 Condition）。
     *
     * @param request    医生站保存批次
     * @param operatorId 当前操作人
     * @return 待调用的批量关联命令
     */
    @Override
    public BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertEncounterDiagnosisAssociationCommand(
        SaveEncounterDiagnosisRequest request,
        Long operatorId) {
        BatchUpsertEncounterDiagnosisAssociationCommand cmd = new BatchUpsertEncounterDiagnosisAssociationCommand();
        cmd.setOperatorId(operatorId);
        BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand batchItem =
            new BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand();
        batchItem.setEncounterId(request.getEncounterId());
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> diagnosisItems = new ArrayList<>();
        for (SaveEncounterDiagnosisChildRequest child : request.getDiagnosisChildList()) {
            BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand d =
                new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
            d.setEncounterDiagnosisId(this.resolveChildEncounterDiagnosisId(child));
            d.setConditionDefinitionId(child.getConditionDefinitionId());
            if (child.getMainDiagnosisFlag() != null
                && child.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                d.setPrimaryFlag(Boolean.TRUE);
            } else {
                d.setPrimaryFlag(Boolean.FALSE);
            }
            d.setRank(child.getDiagnosisSortNo());
            d.setDescription(child.getDiagnosisDesc());
            // 就诊诊断关联 med_type：未传时默认普通门诊（医保 med_type=11，BR-OP-DOD-010）。
            String medTypeCode = trimToNull(child.getMedicalTypeCode());
            if (medTypeCode == null) {
                medTypeCode = YbMedType.GENERAL_OUTPATIENT.getCode();
            } else if (EnumUtil.fromCode(YbMedType.class, medTypeCode) == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请重新选择医疗类别");
            }
            d.setMedType(medTypeCode);
            // 门诊就诊诊断类型固定为「门诊诊断」（ipt_dise_type_code=2，BR-OP-DOD-011）；不接受前端传入覆盖。
            d.setDiagTypeCode(IptPatientDiseaseDiagType.OUTPATIENT.getCode());
            // IF-DOR-08 仅处理西医关联；中医病证组由 IF-DOR-32 专管。
            d.setTcmFlag(Boolean.FALSE);
            diagnosisItems.add(d);
        }
        batchItem.setDiagnosisItems(diagnosisItems);
        cmd.setItem(batchItem);
        return cmd;
    }

    /**
     * 解析保存子项上的就诊诊断行主键。
     *
     * @param child 保存子项
     * @return 就诊诊断行主键；新建时为 null
     */
    private Long resolveChildEncounterDiagnosisId(SaveEncounterDiagnosisChildRequest child) {
        return child.getEncounterDiagnosisId();
    }

    /**
     * 装配保存诊断后的接口响应（条数与就诊诊断行 id 列表）。
     *
     * @param savedCount               成功持久化的诊断条数
     * @param savedEncounterDiagnosisIds 就诊诊断行主键列表
     * @return 非 null 响应
     */
    @Override
    public SaveEncounterDiagnosisResponse toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedEncounterDiagnosisIds) {
        SaveEncounterDiagnosisResponse response = new SaveEncounterDiagnosisResponse();
        response.setSavedCount(savedCount);
        response.setSavedEncounterDiagnosisIds(savedEncounterDiagnosisIds);
        return response;
    }

    /**
     * 解析删除请求中的就诊诊断行主键。
     *
     * @param request 删除请求
     * @return 行主键
     */
    @Override
    public Long resolveDeleteEncounterDiagnosisId(DeleteEncounterDiagnosisRequest request) {
        return request.getEncounterDiagnosisId();
    }

    /**
     * Gate：删除单条就诊诊断前校验主键非空。
     *
     * @param encounterDiagnosisId 就诊诊断行 id
     * @param encounterId          就诊 id
     * @throws com.zxplat.framework.core.application.exception.BusinessException 任一端主键为空时抛出
     */
    @Override
    public void validateDeleteEncounterDiagnosisRequest(Long encounterDiagnosisId, Long encounterId) {
        if (encounterDiagnosisId == null) {
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
     * 将诊断定义读模型行转为常用诊断快捷行（IF-DOR-06A 树节点）。
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
        row.setSourceEnum(TextValueConverter.toNullableInteger(model.getSourceEnum()));
        if (sourceDisplayByCode != null && model.getSourceEnum() != null) {
            row.setSourceEnumText(sourceDisplayByCode.get(model.getSourceEnum()));
        }
        return row;
    }

    /**
     * 将患者历史合并读侧行转为常用诊断快捷行（含中医病证组字段）。
     *
     * @param model               历史读侧行
     * @param sourceDisplayByCode 来源码到展示文案的映射
     * @return 常用行；{@code model==null} 时为 {@code null}
     */
    @Override
    public CommonDiagnosisRowDto toCommonDiagnosisRowDto(
        PatientHistoryCommonDiagnosisRow model,
        Map<String, String> sourceDisplayByCode) {
        if (model == null) {
            return null;
        }
        CommonDiagnosisRowDto row = new CommonDiagnosisRowDto();
        row.setConditionDefinitionId(model.getConditionDefinitionId());
        row.setSyndromeDefinitionId(model.getSyndromeDefinitionId());
        row.setConditionCode(model.getConditionCode());
        row.setConditionDefinitionName(model.getConditionDefinitionName());
        row.setDiagnosisKind(model.getDiagnosisKind());
        row.setDiagnosisKindText(DiagnosisKindPresentationUtil.toDisplay(model.getDiagnosisKind()));
        row.setSourceEnum(model.getSourceEnum());
        row.setYbNo(model.getYbNo());
        row.setTcmSyndromeGroup(model.getTcmSyndromeGroup());
        if (sourceDisplayByCode != null && model.getSourceEnumCode() != null) {
            row.setSourceEnumText(sourceDisplayByCode.get(model.getSourceEnumCode()));
        }
        return row;
    }

    /**
     * 由诊断定义目录来源推导中西医维度标签（与快照 {@code diagnosisKind}、{@link DoctorStationDiagnosisConstants} 分组一致）。
     *
     * @param sourceEnumRaw 持久化来源串，多为数字串；不可解析为非数字时返回 null
     * @return {@link DoctorStationDiagnosisConstants#DIAGNOSIS_KIND_TAG_WESTERN} /
     *         {@link DoctorStationDiagnosisConstants#DIAGNOSIS_KIND_TAG_TCM} / {@code null}
     */
    private String resolveDiagnosisKind(String sourceEnumRaw) {
        return DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(sourceEnumRaw);
    }

    /**
     * 装配 IF-DOR-06B 响应（CHS 门控标志 + 备案候选行）。
     *
     * @param opspFilingImportEnabled 是否启用慢病备案导入
     * @param filingList              备案行；可空
     * @return 响应
     */
    @Override
    public PatientOpspFilingDiagnosisResponse toPatientOpspFilingDiagnosisResponse(
        boolean opspFilingImportEnabled,
        List<OpspFilingDiagnosisRowDto> filingList) {
        PatientOpspFilingDiagnosisResponse response = new PatientOpspFilingDiagnosisResponse();
        response.setOpspFilingImportEnabled(opspFilingImportEnabled);
        // 未启用或上游未装配时返回空列表，避免前端判空失败。
        if (filingList == null) {
            response.setFilingList(new ArrayList<>());
            return response;
        }
        response.setFilingList(filingList);
        return response;
    }

    /**
     * 将 5302A 备案行与目录匹配结果装配为医生站候选行。
     *
     * @param filingLine  平台备案行
     * @param match       目录匹配；未命中可空
     * @param matchStatus 映射状态
     * @param matchHint   提示文案
     * @param valid       是否有效期内
     * @return 候选行
     */
    @Override
    public OpspFilingDiagnosisRowDto toOpspFilingDiagnosisRowDto(
        InsuranceOpspFilingLineSnapshot filingLine,
        OpspDiseCodeDefinitionMatchQueryModel match,
        String matchStatus,
        String matchHint,
        boolean valid) {
        OpspFilingDiagnosisRowDto row = new OpspFilingDiagnosisRowDto();
        // 备案行为空时仍返回空壳，避免 NPE；调用方应避免传入 null。
        if (filingLine != null) {
            row.setOpspDiseCode(filingLine.getOpspDiseCode());
            row.setOpspDiseName(filingLine.getOpspDiseName());
            row.setBeginDate(filingLine.getBegndate());
            row.setEndDate(filingLine.getEnddate());
            row.setMedicalTypeCode(filingLine.getMedicalTypeCode());
        }
        row.setValid(valid);
        row.setMatchStatus(matchStatus);
        row.setMatchHint(matchHint);
        // 映射成功时回填目录锚点，供勾选后走 IF-DOR-08。
        if (match != null) {
            row.setConditionDefinitionId(match.getConditionDefinitionId());
            row.setConditionCode(match.getConditionCode());
            row.setConditionDefinitionName(match.getName());
            row.setMatchField(match.getMatchField());
            row.setDiagnosisKind(resolveDiagnosisKind(match.getSourceEnum()));
        }
        return row;
    }
}
