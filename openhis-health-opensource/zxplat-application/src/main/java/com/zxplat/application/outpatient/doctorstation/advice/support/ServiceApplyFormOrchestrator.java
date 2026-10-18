/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_PROOF;
import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_TEST;
import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_TREATMENT;
import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF;
import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST;
import static com.zxplat.domain.share.constants.ServiceRequestCategoryConstants.SERVICE_CATEGORY_TREATMENT;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.zxplat.application.shared.advice.ServiceRequestDetailListAssembler;
import com.zxplat.application.shared.medicaltech.MedicalTechSharedExecutingOrgSupport;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.zxplat.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
public class ServiceApplyFormOrchestrator {

    /** 诊疗申请写侧（create/remove draft）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 诊疗申请读侧（按就诊/单号/明细批量查询）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;

    /** 费用明细写侧（编辑删旧时作废未计费草稿）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 申请单号 {@code AR} 序列生成。 */
    private final BusinessSequenceService businessSequenceService;

    /** 活动定义对应产品定义批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 活动定义读侧（列表展示名称）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 科室/执行地点读侧（列表 orgName 解析）。 */
    private final LocationQueryService locationQueryService;

    /** 执行科室规则：申请单按行落各自执行科。 */
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;

    /** 共看组拆单键（院点 CT/DR 同单；未配置则按执行科拆）。 */
    private final MedicalTechSharedExecutingOrgSupport medicalTechSharedExecutingOrgSupport;

    /** 单位等术语 code→展示名批量解析。 */
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

    /** 保存申请单结果（可多张 AR；requestIds 与入参行对齐）。 */
    @Getter
    @Setter
    public static class SaveApplyFormResult {
        /** 首张申请单号（兼容旧客户端）。 */
        private String prescriptionNo;
        /** 本次全部申请单号（拆单组顺序）。 */
        private List<String> prescriptionNos = new ArrayList<>();
        /** 新建 ServiceRequest 主键，顺序与入参 lines 一致（耗材绑定按行挂靠）。 */
        private List<Long> requestIds = new ArrayList<>();
        /** 每行所属申请单号，与 requestIds 对齐。 */
        private List<String> requestPrescriptionNos = new ArrayList<>();
    }

