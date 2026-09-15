/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.service;

import com.openhis.health.application.clinical.allergyhistory.assembler.PatientAllergyHistoryListAssembler;
import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryEnumOptionDto;
import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryRowDto;
import com.openhis.health.application.clinical.allergyhistory.mapper.PatientAllergyHistoryMapper;
import com.openhis.health.application.clinical.allergyhistory.request.DeletePatientAllergyHistoryRequest;
import com.openhis.health.application.clinical.allergyhistory.request.ListPatientAllergyHistoryRequest;
import com.openhis.health.application.clinical.allergyhistory.request.LoadPatientAllergyHistoryInitRequest;
import com.openhis.health.application.clinical.allergyhistory.request.SavePatientAllergyHistoryRequest;
import com.openhis.health.application.clinical.allergyhistory.request.SavePatientNkdaRequest;
import com.openhis.health.application.clinical.allergyhistory.response.DeletePatientAllergyHistoryResponse;
import com.openhis.health.application.clinical.allergyhistory.response.ListPatientAllergyHistoryResponse;
import com.openhis.health.application.clinical.allergyhistory.response.LoadPatientAllergyHistoryInitResponse;
import com.openhis.health.application.clinical.allergyhistory.response.SavePatientAllergyHistoryResponse;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveDeclaredAllergyRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveNkdaCommand;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceDomainService;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AllergyCategory;
import com.openhis.health.domain.share.enums.AllergyCriticality;
import com.openhis.health.domain.share.enums.AllergySeverity;
import com.openhis.health.domain.share.enums.AllergyVerificationStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 患者过敏史应用编排（门/住医生站与住院护士站共用）。
 *
 * <p>写入口仅主诉登记与 NKDA；皮试阳性由皮试保存路径进入档案。门诊护士不开放本服务填写页。</p>
 */
@Service
@RequiredArgsConstructor
public class PatientAllergyHistoryAppService {

