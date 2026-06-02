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

package com.openhis.health.application.outpatient.doctorstation.diagnosis.service;

import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.*;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.*;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.application.querysupport.encounter.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 门诊医生站诊断录入编排：就诊诊断快照、常用树、定义检索与保存/删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationDiagnosisAppService {

    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ConditionTemplateQueryService conditionTemplateQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDomainService conditionDomainService;
    private final EncounterDomainService encounterDomainService;
    private final PractitionerQueryService practitionerQueryService;
    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CodeSystemQueryService codeSystemQueryService;
    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    /**
     * 查询当前就诊诊断列表（读快照）。
     *
     * @param request 就诊与患者上下文
     * @return 诊断列表
     * @throws BusinessException 就诊不存在、非门诊读模型、患者上下文不一致或快照解析失败
     */
    public EncounterDiagnosisResponse getEncounterDiagnosis(GetEncounterDiagnosisRequest request) {
        // 强依赖门诊信息读模型，缺失即视为未选择有效就诊（医生站统一口径 EX-DOD-104）。
        OutpatientEncounterContextQueryModel snapshot =
            this.encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // 校验患者上下文与读模型一致，避免串患者卡片展示诊断。
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // 医生站诊断页仅服务门诊/急诊门诊类别读模型行，防止住院行误入。
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())
            && !EncounterClass.EMER.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此加载门诊诊断列表");
        }
        // 实时扫表拼装诊断行，禁止仅依赖上下文 diagnosisJson 中转（读模型废止后 §四）。
        List<EncounterDiagnosisRowDto> rows =
            this.encounterDiagnosisSnapshotAssembler.buildRows(request.getEncounterId());
        return this.doctorStationDiagnosisMapper.toEncounterDiagnosisResponse(rows);
    }

    /**
     * 加载四类常用诊断快捷候选。
     *
     * <p><b>规模（B.2）</b>：「病人历史」分支对患者维度病情事实做扫描后去重再批量拉目录；
     * 长期就诊极多患者时单次请求成本偏高，后续若需收敛应增加时间窗或条数上限（须产品/规格确认）。</p>
     *
     * @param request 至少包含患者标识
     * @return 四类列表
     * @throws BusinessException 查询失败或上下文缺失
     */
    public CommonDiagnosesResponse getConditionDefinitionBusinessClass(GetCommonDiagnosesRequest request) {
        // 门控：术语展示依赖登录用户与科室上下文；缺失时仍允许返回空树，但医师维度无法解析。
        Long userId = this.currentUser.getCurrentUserId();
        Long orgId = this.currentUser.getCurrentOrganizationUnitId();
        // 解析登录用户到医师主数据，用于「医生常用」模板 owner 解析
        Practitioner practitioner = null;
        if (userId != null) {
            // 登录用户 → 医师主数据：用于「医生常用」模板 owner 解析；无绑定则 practitioner 保持 null。
            practitioner = this.practitionerQueryService.findByUserId(userId);
        }
        // 提取医师主键，无法解析时保持 null（医生常用模板返回空列表）
        Long practitionerId = null;
        if (practitioner != null) {
            practitionerId = practitioner.getId();
        }
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        // 病人历史诊断来自事实表去重后的定义集合（非读模型模板）。
        List<CommonDiagnosisRowDto> history = this.buildPatientHistoryCommonRows(request.getPatientId(), sourceLabels);
        // 医生常用模板：绑定对象按医师主键解释（若无法解析医师则返回空列表）。
        List<CommonDiagnosisRowDto> doctorRows = new ArrayList<>();
        if (practitionerId != null) {
            List<ConditionTemplateQueryModel> doctorTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                practitionerId, ConditionTemplateBinding.PERSONAL.getCode());
            doctorRows = this.mapTemplatesToCommonRows(doctorTemplates, sourceLabels);
        }
        // 用户个人模板（绑定 PERSONAL + 用户 id）。
        List<CommonDiagnosisRowDto> personalRows = new ArrayList<>();
        if (userId != null) {
            List<ConditionTemplateQueryModel> personalTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                userId, ConditionTemplateBinding.PERSONAL.getCode());
            personalRows = this.mapTemplatesToCommonRows(personalTemplates, sourceLabels);
        }
        // 科室模板（绑定 DEPARTMENT + 科室 id）。
        List<CommonDiagnosisRowDto> orgRows = new ArrayList<>();
        if (orgId != null) {
            List<ConditionTemplateQueryModel> orgTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            orgRows = this.mapTemplatesToCommonRows(orgTemplates, sourceLabels);
        }
        // 聚合四类列表为响应（仅读）。
        return this.doctorStationDiagnosisMapper.toCommonDiagnosesResponse(history, doctorRows, personalRows, orgRows);
    }

    /**
     * 分页检索已发布诊断定义。
     *
     * @param request 检索条件
     * @return 分页结果
     * @throws BusinessException 参数非法
     */
    public ConditionDefinitionPageResponse getConditionDefinitionMetadata(PageConditionDefinitionRequest request) {
        // 门控：诊断中西医维度筛选只允许 1/2/空，避免构造无法下推到 SQL 的非法 source 集合。
        if (StringUtils.isNotBlank(request.getDiagnosisKindCode())) {
            String code = request.getDiagnosisKindCode().trim();
            if (!DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(code)
                && !DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(code)) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断中西医维度筛选参数非法");
            }
        }
        // 将筛选入参解析为目录来源编码集合；null 表示不按来源收敛。
        List<String> sourceIn = this.doctorStationDiagnosisMapper.parseDiagnosisKindSourceFilter(request.getDiagnosisKindCode());
        // 分页检索仅返回已启用目录行，关键字同时命中名称/编码/拼音/五笔列。
        Page<DoctorStationConditionDefinitionRowModel> page = this.conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
            request.getKeyword(),
            sourceIn,
            request.getPageNo(),
            request.getPageSize());
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        List<ConditionDefinitionRowResponse> records = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            // 每行补充目录来源中文名、中西医标签等展示字段；无批量 API，仅内存装配 acceptable。
            ConditionDefinitionRowResponse definitionRow =
                this.doctorStationDiagnosisMapper.toConditionDefinitionRowResponse(model, sourceLabels);
            records.add(definitionRow);
        }
        // 装配分页元数据与行列表。
        return this.doctorStationDiagnosisMapper.toConditionDefinitionPageResponse(page, records);
    }

    /**
     * 医生保存诊断（先事实后关联，跨聚合编排）。
     *
     * @param request 全量批次
     * @return 保存结果
     * @throws BusinessException 参数/上下文/持久化失败
     */
    public SaveEncounterDiagnosisResponse saveDoctorDiagnosis(SaveEncounterDiagnosisRequest request) {
        // 门控：批次结构与主诊/排序等业务校验，失败则阻断写库。
        this.doctorStationDiagnosisMapper.validateSaveEncounterDiagnosisRequest(request);
        // 加载读模型快照并校验患者上下文，避免写入错误就诊下的诊断事实。
        OutpatientEncounterContextQueryModel snapshot =
            this.encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // 就诊类别门禁：仅允许门诊/急诊就诊保存诊断
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())
            && !EncounterClass.EMER.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此保存门诊诊断");
        }
        // 解析当前操作人主键，未登录时阻断写操作
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        SaveEncounterDiagnosisResponse response;
        try {
            SaveDoctorDiagnosisCommand saveCmd =
                this.doctorStationDiagnosisMapper.toSaveDoctorDiagnosisCommand(request, operatorId);
            List<Long> savedIds = this.conditionDomainService.saveDoctorDiagnosis(saveCmd);
            com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand assocCmd =
                this.doctorStationDiagnosisMapper.toBatchUpsertEncounterDiagnosisAssociationCommand(request, savedIds, operatorId);
            this.encounterDomainService.batchUpsertEncounterDiagnosisAssociation(assocCmd);
            response = this.doctorStationDiagnosisMapper.toSaveEncounterDiagnosisResponse(savedIds.size(), savedIds);
        } catch (BusinessException ex) {
            // 业务规则类失败原样上抛，便于前端展示具体错误码与文案。
            throw ex;
        } catch (RuntimeException ex) {
            // 非预期异常：打日志后统一包装，避免栈信息直出给患者端。
            log.warn("saveDoctorDiagnosis failed encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "保存失败，请稍后重试或联系管理员");
        }
        return response;
    }

    /**
     * 删除就诊诊断（先解除关联再软删事实）。
     *
     * @param request 诊断与就诊标识
     * @return 删除结果
     * @throws BusinessException 非幂等可恢复错误（如仍被医嘱引用）时抛出
     */
    public DeleteEncounterDiagnosisResponse delEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        // 门控：参数必填校验。
        this.doctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(request.getConditionId(), request.getEncounterId());
        // 读模型门控，避免未选择就诊即删除。
        OutpatientEncounterContextQueryModel snapshot =
            this.encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // 就诊类别门禁：仅允许门诊/急诊就诊删除诊断
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())
            && !EncounterClass.EMER.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此删除门诊诊断");
        }
        // 医嘱引用计数门控（当前仓储占位实现可能恒为 0，仍保留调用点以对齐 BR-DOD-001）。
        int adviceCnt = this.conditionQueryService.countAdviceRefByConditionId(request.getConditionId());
        // 诊断被医嘱引用时阻断删除，避免破坏医嘱关联完整性
        if (adviceCnt > 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "先撤销或调整相关医嘱后再删");
        }
        // 解析当前操作人主键
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        try {
            RemoveEncounterDiagnosisAssociationCommand rmCmd = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(request.getEncounterId());
            item.setConditionId(request.getConditionId());
            item.setRemoveReason("DOCTOR_STATION_DELETE");
            rmCmd.setItem(item);
            rmCmd.setOperatorId(operatorId);
            this.encounterDomainService.removeEncounterDiagnosisAssociation(rmCmd);
            DelEncounterDiagnosisCommand delCmd = new DelEncounterDiagnosisCommand();
            delCmd.setConditionId(request.getConditionId());
            delCmd.setOperatorId(operatorId);
            delCmd.setReason("DOCTOR_STATION_DELETE");
            this.conditionDomainService.delEncounterDiagnosis(delCmd);
        } catch (BusinessException ex) {
            // 幂等：重复删除时关联或事实可能已不存在，视为成功以避免前端抖动。
            if (ErrorCodeConstants.CONDITION_NOT_FOUND.equals(ex.getErrorCode())) {
                return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            if (ErrorCodeConstants.ENCOUNTER_NOT_FOUND.equals(ex.getErrorCode())) {
                return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("delEncounterDiagnosis failed conditionId={}", request.getConditionId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "删除失败，请稍后重试或联系管理员");
        }
        return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    /**
     * 构建目录来源码到展示文案映射（单次请求内复用）。
     *
     * @return 映射
     */
    private Map<String, String> buildSourceLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<OptionDto> options = this.codeSystemQueryService.getOptionListByCodeSystemName(
                TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE);
        if (options == null) {
            // 术语服务不可用时返回空映射：下游仍展示来源码，不阻断诊断检索主流程。
            return map;
        }
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                map.put(option.getValue(), option.getLabel());
            }
        }
        return map;
    }

    /**
     * 将模板绑定列表解析为常用诊断行（保持模板顺序，过滤无效定义）。
     *
     * @param templates    模板只读行
     * @param sourceLabels 来源展示映射
     * @return 常用行列表
     */
    private List<CommonDiagnosisRowDto> mapTemplatesToCommonRows(
        List<ConditionTemplateQueryModel> templates,
        Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        if (templates == null || templates.isEmpty()) {
            return rows;
        }
        List<Long> ids = new ArrayList<>();
        for (ConditionTemplateQueryModel template : templates) {
            if (template.getDefinitionId() != null) {
                ids.add(template.getDefinitionId());
            }
        }
        // 模板仅存 definitionId：一次性批量拉启用定义行，避免逐条查库。
        List<DoctorStationConditionDefinitionRowModel> defs = this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(ids);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (ConditionTemplateQueryModel template : templates) {
            DoctorStationConditionDefinitionRowModel def = byId.get(template.getDefinitionId());
            // 模板指向已停用/已删定义：静默跳过，避免常用树出现不可选脏数据。
            if (def == null) {
                continue;
            }
            CommonDiagnosisRowDto row = this.doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 基于患者历史诊断事实构造常用行（按定义去重，顺序保持首次出现）。
     *
     * @param patientId    患者
     * @param sourceLabels 来源展示映射
     * @return 常用行列表
     */
    private List<CommonDiagnosisRowDto> buildPatientHistoryCommonRows(Long patientId, Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        List<Condition> conditions = this.conditionQueryService.findByPatientId(patientId);
        Set<Long> seenDef = new LinkedHashSet<>();
        List<Long> orderedIds = new ArrayList<>();
        for (Condition condition : conditions) {
            if (condition.getDefinitionId() == null) {
                continue;
            }
            // LinkedHashSet 保序去重：同一定义多次就诊只保留首次出现顺序，符合「最近常用」直觉。
            if (seenDef.add(condition.getDefinitionId())) {
                orderedIds.add(condition.getDefinitionId());
            }
        }
        List<DoctorStationConditionDefinitionRowModel> defs = this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(orderedIds);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (Long definitionId : orderedIds) {
            DoctorStationConditionDefinitionRowModel def = byId.get(definitionId);
            if (def == null) {
                continue;
            }
            rows.add(this.doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels));
        }
        return rows;
    }
}