    /**
     * 保存申请单：按执行科室拆多张 {@code AR}；共看组（如 CT/DR）同组共一张。
     *
     * <p>编辑时先删旧 DRAFT 行再重建。行上 {@code org_id} 仍为真实执行科；
     * Detail/部位样本随本行；耗材签发绑定按 requestIds 下标对齐，不串单。</p>
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param categoryEnum           请求类别（code 或枚举名）
     * @param formOrgId              整单执行科室（无规则行回落）
     * @param orderingOrgId          就诊开立科室（查执行科室规则）；可空则不按规则拆分执行科
     * @param encounterDiagnosisIds  整单绑定就诊诊断有序 ids 串；可空
     * @param previousPrescriptionNos 编辑时旧申请单号全集；新建可空/空列表；拆多张再保存须传齐
     * @param lines                   明细行
     * @param executionCount          整单执行次数回落值；行未传时使用；空或 ≤0 时回落为 1
     * @param operatorId              操作人
     * @param chargeDraftCreator      费用草稿创建回调；门诊注入实现，住院传 {@code null}
     * @return 新号列表与对齐的 requestId 列表
     * @throws BusinessException 旧号校验失败、行/Detail 校验失败或产品定义缺失
     */
    public SaveApplyFormResult saveApplyForm(
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long formOrgId,
        Long orderingOrgId,
        String encounterDiagnosisIds,
        List<String> previousPrescriptionNos,
        List<ServiceApplyFormLineSpec> lines,
        Integer executionCount,
        Long operatorId,
        ChargeDraftCreator chargeDraftCreator
    ) {
        // Gate：明细与整单执行科室为必填，否则无法形成合法申请单。
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单明细不能为空");
        }
        if (formOrgId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "整单执行科室不能为空");
        }
        // Pre：整单执行次数仅作行级回落；空或非法回落 1。
        Integer resolvedFormExecutionCount = resolveApplyFormExecutionCount(executionCount);
        // Pre：解析类别；编辑时须先删齐全部旧 DRAFT（含拆单兄弟 AR），再重建。
        String resolvedCategory = resolveApplyFormCategoryCode(categoryEnum);
        this.removePreviousDraftApplyForms(
            encounterId, previousPrescriptionNos, resolvedCategory, operatorId);
        // Pre：逐行解析执行科/次数，按拆单键分组（平台按执行科拆；共看组同键）
        List<PreparedApplyFormLine> preparedLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            ServiceApplyFormLineSpec line = lines.get(i);
            validateApplyFormLine(line);
            ProductDefinitionDisplayDto productRow = loadActivityProduct(line.getActivityDefinitionId());
            Integer lineExecutionCount =
                resolveLineExecutionCount(line.getExecutionCount(), resolvedFormExecutionCount);
            Long lineOrgId = this.resolveLineExecutingOrgId(orderingOrgId, formOrgId, productRow);
            PreparedApplyFormLine prepared = new PreparedApplyFormLine();
            prepared.line = line;
            prepared.productRow = productRow;
            prepared.lineOrgId = lineOrgId;
            prepared.lineExecutionCount = lineExecutionCount;
            prepared.originalIndex = i;
            preparedLines.add(prepared);
        }
        // Pre：按拆单键分桶——平台默认执行科各一键；共看组（如 CT/DR）同规范键合一张 AR；耗材不串单
        LinkedHashMap<Long, List<PreparedApplyFormLine>> groupsBySplitKey = new LinkedHashMap<>();
        for (PreparedApplyFormLine prepared : preparedLines) {
            Long splitKey = this.medicalTechSharedExecutingOrgSupport.resolveApplyFormSplitKey(prepared.lineOrgId);
            // 行无执行科时回落整单执行科，避免拆单键为空
            if (splitKey == null) {
                splitKey = formOrgId;
            }
            List<PreparedApplyFormLine> bucket = groupsBySplitKey.get(splitKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groupsBySplitKey.put(splitKey, bucket);
            }
            bucket.add(prepared);
        }
        // Cmd：每组一张 AR；requestIds 按入参下标回填，供耗材绑定对齐
        Long[] requestIdByIndex = new Long[preparedLines.size()];
        String[] prescriptionNoByIndex = new String[preparedLines.size()];
        List<String> prescriptionNos = new ArrayList<>();
        for (List<PreparedApplyFormLine> group : groupsBySplitKey.values()) {
            String prescriptionNo = businessSequenceService.getSeqByDay(
                AggregateTypeConstants.REQUISITION_APPLICATION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
            prescriptionNos.add(prescriptionNo);
            int sortNumber = 1;
            for (PreparedApplyFormLine prepared : group) {
                CreateServiceRequestDraftCommand cmd = buildCreateCommand(
                    encounterId,
                    patientId,
                    prepared.lineOrgId,
                    encounterDiagnosisIds,
                    prescriptionNo,
                    resolvedCategory,
                    prepared.line,
                    prepared.lineExecutionCount,
                    operatorId,
                    sortNumber++,
                    prepared.productRow);
                Long requestId = serviceRequestDomainService.createServiceRequestDraft(cmd);
                requestIdByIndex[prepared.originalIndex] = requestId;
                prescriptionNoByIndex[prepared.originalIndex] = prescriptionNo;
                if (chargeDraftCreator != null) {
                    chargeDraftCreator.createCharge(
                        encounterId,
                        patientId,
                        requestId,
                        prepared.line.getActivityDefinitionId(),
                        prepared.line.getQuantity(),
                        prepared.line.getUnitCode(),
                        prepared.productRow);
                }
            }
        }
        // Asm：对齐列表；首张号兼容旧字段
        SaveApplyFormResult result = new SaveApplyFormResult();
        result.setPrescriptionNos(prescriptionNos);
        if (!prescriptionNos.isEmpty()) {
            result.setPrescriptionNo(prescriptionNos.get(0));
        }
        List<Long> requestIds = new ArrayList<>();
        List<String> requestPrescriptionNos = new ArrayList<>();
        for (int i = 0; i < preparedLines.size(); i++) {
            requestIds.add(requestIdByIndex[i]);
            requestPrescriptionNos.add(prescriptionNoByIndex[i]);
        }
        result.setRequestIds(requestIds);
        result.setRequestPrescriptionNos(requestPrescriptionNos);
        return result;
    }

    /** 保存前预解析行：执行科、次数、入参下标。 */
    private static final class PreparedApplyFormLine {
        private ServiceApplyFormLineSpec line;
        private ProductDefinitionDisplayDto productRow;
        private Long lineOrgId;
        private Integer lineExecutionCount;
        private int originalIndex;
    }

    /**
     * 按开立科室 + 诊疗定义解析本行执行科室：命中规则取首条（单品优先于类型/大类）；无命中回落整单。
     *
     * @param orderingOrgId 就诊开立科室
     * @param formOrgId     整单执行科室回落
     * @param productRow    诊疗产品读模型（含大类/业务类型）
     * @return 本行应写入的 {@code ServiceRequest.org_id}
     */
    private Long resolveLineExecutingOrgId(
        Long orderingOrgId,
        Long formOrgId,
        ProductDefinitionDisplayDto productRow) {
        if (orderingOrgId == null || productRow == null || productRow.getProductDefinitionId() == null) {
            return formOrgId;
        }
        List<ExecutionRuleListQueryModel> rules =
            this.activityExecutionLocationQueryService.listExecutionRulesForActivityResolution(
                orderingOrgId,
                productRow.getProductDefinitionId(),
                productRow.getCategoryCode(),
                productRow.getActivityTypeCode());
        if (rules == null || rules.isEmpty()) {
            return formOrgId;
        }
        // 业务：排序已由 QueryService 保证单品优先；取首条非空执行科
        for (ExecutionRuleListQueryModel rule : rules) {
            if (rule != null && rule.getExecutionOrgId() != null) {
                return rule.getExecutionOrgId();
            }
        }
        return formOrgId;
    }

    /**
     * 按就诊与类别列出 {@code AR} 前缀申请单，按 {@code prescriptionNo} 分组。
     *
     * @param encounterId  就诊主键
     * @param categoryEnum 请求类别（code 或枚举名）
     * @return 申请单头列表；无匹配时空列表
     */
    public List<ServiceApplyFormHeaderModel> listApplyForms(Long encounterId, String categoryEnum) {
        // Pre：解析类别并拉取本就诊医生开立来源的全部 ServiceRequest 行。
        String resolvedCategory = resolveApplyFormCategoryCode(categoryEnum);
        List<ServiceRequestListModel> allRows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // Pre：仅保留 AR 前缀且类别与请求一致的 ServiceRequest 行。
        List<ServiceRequestListModel> matchedRows = new ArrayList<>();
        for (ServiceRequestListModel row : allRows) {
            if (row == null || row.getId() == null) {
                // Pre：脏读行跳过，避免 NPE 污染分组。
                continue;
            }
            if (!isRequisitionApplicationPrescriptionNo(row.getPrescriptionNo())) {
                // Pre：非申请单序列（非 AR 前缀）不参与本接口列表。
                continue;
            }
            if (!resolvedCategory.equals(trimToEmpty(row.getCategoryEnum()))) {
                // Pre：类别不一致的行不属于当前列表口径。
                continue;
            }
            matchedRows.add(row);
        }
        if (matchedRows.isEmpty()) {
            // Asm：无匹配行时直接返回空列表，避免后续批量查询。
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
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(serviceRequestIds);
        Map<Long, ActivityDefinition> activityById = new HashMap<>();
        for (ActivityDefinition def : activityDefinitionQueryService.findAllByIds(activityDefIds)) {
            if (def.getId() != null) {
                activityById.put(def.getId(), def);
            }
        }
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
                // Asm：执行科室名称来自批量 Location 解析，缺失时保持 null。
                header.setOrgName(orgNameById.get(first.getOrgId()));
            }
            LocalDateTime openedAt = first.getReqOpenedTime();
            if (openedAt == null) {
                openedAt = first.getReqAuthoredTime();
            }
            header.setReqOpenedTime(formatRequestInstantForApi(openedAt));
            header.setStatusSummary(buildStatusSummary(groupRows));
            List<ServiceApplyFormLineModel> lineDtos = new ArrayList<>();
            // Asm：组内每行投影为 IF 契约行 DTO，含明细摘要与单位展示名。
            for (ServiceRequestListModel row : groupRows) {
                ServiceApplyFormLineModel lineDto = new ServiceApplyFormLineModel();
                lineDto.setRequestId(row.getId());
                lineDto.setActivityDefinitionId(row.getActivityId());
                ActivityDefinition def = activityById.get(row.getActivityId());
                if (def != null) {
                    // Asm：活动定义存在时填充展示名，否则仅保留 id。
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
                lineDto.setPriorityEnum(row.getPriorityEnum());
                lineDto.setDetails(toDetailSummaryModels(detailsByRequestId.get(row.getId())));
                lineDtos.add(lineDto);
            }
            header.setLines(lineDtos);
            forms.add(header);
        }
        // Asm：整单按开立时间倒序，与医生站列表「最新在前」一致。
        forms.sort(Comparator.comparing(
            ServiceApplyFormHeaderModel::getReqOpenedTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return forms;
    }

    /**
     * 判断 {@code prescriptionNo} 是否为申请单序列（{@code AR} 前缀）。
     *
     * @param prescriptionNo 处方/申请单号
     * @return 是申请单号时 {@code true}
     */
    public static boolean isRequisitionApplicationPrescriptionNo(String prescriptionNo) {
        return isNotEmpty(prescriptionNo)
            && prescriptionNo.startsWith(AggregateTypeConstants.REQUISITION_APPLICATION);
    }

    /**
     * 列表聚邻排序键：{@code AR} 前缀申请单按整单号成组，其它处方号次之，无单号置后。
     *
     * <p>供医生站医嘱列表、护士站诊疗列表等同号多行聚邻展示（BR-NUR-015）。</p>
     *
     * @param prescriptionNo 处方/申请单号
     * @return 字典序比较用键
     */
    public static String resolveApplyFormGroupingSortKey(String prescriptionNo) {
        if (isRequisitionApplicationPrescriptionNo(prescriptionNo)) {
            return "0_" + prescriptionNo.trim();
        }
        if (isNotEmpty(prescriptionNo)) {
            return "1_" + prescriptionNo.trim();
        }
        return "2_";
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
            // Asm：无明细时不分配列表，与 IF 空数组语义一致。
            return List.of();
        }
        // Asm：逐条映射领域明细读模型为 supcontracts 层摘要（不含写路径逻辑）。
        List<ServiceRequestDetailSummaryModel> result = new ArrayList<>();
        for (ServiceRequestDetailListModel model : models) {
            if (model == null) {
                // Asm：单条脏数据跳过，不影响同组其它明细。
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
        String raw = trimToEmpty(categoryEnum);
        if (raw.isEmpty()) {
            // Gate：类别为空无法落库，与 IF 必填一致。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单类别不能为空");
        }
        if (SERVICE_CATEGORY_TREATMENT.equals(raw)
            || SERVICE_CATEGORY_PROOF.equals(raw)
            || SERVICE_CATEGORY_TEST.equals(raw)) {
            // Pre：已是持久化 code 时直接返回，避免重复映射。
            return raw;
        }
        // Pre：兼容枚举名入参（TREATMENT/PROOF/TEST），统一落库为 category code。
        switch (raw.toUpperCase()) {
            case CATEGORY_ENUM_NAME_TREATMENT:
                return SERVICE_CATEGORY_TREATMENT;
            case CATEGORY_ENUM_NAME_PROOF:
                return SERVICE_CATEGORY_PROOF;
            case CATEGORY_ENUM_NAME_TEST:
                return SERVICE_CATEGORY_TEST;
            default:
                // Gate：无法识别的类别拒绝，防止写入非法 category_enum。
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "不支持的申请单类别: " + categoryEnum);
        }
    }

    /**
     * 编辑删旧：先校验全部旧号均为可替换 DRAFT，再一并软删（避免只删首张留下拆单孤儿）。
     *
     * @param encounterId             就诊主键
     * @param previousPrescriptionNos 待替换旧申请单号；空则新建无删
     * @param expectedCategory        请求侧已解析的类别 code
     * @param operatorId              操作人
     * @throws BusinessException 任一旧号不存在、非 AR、含非草稿行或类别不一致
     */
    private void removePreviousDraftApplyForms(
        Long encounterId,
        List<String> previousPrescriptionNos,
        String expectedCategory,
        Long operatorId
    ) {
        List<String> uniqueNos = this.normalizePreviousPrescriptionNos(previousPrescriptionNos);
        // 新建：无旧号可删
        if (uniqueNos.isEmpty()) {
            return;
        }
        // Pre：先整批校验，再整批删除，避免半成功留下孤儿
        LinkedHashMap<String, List<Long>> requestIdsByPrescriptionNo = new LinkedHashMap<>();
        for (String previousPrescriptionNo : uniqueNos) {
            requestIdsByPrescriptionNo.put(
                previousPrescriptionNo,
                this.validatePreviousDraftApplyFormForReplace(
                    encounterId, previousPrescriptionNo, expectedCategory));
        }
        // Cmd：逐单作废未计费费用并软删 ServiceRequest（域内连带 Detail）
        for (Map.Entry<String, List<Long>> entry : requestIdsByPrescriptionNo.entrySet()) {
            List<Long> oldRequestIds = entry.getValue();
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, oldRequestIds);
            for (Long requestId : oldRequestIds) {
                serviceRequestDomainService.removeServiceRequestDraft(buildRemoveCommand(requestId, operatorId));
            }
        }
    }

    /**
     * 规范化旧申请单号：trim、去空、保序去重。
     *
     * @param previousPrescriptionNos 请求侧旧号列表；可空
     * @return 非空唯一号列表（可能为空）
     */
    private List<String> normalizePreviousPrescriptionNos(List<String> previousPrescriptionNos) {
        List<String> uniqueNos = new ArrayList<>();
        if (previousPrescriptionNos == null || previousPrescriptionNos.isEmpty()) {
            return uniqueNos;
        }
        Set<String> seen = new HashSet<>();
        for (String raw : previousPrescriptionNos) {
            // 空串跳过
            if (isEmpty(raw)) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }
            uniqueNos.add(trimmed);
        }
        return uniqueNos;
    }

    /**
     * 校验单张旧 AR 可被编辑替换，并返回其下全部草稿 requestId。
     *
     * @param encounterId            就诊主键
     * @param previousPrescriptionNo 待替换旧申请单号（已 trim）
     * @param expectedCategory       请求侧已解析的类别 code
     * @return 该单下待删 ServiceRequest 主键
     * @throws BusinessException 旧号不存在、非 AR、含非草稿行或类别不一致
     */
    private List<Long> validatePreviousDraftApplyFormForReplace(
        Long encounterId,
        String previousPrescriptionNo,
        String expectedCategory
    ) {
        List<ServiceRequestListModel> oldRows = serviceRequestQueryService.listByEncounterAndPrescriptionNo(
            encounterId, previousPrescriptionNo, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        if (oldRows.isEmpty()) {
            // Gate：编辑目标须存在，否则无法安全替换。
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
                // Gate：含已签发/执行中行不可编辑，须走撤销流程而非覆盖。
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "申请单含非草稿行，不可编辑: " + previousPrescriptionNo);
            }
            if (!expectedCategory.equals(trimToEmpty(row.getCategoryEnum()))) {
                // Gate：旧单类别须与本次请求一致，防止跨类别误删。
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "申请单类别与请求不一致: " + previousPrescriptionNo);
            }
            oldRequestIds.add(row.getId());
        }
        return oldRequestIds;
    }

    /**
     * 组装单条软删 ServiceRequest 草稿命令。
     *
     * @param requestId  待删 ServiceRequest 主键
     * @param operatorId 操作人
     * @return 领域软删命令
     */
    private static RemoveServiceRequestDraftCommand buildRemoveCommand(Long requestId, Long operatorId) {
        RemoveServiceRequestDraftCommand cmd = new RemoveServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item =
            new RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand();
        item.setId(requestId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 校验单行 Spec：数量/单位/Detail 白名单（检查部位、检验样本、治疗部位均选填）。
     *
     * @param line 明细行 Spec
     * @throws BusinessException 行级校验失败
     */
    private void validateApplyFormLine(ServiceApplyFormLineSpec line) {
        if (line == null || line.getActivityDefinitionId() == null) {
            // Gate：每行须绑定诊疗活动定义。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "诊疗定义主键不能为空");
        }
        BigDecimal quantity = line.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            // Gate：申请数量须为正数，零或负拒绝落库。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单数量必须大于 0");
        }
        if (isEmpty(line.getUnitCode())) {
            // Gate：计价单位必填，与产品定义校验口径一致。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "申请单单位不能为空");
        }
        List<ServiceApplyFormDetailItemSpec> detailItems = line.getDetailItems();
        if (detailItems == null) {
            detailItems = List.of();
        }
        for (ServiceApplyFormDetailItemSpec detail : detailItems) {
            if (detail == null) {
                // Gate：Detail 列表中的空元素跳过，不参与白名单校验。
                continue;
            }
            String targetTable = trimToEmpty(detail.getTargetTable());
            // Gate：Detail 目标表须在白名单内，防止任意表引用。
            if (!TableNameConstants.DIA_BODY_STRUCTURE.equals(targetTable)
                && !TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(targetTable)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "明细 targetTable 不在白名单内: " + detail.getTargetTable());
            }
            if (detail.getTargetId() == null) {
                // Gate：白名单表仍须有效 targetId，否则 Detail 无法关联。
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "明细 targetId 不能为空");
            }
        }
    }

    /**
     * 组装单条 ServiceRequest 草稿命令：整单 org/诊断/AR 号/类别写入 item，行级 quantity/remark/detail 来自 Spec。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param orgId                  整单执行科室
     * @param encounterDiagnosisIds  整单就诊诊断有序 ids 串；可空
     * @param prescriptionNo         新 AR 申请单号
     * @param categoryCode           已解析类别 code
     * @param line                   明细行 Spec
     * @param executionCount         已解析的本行执行次数（≥1）
     * @param operatorId             操作人
     * @param sortNumber             组内排序号
     * @param productRow             活动对应产品定义（单位校验用）
     * @return 领域 create draft 命令
     */
    private CreateServiceRequestDraftCommand buildCreateCommand(
        Long encounterId,
        Long patientId,
        Long orgId,
        String encounterDiagnosisIds,
        String prescriptionNo,
        String categoryCode,
        ServiceApplyFormLineSpec line,
        Integer executionCount,
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
        item.setUnitCode(ServiceAdviceDraftCommandHelper.requireUnitCode(
            line.getUnitCode(), null, productRow));
        // Cmd：本行执行次数写入 ServiceRequest（护士站可执行次数上限）。
        item.setExecutionCount(executionCount);
        item.setCategoryEnum(categoryCode);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setOrgId(orgId);
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setPrescriptionNo(prescriptionNo);
        item.setSortNumber(sortNumber);
        item.setNote(line.getRemark());
        // Cmd：整单诊断绑定与 IF-DOR-14 诊疗草稿同源；可空表示不绑定。
        item.setEncounterDiagnosisIds(encounterDiagnosisIds);
        // Cmd：按项目加急（EM）；空则非急
        item.setPriorityEnum(line.getPriorityEnum());
        item.setDetailItems(mapDetailCommands(line.getDetailItems()));
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 解析整单执行次数回落值：空或 ≤0 回落为 1。
     *
     * @param executionCount 请求入参；可空
     * @return ≥1 的执行次数
     */
    private static Integer resolveApplyFormExecutionCount(Integer executionCount) {
        if (executionCount == null || executionCount <= 0) {
            return GeneralSharedConstants.ONE;
        }
        return executionCount;
    }

    /**
     * 解析本行执行次数：行级优先；空或非法时回落整单值。
     *
     * @param lineExecutionCount 行入参；可空
     * @param formExecutionCount 已解析的整单回落值（≥1）
     * @return ≥1 的本行执行次数
     */
    private static Integer resolveLineExecutionCount(Integer lineExecutionCount, Integer formExecutionCount) {
        if (lineExecutionCount != null && lineExecutionCount > 0) {
            return lineExecutionCount;
        }
        return formExecutionCount;
    }

    /**
     * Detail Spec 列表 → 领域 DetailItemCommand 列表（跳过 null 或无 targetId 项）。
     *
     * @param detailItems 行级 Detail Spec；可空
     * @return 领域命令明细列表；无有效项时空列表
     */
    private static List<CreateServiceRequestDraftCommand.DetailItemCommand> mapDetailCommands(
        List<ServiceApplyFormDetailItemSpec> detailItems
    ) {
        if (detailItems == null || detailItems.isEmpty()) {
            return List.of();
        }
        List<CreateServiceRequestDraftCommand.DetailItemCommand> commands = new ArrayList<>();
        for (ServiceApplyFormDetailItemSpec detail : detailItems) {
            if (detail == null || detail.getTargetId() == null) {
                // Cmd：无效 Detail 项不落库，与 validate 阶段口径一致。
                continue;
            }
            CreateServiceRequestDraftCommand.DetailItemCommand cmd = new CreateServiceRequestDraftCommand.DetailItemCommand();
            cmd.setTargetTable(trimToEmpty(detail.getTargetTable()));
            cmd.setTargetId(detail.getTargetId());
            cmd.setRemark(detail.getRemark());
            commands.add(cmd);
        }
        return commands;
    }

    /**
     * 按活动定义主键加载单条产品定义读模型。
     *
     * @param activityDefinitionId 活动定义主键
     * @return 产品定义展示 DTO
     * @throws BusinessException 产品定义行不存在
     */
    private ProductDefinitionDisplayDto loadActivityProduct(Long activityDefinitionId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityDefinitionId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityDefinitionId);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            // Pre：活动须有关联产品定义方可计价与校验单位。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "未找到诊疗活动产品定义行");
        }
        return row;
    }

    /**
     * 批量加载活动定义对应产品定义，供列表单位展示名解析。
     *
     * @param activityDefIds 活动定义主键集合；可空
     * @return 复合键 → 产品定义 DTO；无 id 时空 Map
     */
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
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 按组内各行状态汇总整单展示文案（全草稿 / 已签发 / 混合）。
     *
     * @param rows 同一 prescriptionNo 下的 ServiceRequest 行
     * @return 状态摘要文案
     */
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
            // Asm：组内均为 DRAFT 时展示「全草稿」，可继续编辑。
            return "全草稿";
        }
        if (allActive) {
            // Asm：组内均已签发时展示「已签发」，不可整单编辑覆盖。
            return "已签发";
        }
        // Asm：混合状态时提示含已签发行，避免误以为仍可编辑。
        return "含已签发";
    }

    /**
     * 将域内 LocalDateTime 格式化为 API 契约 ISO 本地时间字符串（开立/签发等业务时刻通用）。
     *
     * @param requestTime 开立或签发时刻；可空
     * @return ISO 格式字符串；null 输入返回 null
     */
    private static String formatRequestInstantForApi(LocalDateTime requestTime) {
        if (requestTime == null) {
            return null;
        }
        return GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER.format(requestTime);
    }
}