    private final PatientAllergyHistoryMapper patientAllergyHistoryMapper;
    private final PatientAllergyHistoryListAssembler patientAllergyHistoryListAssembler;
    private final AllergyIntoleranceDomainService allergyIntoleranceDomainService;
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 加载过敏史表单枚举选项。
     *
     * @param request 可空请求体
     * @return 类别/验证/严重程度/危险程度选项
     * @throws BusinessException 未绑定医务人员时
     */
    public LoadPatientAllergyHistoryInitResponse loadPatientAllergyHistoryInit(
        LoadPatientAllergyHistoryInitRequest request) {
        // Gate：选项与就诊无关，仅要求已登录医务人员，避免匿名拉码表
        currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT,
            "当前登录未绑定医务人员");
        // Asm：四类枚举转为前端选项
        return patientAllergyHistoryMapper.toInitResponse(
            this.toEnumOptions(EnumUtil.toOptions(AllergyCategory.class)),
            this.toEnumOptions(EnumUtil.toOptions(AllergyVerificationStatus.class)),
            this.toEnumOptions(EnumUtil.toOptions(AllergySeverity.class)),
            this.toEnumOptions(EnumUtil.toOptions(AllergyCriticality.class)));
    }

    /**
     * 按患者查询过敏史（跨就诊；含阳性皮试与主诉、NKDA）。
     *
     * @param request 患者与当前就诊
     * @return 列表与摘要
     * @throws BusinessException 患者与就诊不一致或就诊不存在时
     */
    public ListPatientAllergyHistoryResponse listPatientAllergyHistory(ListPatientAllergyHistoryRequest request) {
        // Gate：就诊存在且与请求患者一致
        this.assertPatientEncounter(request.getPatientId(), request.getEncounterId());
        // Pre：按患者加载过敏史可见行
        List<AllergyIntoleranceRecordQueryModel> records =
            allergyIntoleranceQueryService.listPatientAllergyHistory(request.getPatientId());
        Map<Long, String> medicationNameById = this.loadMedicationNames(records);
        List<PatientAllergyHistoryRowDto> rows =
            patientAllergyHistoryListAssembler.assembleRows(records, medicationNameById);
        AllergyHistoryFlagQueryModel flag =
            allergyIntoleranceQueryService.getAllergyHistoryFlag(request.getPatientId(), request.getEncounterId());
        // Asm：摘要 + 行
        return patientAllergyHistoryMapper.toListResponse(flag, rows);
    }

    /**
     * 保存主诉过敏史。
     *
     * @param request 过敏原与就诊
     * @return 记录主键
     * @throws BusinessException 患者就诊不一致、过敏原名为空或领域校验失败时
     */
    public SavePatientAllergyHistoryResponse savePatientAllergyHistory(SavePatientAllergyHistoryRequest request) {
        // Gate：空体直接拒绝，避免 NPE 落到领域层
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "保存请求不能为空");
        }
        // Gate：就诊与患者一致，且当前会话绑定医务人员
        this.assertPatientEncounter(request.getPatientId(), request.getEncounterId());
        Long practitionerId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT,
            "当前登录未绑定医务人员");
        // Gate：过敏原名称是主诉登记的最低必填
        if (StringUtils.isEmpty(request.getDescription())) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "过敏原名称不能为空");
        }
        // Pre：请求转领域命令
        SaveDeclaredAllergyRecordCommand cmd =
            patientAllergyHistoryMapper.toSaveDeclaredCommand(request, practitionerId);
        // Cmd：主诉落库；有效过敏原会作废 NKDA
        Long allergyRecordId = allergyIntoleranceDomainService.saveDeclaredAllergyRecord(cmd);
        // Asm
        return patientAllergyHistoryMapper.toSaveResponse(allergyRecordId);
    }

    /**
     * 登记无已知过敏。
     *
     * @param request 患者与就诊
     * @return 记录主键
     * @throws BusinessException 仍有有效过敏原或就诊不一致时
     */
    public SavePatientAllergyHistoryResponse savePatientNkda(SavePatientNkdaRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "保存请求不能为空");
        }
        // Gate：就诊与患者一致
        this.assertPatientEncounter(request.getPatientId(), request.getEncounterId());
        Long practitionerId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT,
            "当前登录未绑定医务人员");
        // Pre：请求转 NKDA 命令
        SaveNkdaCommand cmd = patientAllergyHistoryMapper.toSaveNkdaCommand(request, practitionerId);
        // Cmd：存在有效过敏原时领域层拒绝
        Long allergyRecordId = allergyIntoleranceDomainService.saveNkda(cmd);
        // Asm
        return patientAllergyHistoryMapper.toSaveResponse(allergyRecordId);
    }

    /**
     * 删除主诉过敏史或 NKDA。
     *
     * @param request 记录与就诊
     * @return 是否成功
     * @throws BusinessException 记录不存在、皮试行或就诊不一致时
     */
    public DeletePatientAllergyHistoryResponse deletePatientAllergyHistory(DeletePatientAllergyHistoryRequest request) {
        if (request == null || request.getAllergyRecordId() == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "记录标识不能为空");
        }
        // Gate：就诊与患者一致
        this.assertPatientEncounter(request.getPatientId(), request.getEncounterId());
        Long operatorId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT,
            "当前登录未绑定医务人员");
        AllergyIntoleranceRecordQueryModel existing =
            allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
        // 禁止用他人患者上下文删除本条档案
        if (!request.getPatientId().equals(existing.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在");
        }
        // Pre / Cmd：皮试行拒绝由此删除
        DeleteSkinTestRecordCommand cmd =
            patientAllergyHistoryMapper.toDeleteCommand(request.getAllergyRecordId(), operatorId);
        allergyIntoleranceDomainService.deleteAllergyHistoryRecord(cmd);
        // Asm
        return patientAllergyHistoryMapper.toDeleteResponse(true);
    }

    /**
     * 校验请求患者属于指定就诊。
     *
     * @param patientId   请求患者
     * @param encounterId  请求就诊
     * @throws BusinessException 标识为空或患者不属于该就诊
     */
    private void assertPatientEncounter(Long patientId, Long encounterId) {
        // 无就诊上下文不允许读写患者级过敏档案
        if (patientId == null || encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "患者、就诊标识不能为空");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounter.getPatientId(),
            ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT,
            "患者与就诊不匹配");
    }

    /**
     * 将框架 OptionDto 转为过敏史枚举选项。
     *
     * @param source 框架选项
     * @return 过敏史选项
     */
    private List<PatientAllergyHistoryEnumOptionDto> toEnumOptions(List<OptionDto> source) {
        List<PatientAllergyHistoryEnumOptionDto> target = new ArrayList<>();
        if (source == null) {
            return target;
        }
        for (OptionDto option : source) {
            if (option == null) {
                continue;
            }
            target.add(patientAllergyHistoryMapper.toEnumOption(option.getValue(), option.getLabel()));
        }
        return target;
    }

    /**
     * 按皮试行药品 id 批量加载展示名。
     *
     * @param records 过敏史读模型
     * @return 药品 id → 名称
     */
    private Map<Long, String> loadMedicationNames(List<AllergyIntoleranceRecordQueryModel> records) {
        Map<Long, String> medicationNameById = new HashMap<>();
        if (records == null || records.isEmpty()) {
            return medicationNameById;
        }
        Set<Long> medicationIds = new LinkedHashSet<>();
        for (AllergyIntoleranceRecordQueryModel record : records) {
            if (record == null || record.getMedicationId() == null) {
                continue;
            }
            medicationIds.add(record.getMedicationId());
        }
        if (medicationIds.isEmpty()) {
            return medicationNameById;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (Long medicationId : medicationIds) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId);
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
                medicationNameById.put(medicationId, product.getProductName().trim());
            }
        }
        return medicationNameById;
    }
}
