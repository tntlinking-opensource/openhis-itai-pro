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

package com.openhis.health.module.common.assembler.advice;

import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormDetailItemSpec;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormHeaderModel;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormLineModel;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormLineSpec;
import com.openhis.health.module.common.assembler.advice.model.ServiceRequestDetailSummaryModel;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-21/22、IF-IP-DOR-23/24：检验/检查/治疗申请单保存与列表共享编排。
 *
 * <p>门诊/住院 AppService 仅负责 Gate 与 {@link ChargeDraftCreator} 注入；类别解析、Detail 白名单、
 * 编辑删旧取新号、按 {@code AR} 前缀分组列表等逻辑集中在本类。</p>
 */
@Component
@RequiredArgsConstructor
public class ServiceApplyFormOrchestrationSupport {

    private static final String CATEGORY_TREATMENT = "21";
    private static final String CATEGORY_PROOF = "22";
    private static final String CATEGORY_TEST = "23";
    private static final int DEFAULT_EXECUTION_COUNT = 1;

    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final LocationQueryService locationQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 费用草稿创建回调：由 AppService 注入 {@code createChargeIfPossible} 等价逻辑。
     */
    @FunctionalInterface
    public interface ChargeDraftCreator {
        /**
         * 为新建诊疗申请挂一笔费用草稿。
         *
         * @param encounterId          就诊主键
         * @param patientId            患者主键
         * @param serviceRequestId     服务申请主键
         * @param activityDefinitionId 活动定义主键
         * @param quantity             计价数量
         * @param unitCode             计价单位
         * @param productRow           产品读模型
         */
        void createCharge(
            Long encounterId,
            Long patientId,
            Long serviceRequestId,
            Long activityDefinitionId,
            BigDecimal quantity,
            String unitCode,
            ProductDefinitionDisplayDto productRow
        );
    }

    /** 保存申请单结果（新号与新建 requestId 列表）。 */
    @Getter
    @Setter
    public static class SaveApplyFormResult {
        /** 新申请单号（{@code AR} 前缀）。 */
        private String prescriptionNo;
        /** 本单新建 ServiceRequest 主键，顺序与入参 lines 一致。 */
        private List<Long> requestIds = new ArrayList<>();
    }

    /**
     * 保存整张申请单：编辑时删旧 DRAFT 行后一律取新 {@code AR} 号重建。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param categoryEnum           请求类别（code 或枚举名）
     * @param orgId                  整单执行科室
     * @param conditionDefinitionId  整单绑定诊断定义；可空，非空时写入该单全部 {@code ServiceRequest.condition_definition_id}
     * @param encounterDiagnosisId   整单绑定就诊诊断；可空，非空时与 {@code conditionDefinitionId} 成对落库 {@code encounter_diagnosis_id}
     * @param previousPrescriptionNo 编辑时旧申请单号；新建可空
     * @param lines                  明细行
     * @param operatorId             操作人
     * @param chargeDraftCreator     费用草稿创建回调
     * @return 新号与新建 requestId 列表
     * @throws BusinessException 旧号校验失败、行/Detail 校验失败或产品定义缺失
     */
    public SaveApplyFormResult saveApplyForm(
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long orgId,
        Long conditionDefinitionId,
        Long encounterDiagnosisId,
        String previousPrescriptionNo,
        List<ServiceApplyFormLineSpec> lines,
        Long operatorId,
        ChargeDraftCreator chargeDraftCreator
    ) {
        // Gate：明细与整单执行科室为必填，否则无法形成合法申请单。
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单明细不能为空");
        }
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "整单执行科室不能为空");
        }
        // Pre：解析类别；编辑时须先删旧 DRAFT 行（含 Detail 与未计费费用）。
        String resolvedCategory = resolveApplyFormCategoryCode(categoryEnum);
        if (StringUtils.isNotBlank(previousPrescriptionNo)) {
            removePreviousDraftApplyForm(encounterId, previousPrescriptionNo.trim(), resolvedCategory, operatorId);
        }
        // Cmd：新建与编辑均取新 AR 号，逐行 createServiceRequestDraft 并由回调挂费草稿。
        String prescriptionNo = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.REQUISITION_APPLICATION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        List<Long> requestIds = new ArrayList<>();
        int sortNumber = 1;
        // 逐行校验、加载产品定义、写 ServiceRequest 与 ChargeItem 草稿（整单 org/诊断绑定透传至各行）。
        for (ServiceApplyFormLineSpec line : lines) {
            validateApplyFormLine(resolvedCategory, line);
            ProductDefinitionDisplayDto productRow = loadActivityProduct(line.getActivityDefinitionId());
            CreateServiceRequestDraftCommand cmd = buildCreateCommand(
                encounterId, patientId, orgId, conditionDefinitionId, encounterDiagnosisId,
                prescriptionNo, resolvedCategory, line, operatorId, sortNumber++, productRow);
            Long requestId = serviceRequestDomainService.createServiceRequestDraft(cmd);
            requestIds.add(requestId);
            chargeDraftCreator.createCharge(
                encounterId,
                patientId,
                requestId,
                line.getActivityDefinitionId(),
                line.getQuantity(),
                line.getUnitCode(),
                productRow);
        }
        SaveApplyFormResult result = new SaveApplyFormResult();
        result.setPrescriptionNo(prescriptionNo);
        result.setRequestIds(requestIds);
        return result;
    }

    /**
     * 按就诊与类别列出 {@code AR} 前缀申请单，按 {@code prescriptionNo} 分组。
     *
     * @param encounterId  就诊主键
     * @param categoryEnum 请求类别（code 或枚举名）
     * @return 申请单头列表
     */
    public List<ServiceApplyFormHeaderModel> listApplyForms(Long encounterId, String categoryEnum) {
        String resolvedCategory = resolveApplyFormCategoryCode(categoryEnum);
        List<ServiceRequestListModel> allRows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // Pre：仅保留 AR 前缀且类别与请求一致的 ServiceRequest 行。
        List<ServiceRequestListModel> matchedRows = new ArrayList<>();
        for (ServiceRequestListModel row : allRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo())) {
                continue;
            }
            if (!resolvedCategory.equals(StringUtils.trimToEmpty(row.getCategoryEnum()))) {
                continue;
            }
            matchedRows.add(row);
        }
        if (matchedRows.isEmpty()) {
            return List.of();
        }
        // Pre：收集主键与关联 id，批量加载明细、定义、科室与单位术语。
        List<Long> serviceRequestIds = new ArrayList<>();
        Set<Long> activityDefIds = new HashSet<>();
        Set<Long> orgIds = new HashSet<>();
        for (ServiceRequestListModel row : matchedRows) {
            serviceRequestIds.add(row.getId());
            if (row.getActivityId() != null) {
                activityDefIds.add(row.getActivityId());
            }
            if (row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
        }
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId =
            serviceRequestQueryService.mapDetailListModelsByServiceRequestIds(serviceRequestIds);
        Map<Long, ActivityDefinition> activityById =
            activityDefinitionQueryService.findAllByIds(activityDefIds).stream()
                .filter(def -> def.getId() != null)
                .collect(java.util.stream.Collectors.toMap(ActivityDefinition::getId, def -> def, (a, b) -> a));
        Map<Long, String> orgNameById = locationQueryService.mapNamesByIds(orgIds);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, ProductDefinitionDisplayDto> productByKey = batchLoadActivityProducts(activityDefIds);

        // Asm：按 prescriptionNo 分组，组内按 sortNumber 排序后装配头/行 Model。
        Map<String, List<ServiceRequestListModel>> grouped = new LinkedHashMap<>();
        for (ServiceRequestListModel row : matchedRows) {
            grouped.computeIfAbsent(row.getPrescriptionNo(), key -> new ArrayList<>()).add(row);
        }
        List<ServiceApplyFormHeaderModel> forms = new ArrayList<>();
        for (Map.Entry<String, List<ServiceRequestListModel>> entry : grouped.entrySet()) {
            List<ServiceRequestListModel> groupRows = new ArrayList<>(entry.getValue());
            groupRows.sort(Comparator
                .comparing(ServiceRequestListModel::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ServiceRequestListModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            ServiceRequestListModel first = groupRows.get(0);
            ServiceApplyFormHeaderModel header = new ServiceApplyFormHeaderModel();
            header.setPrescriptionNo(entry.getKey());
            header.setCategoryEnum(resolvedCategory);
            header.setOrgId(first.getOrgId());
            if (first.getOrgId() != null) {
                header.setOrgName(orgNameById.get(first.getOrgId()));
            }
            header.setAuthoredAt(formatAuthoredInstantForApi(first.getReqAuthoredTime()));
            header.setStatusSummary(buildStatusSummary(groupRows));
            List<ServiceApplyFormLineModel> lineDtos = new ArrayList<>();
            for (ServiceRequestListModel row : groupRows) {
                ServiceApplyFormLineModel lineDto = new ServiceApplyFormLineModel();
                lineDto.setRequestId(row.getId());
                lineDto.setActivityDefinitionId(row.getActivityId());
                ActivityDefinition def = activityById.get(row.getActivityId());
                if (def != null) {
                    lineDto.setActivityName(def.getName());
                }
                lineDto.setQuantity(row.getQuantity());
                lineDto.setUnitCode(row.getUnitCode());
                ProductDefinitionDisplayDto product = productByKey.get(
                    ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.WKF_ACTIVITY_DEFINITION, row.getActivityId()));
                lineDto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    unitLabelByCode, row.getUnitCode(), product));
                lineDto.setRemark(row.getRemark());
                lineDto.setDetails(toDetailSummaryModels(detailsByRequestId.get(row.getId())));
                lineDtos.add(lineDto);
            }
            header.setLines(lineDtos);
            forms.add(header);
        }
        forms.sort(Comparator.comparing(
            ServiceApplyFormHeaderModel::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return forms;
    }

    /**
     * 判断 {@code prescriptionNo} 是否为申请单序列（{@code AR} 前缀）。
     *
     * @param prescriptionNo 处方/申请单号
     * @return 是申请单号时 {@code true}
     */
    public static boolean isRequisitionApplicationPrescriptionNo(String prescriptionNo) {
        return StringUtils.isNotBlank(prescriptionNo)
            && prescriptionNo.startsWith(AggregateTypeConstants.REQUISITION_APPLICATION);
    }

    /**
     * 明细读模型 → 摘要 DTO。
     *
     * @param models 域明细行；可空
     * @return 摘要列表；无明细时空列表
     */
    public static List<ServiceRequestDetailSummaryModel> toDetailSummaryModels(
        List<ServiceRequestDetailListModel> models
    ) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        // 逐条映射领域明细读模型为 support 层摘要（不含写路径逻辑）。
        List<ServiceRequestDetailSummaryModel> result = new ArrayList<>();
        for (ServiceRequestDetailListModel model : models) {
            if (model == null) {
                continue;
            }
            ServiceRequestDetailSummaryModel dto = new ServiceRequestDetailSummaryModel();
            dto.setTargetTable(model.getTargetTable());
            dto.setTargetId(model.getTargetId());
            dto.setTargetDisplayName(model.getTargetDisplayName());
            dto.setRemark(model.getRemark());
            result.add(dto);
        }
        return result;
    }

    /**
     * 解析申请单类别 code：接受 {@code 21/22/23} 或 {@code TREATMENT/PROOF/TEST}（忽略大小写）。
     *
     * @param categoryEnum 请求类别
     * @return 持久化 category_enum code
     * @throws BusinessException 无法识别时抛出
     */
    public static String resolveApplyFormCategoryCode(String categoryEnum) {
        String raw = StringUtils.trimToEmpty(categoryEnum);
        if (raw.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单类别不能为空");
        }
        if (CATEGORY_TREATMENT.equals(raw) || CATEGORY_PROOF.equals(raw) || CATEGORY_TEST.equals(raw)) {
            return raw;
        }
        // 兼容枚举名入参（TREATMENT/PROOF/TEST），统一落库为 category code。
        switch (raw.toUpperCase()) {
            case "TREATMENT":
                return CATEGORY_TREATMENT;
            case "PROOF":
                return CATEGORY_PROOF;
            case "TEST":
                return CATEGORY_TEST;
            default:
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "不支持的申请单类别: " + categoryEnum);
        }
    }

    /**
     * 编辑删旧：校验旧号下全部为 DRAFT 后作废费用并软删 ServiceRequest（连带 Detail）。
     */
    private void removePreviousDraftApplyForm(
        Long encounterId,
        String previousPrescriptionNo,
        String expectedCategory,
        Long operatorId
    ) {
        List<ServiceRequestListModel> oldRows = serviceRequestQueryService.listByEncounterAndPrescriptionNo(
            encounterId, previousPrescriptionNo, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        if (oldRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "待替换的申请单不存在: " + previousPrescriptionNo);
        }
        // Gate：旧号须为 AR 前缀，且组内全部为 DRAFT 方可编辑替换。
        if (!isRequisitionApplicationPrescriptionNo(previousPrescriptionNo)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "待替换的申请单号须为 AR 前缀: " + previousPrescriptionNo);
        }
        List<Long> oldRequestIds = new ArrayList<>();
        for (ServiceRequestListModel row : oldRows) {
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "申请单含非草稿行，不可编辑: " + previousPrescriptionNo);
            }
            if (!expectedCategory.equals(StringUtils.trimToEmpty(row.getCategoryEnum()))) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "申请单类别与请求不一致: " + previousPrescriptionNo);
            }
            oldRequestIds.add(row.getId());
        }
        // Cmd：先作废未计费费用，再软删 ServiceRequest（域内连带 Detail）。
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
            encounterId, TableNameConstants.WKF_SERVICE_REQUEST, oldRequestIds);
        for (Long requestId : oldRequestIds) {
            serviceRequestDomainService.removeServiceRequestDraft(buildRemoveCommand(requestId, operatorId));
        }
    }

    private static RemoveServiceRequestDraftCommand buildRemoveCommand(Long requestId, Long operatorId) {
        RemoveServiceRequestDraftCommand cmd = new RemoveServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item =
            new RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand();
        item.setId(requestId);
        cmd.setItem(item);
        return cmd;
    }

    private void validateApplyFormLine(String categoryCode, ServiceApplyFormLineSpec line) {
        if (line == null || line.getActivityDefinitionId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "诊疗定义主键不能为空");
        }
        BigDecimal quantity = line.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单数量必须大于 0");
        }
        if (StringUtils.isBlank(line.getUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单单位不能为空");
        }
        List<ServiceApplyFormDetailItemSpec> detailItems = line.getDetailItems();
        if (detailItems == null) {
            detailItems = List.of();
        }
        boolean hasBodyStructure = false;
        boolean hasSpecimen = false;
        for (ServiceApplyFormDetailItemSpec detail : detailItems) {
            if (detail == null) {
                continue;
            }
            String targetTable = StringUtils.trimToEmpty(detail.getTargetTable());
            // Gate：Detail 目标表须在白名单内，防止任意表引用。
            if (!TableNameConstants.DIA_BODY_STRUCTURE.equals(targetTable)
                && !TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(targetTable)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "明细 targetTable 不在白名单内: " + detail.getTargetTable());
            }
            if (detail.getTargetId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "明细 targetId 不能为空");
            }
            if (TableNameConstants.DIA_BODY_STRUCTURE.equals(targetTable)) {
                hasBodyStructure = true;
            }
            if (TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(targetTable)) {
                hasSpecimen = true;
            }
        }
        // Gate：检查类每行至少一个部位；检验类每行至少一个样本。
        if (CATEGORY_TEST.equals(categoryCode) && !hasBodyStructure) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "检查申请每行至少需要一个部位");
        }
        if (CATEGORY_PROOF.equals(categoryCode) && !hasSpecimen) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "检验申请每行至少需要一个样本");
        }
    }

    /**
     * 组装单条 ServiceRequest 草稿命令：整单 org/诊断/AR 号/类别写入 item，行级 quantity/remark/detail 来自 Spec。
     */
    private CreateServiceRequestDraftCommand buildCreateCommand(
        Long encounterId,
        Long patientId,
        Long orgId,
        Long conditionDefinitionId,
        Long encounterDiagnosisId,
        String prescriptionNo,
        String categoryCode,
        ServiceApplyFormLineSpec line,
        Long operatorId,
        int sortNumber,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setActivityId(line.getActivityDefinitionId());
        item.setQuantity(line.getQuantity());
        item.setUnitCode(ServiceAdviceDraftCommandSupport.requireUnitCode(
            line.getUnitCode(), null, productRow));
        item.setExecutionCount(DEFAULT_EXECUTION_COUNT);
        item.setCategoryEnum(categoryCode);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setOrgId(orgId);
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setPrescriptionNo(prescriptionNo);
        item.setSortNumber(sortNumber);
        item.setNote(line.getRemark());
        // 整单诊断绑定：与 IF-DOR-14 诊疗草稿 condition_definition_id / encounter_diagnosis_id 同源；可空表示不绑定。
        item.setConditionDefinitionId(conditionDefinitionId);
        item.setEncounterDiagnosisId(encounterDiagnosisId);
        item.setDetailItems(mapDetailCommands(line.getDetailItems()));
        cmd.setItem(item);
        return cmd;
    }

    private static List<CreateServiceRequestDraftCommand.DetailItemCommand> mapDetailCommands(
        List<ServiceApplyFormDetailItemSpec> detailItems
    ) {
        if (detailItems == null || detailItems.isEmpty()) {
            return List.of();
        }
        List<CreateServiceRequestDraftCommand.DetailItemCommand> commands = new ArrayList<>();
        for (ServiceApplyFormDetailItemSpec detail : detailItems) {
            if (detail == null || detail.getTargetId() == null) {
                continue;
            }
            CreateServiceRequestDraftCommand.DetailItemCommand cmd = new CreateServiceRequestDraftCommand.DetailItemCommand();
            cmd.setTargetTable(StringUtils.trimToEmpty(detail.getTargetTable()));
            cmd.setTargetId(detail.getTargetId());
            cmd.setRemark(detail.getRemark());
            commands.add(cmd);
        }
        return commands;
    }

    private ProductDefinitionDisplayDto loadActivityProduct(Long activityDefinitionId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityDefinitionId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityDefinitionId);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "未找到诊疗活动产品定义行");
        }
        return row;
    }

    private Map<String, ProductDefinitionDisplayDto> batchLoadActivityProducts(Set<Long> activityDefIds) {
        if (activityDefIds == null || activityDefIds.isEmpty()) {
            return Map.of();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long id : activityDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private static String buildStatusSummary(List<ServiceRequestListModel> rows) {
        boolean allDraft = true;
        boolean allActive = true;
        for (ServiceRequestListModel row : rows) {
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                allDraft = false;
            }
            if (!RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                allActive = false;
            }
        }
        if (allDraft) {
            return "全草稿";
        }
        if (allActive) {
            return "已签发";
        }
        return "含已签发";
    }

    private static String formatAuthoredInstantForApi(LocalDateTime authoredTime) {
        if (authoredTime == null) {
            return null;
        }
        return GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER.format(authoredTime);
    }
}
