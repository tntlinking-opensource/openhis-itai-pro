/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.openhis.health.application.shared.advice.AdviceOrderBindQueryLoader;
import com.openhis.health.application.shared.advice.AdviceOrderBindRowDto;
import com.openhis.health.application.shared.advice.SpecimenTubeMergeBindResolveSupport;
import com.openhis.health.application.shared.advice.ServiceRequestDetailListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteSlotDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.application.shared.advice.InpatientStandingOrderSplittableSettingResolver;
import com.openhis.health.application.shared.advice.LongTermFirstExecutionSlotSupport;
import com.openhis.health.application.shared.advice.StandingOrderOralPackageInventorySupport;
import com.openhis.health.application.shared.advice.StandingOrderPackageBatchPlanTimeResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行列表装配（IF-IP-EXE-01 Asm，BR-IP-EXE-005～013）。
 *
 * <p>核心编排步骤：</p>
 * <ol>
 *   <li>剔除无 rateCode 长期医嘱；转科/出院/护理类诊疗整单排除（与 ATD-03 同源，不经执行槽位）</li>
 *   <li>停嘱（STOPPED）仍展示，状态「已停嘱」（BR-IP-EXE-002/003）</li>
 *   <li>长期：经 {@code StandingOrderPackageBatchPlanTimeResolver} 频次展开（BR-IP-EXE-005）+ 可选一盒一点（BR-IP-EXE-035）；临时：整单按净 COMPLETED 归并（BR-IP-EXE-006）</li>
 *   <li>长期 Procedure 按 occurrence 归并；临时不按 occurrence 与开立时刻并列 pending → pending/executed/skipped/cancelled 四类槽位（BR-IP-EXE-007～009）</li>
 *   <li>净态判定：occurrence 组内 id 最大 + {@code CANCEL.refund_id}；执行-取消对冲净值为 hedged pending，同时展示于待执行/取消 Tab（BR-IP-EXE-008/014）</li>
 *   <li>批量挂载 bindItems：药嘱按用法→耗材，诊嘱按 activity→绑定模板（BR-IP-EXE-013）</li>
 *   <li>诊嘱批量挂载 packageChildren / serviceRequestDetails：供检查/检验申请单打印（列表页不展示）</li>
 *   <li>长期医嘱 bindItems 数量：批量回显上次 ORDER_EXECUTE 派生量（含取消软删，便于下次执行沿用护士手改）</li>
 *   <li>终态槽位批量回填操作人姓名（BR-IP-EXE-031；主执行人优先，否则 recorder）</li>
 *   <li>executeTab 收窄当前 Tab 槽位（医嘱行不变，BR-IP-EXE-010/012）；长嘱不可拆一盒一点按盒首锚点展示（BR-IP-EXE-035）</li>
 * </ol>
 *
 * <p>医嘱行展示字段委托 {@link NurseStationOrderViewListAssembler}。
 * 全局开关 {@code inpatientStandingOrderSplittable=0} 且满足资格（长期 + 包装≠拆零 + 口服类）时，
 * 本列表按包装切盒后仅回传<strong>盒首锚点</strong>（一盒一点，BR-IP-EXE-035）；写路径整包装量由
 * {@code NurseStationOrderExecuteAppService} 另行判定。出院门禁
 * {@link #hasLongTermStoppedPendingExecutionSlots} 与本列表 / 待执计数同源启用一盒一点与拆零下限切批
 * （BR-IP-ATD-011①′ / BR-IP-EXE-035/036），禁止再按可拆日点误拦已整包装执行的停嘱。</p>
 *
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteListReadAssembler {

    /** 医嘱查看列表行展示内核。 */
    private final NurseStationOrderViewListAssembler orderViewListAssembler;
    /** Procedure 批量加载。 */
    private final ProcedureQueryService procedureQueryService;
    /** 改量链与药嘱实体加载（BR-IP-EXE-038）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 医务人员姓名解析（终态槽位操作人展示）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 绑定项模板查询。 */
    private final AdviceOrderBindQueryLoader orderBindQueryLoader;
    /** 派生服务申请查询（长期绑定诊疗上次执行数量回显）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 派生耗材请求查询（长期绑定耗材上次执行数量回显）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 收费明细查询（长期带出量优先取上次 ORDER_EXECUTE 账单数量）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 中药 Tab 方级折叠。 */
    private final InpatientNurseStationTcmOrchestrator inpatientNurseStationTcmOrchestrator;
    /** 住院长嘱是否可拆全局开关。 */
    private final InpatientStandingOrderSplittableSettingResolver standingOrderSplittableSettingResolver;
    /** 长嘱可拆/一盒一点计划点解析（与待执计数同源）。 */
    private final StandingOrderPackageBatchPlanTimeResolver standingOrderPackageBatchPlanTimeResolver;
    /** 口服一盒一点剩余计数与按天抑制（BR-IP-EXE-038）。 */
    private final StandingOrderOralPackageInventorySupport standingOrderOralPackageInventorySupport;
    /** 药品目录批量加载（不可拆切盒取包装拆零比）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 用法术语码→展示名（一盒一点口服类资格判定）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 活动定义子项只读（套餐子项，检查/检验申请单打印用）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 服务申请明细只读装配（部位/标本展示名，申请单打印用）。 */
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;

    /** 标本合管设备反查（采血贴打印分组 enrich）。 */
    private final SpecimenTubeMergeBindResolveSupport specimenTubeMergeBindResolveSupport;

    /**
     * OrderInfo 骨架 + 域明细 → 按就诊分组且组内已排序的列表（含槽位与 bindItems）。
     *
     * @param skeleton           过滤后的读模型行
     * @param medByEncounter     就诊 → 药品明细
     * @param svcByEncounter     就诊 → 诊疗明细
     * @param executionDeadline  截止时间（计划点上界）
     * @param executeTab         执行 Tab
     * @param encounterOrgIds    就诊 → 机构主键（绑定模板预留）
     * @param medicineCategoryTab west / tcm
     * @param planTimeFrom       计划点下界（含）；空表示不下界过滤
     * @param medicineCategoryTab west / tcm / all
     * @param planTimeFrom 计划点下界（打印）；可空
     * @param executionRecordHistory true=执行记录事实行（含对冲前 COMPLETED）；false=护士站净态
     * @param includePrintDetails true=挂载检查/检验打印套餐与部位标本；列表页 false
     * @return 患者分组列表
     */
    public List<NurseStationOrderExecutePatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        LocalDateTime executionDeadline,
        String executeTab,
        Map<Long, Long> encounterOrgIds,
        String medicineCategoryTab,
        LocalDateTime planTimeFrom,
        boolean executionRecordHistory,
        boolean includePrintDetails) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext displayContext =
            orderViewListAssembler.prepareDisplayContext(index);
        // Pre：皮试药最新登记结果（列表药名后展示阴/阳性，与医生站 skinTestOutcome* 同源）
        orderViewListAssembler.enrichSkinTestOutcomes(displayContext, medByEncounter);
        // Pre：长期药品目录预热——院点一盒一点 + 产品拆零下限切批均需包装/拆零比与槽量换算
        boolean standingOrderSplittable = standingOrderSplittableSettingResolver.isSplittable();
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey =
            this.loadMedicationProductsForPackageBatch(index.getMedDetailById());
        // Pre：诊嘱套餐子项 + 申请明细仅打印路径需要；列表页跳过（§2.1 #9）
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId = Collections.emptyMap();
        Map<Long, List<ServiceRequestDetailSummaryDto>> detailsByServiceRequestId = Collections.emptyMap();
        if (includePrintDetails) {
            packageChildrenByActivityId = this.loadPackageChildrenByActivityId(index.getSvcDetailById().values());
            detailsByServiceRequestId = this.loadServiceRequestDetailSummaries(index.getSvcDetailById().values());
        }
        // Pre：当前页就诊下 Procedure 一次批量装载并按 request 锚点索引，禁逐行 getProcedureRecords
        Set<Long> encounterIdsForProcedure = new LinkedHashSet<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order != null && order.getEncounterId() != null) {
                encounterIdsForProcedure.add(order.getEncounterId());
            }
        }
        Map<String, List<ProcedureRecordQueryModel>> proceduresByAnchor =
            procedureQueryService.mapProcedureRecordsByRequestAnchorForEncounters(encounterIdsForProcedure);
        char procedureAnchorSeparator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        boolean pendingOnly = InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(
            trimToEmpty(executeTab));
        // Pre：bind 模板按 (bindType|sourceKey|orgId) 请求内缓存，禁逐行打库
        Map<String, List<AdviceOrderBindRowDto>> bindRowsCache = new HashMap<>();
        Map<Long, List<NurseStationOrderExecuteRowDto>> rowsByEncounter = new LinkedHashMap<>();
        try {
        // 逐 OrderInfo 骨架行装配：展示字段 + 四类槽位 + bindItems + Tab 过滤
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            // 剔除长期无频次、转科/出院/护理等特殊诊疗等不可执行行（皮试检查与其它诊疗同轨，不互斥）
            if (this.shouldExcludeExecuteRow(
                order, index.getMedDetailById(), index.getSvcDetailById())) {
                continue;
            }
            Long requestId = orderViewListAssembler.resolveRequestId(order);
            String requestTable = orderViewListAssembler.resolveRequestTable(order);
            List<ProcedureRecordQueryModel> procedures = Collections.emptyList();
            if (requestId != null && !StringUtils.isEmpty(requestTable)) {
                String anchor = requestTable + procedureAnchorSeparator + requestId;
                List<ProcedureRecordQueryModel> fromBatch = proceduresByAnchor.get(anchor);
                if (fromBatch != null) {
                    procedures = fromBatch;
                }
            }
            ServiceRequestListModel svcDetail = index.getSvcDetailById().get(requestId);
            PackageBatchExpandOutcome expandOutcome = this.mapOrderRow(
                order,
                index.getMedDetailById().get(requestId),
                svcDetail,
                displayContext,
                procedures,
                executionDeadline,
                encounterOrgIds,
                !standingOrderSplittable,
                medicationProductByKey,
                executionRecordHistory,
                pendingOnly,
                bindRowsCache);
            if (expandOutcome == null || expandOutcome.row == null) {
                continue;
            }
            NurseStationOrderExecuteRowDto line = expandOutcome.row;
            // Asm：诊嘱挂载套餐子项/部位标本（仅打印路径）
            if (includePrintDetails) {
                this.attachServiceRequestPrintDetails(
                    line, svcDetail, packageChildrenByActivityId, detailsByServiceRequestId);
            }
            // Tab 过滤：一盒一点锚点已按盒首≤deadline 选出；可选 planTimeFrom 收窄下界
            this.applyExecuteTabSlotFilter(line, executeTab, executionDeadline, planTimeFrom);
            // 待执行 Tab：过滤后无 pending 槽则不回传该行（省 bind/传输）
            if (pendingOnly) {
                List<NurseStationOrderExecuteSlotDto> pendingAfterFilter = line.getPendingSlots();
                if (pendingAfterFilter == null || pendingAfterFilter.isEmpty()) {
                    continue;
                }
            }
            List<NurseStationOrderExecuteRowDto> bucket = rowsByEncounter.get(order.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounter.put(order.getEncounterId(), bucket);
            }
            bucket.add(line);
        }
        // Asm：长期医嘱 bindItems 数量回显上次 ORDER_EXECUTE 派生量（护士手改后下次执行沿用）
        this.overlayLongTermBindQuantitiesFromLastExecution(rowsByEncounter);
        // Asm：终态槽位批量回填操作人姓名（待执行 Tab 无终态槽，跳过）
        if (!pendingOnly) {
            this.enrichTerminalSlotOperatorNames(rowsByEncounter.values());
        }
        } finally {
            // 请求结束清空频次展开 ThreadLocal，避免线程池复用脏缓存
            standingOrderPackageBatchPlanTimeResolver.clearRawExpandPointsCache();
        }
        // 按就诊分组，组内按开立时间倒序
        // 输出：每个 encounterId 一组，组内 orders 已排序
        List<NurseStationOrderExecutePatientGroupDto> groups = new ArrayList<>();
        Map<Long, MedicationRequestListQueryModel> medDetailById =
            inpatientNurseStationTcmOrchestrator.indexMedDetailById(medByEncounter);
        for (Map.Entry<Long, List<NurseStationOrderExecuteRowDto>> entry : rowsByEncounter.entrySet()) {
            List<NurseStationOrderExecuteRowDto> sorted = this.sortRows(entry.getValue());
            List<NurseStationOrderExecuteRowDto> collapsed =
                inpatientNurseStationTcmOrchestrator.collapseTcmPrescriptionDisplayRows(
                    sorted,
                    medDetailById,
                    medicineCategoryTab,
                    EXECUTE_TCM_ROW_ACCESSOR);
            NurseStationOrderExecutePatientGroupDto group = new NurseStationOrderExecutePatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(collapsed);
            groups.add(group);
        }
        return groups;
    }

    /**
     * 出院门禁/ATD-03：长期已停嘱是否仍有待执行槽位（BR-IP-ATD-011①′，与 Q-IP-EXE-001 槽位分桶同源）。
     *
     * <p>药嘱侧与 IF-IP-EXE-01 / BR-IP-NUR-005 一致：全局不可拆时启用一盒一点（BR-IP-EXE-035），
     * 否则仍可命中拆零下限切批（BR-IP-EXE-036）；禁止写死可拆日频次点导致整包装已执行仍报
     * {@code LONG_PENDING_SLOTS}。</p>
     *
     * @param medDetail           药品请求；非药嘱传 null
     * @param svcDetail           诊疗请求；非诊嘱传 null
     * @param procedures          该 request 下全部 Procedure 实体
     * @param executionDeadline   截止时刻（出院门禁通常为当前时刻）
     * @return true 表示仍有 pending 槽位，阻塞出院
     */
    public boolean hasLongTermStoppedPendingExecutionSlots(
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<Procedure> procedures,
        LocalDateTime executionDeadline) {
        List<LocalDateTime> pendingPlanTimes = this.listLongTermStoppedPendingPlanTimes(
            medDetail,
            svcDetail,
            procedures,
            executionDeadline);
        if (pendingPlanTimes == null || pendingPlanTimes.isEmpty()) {
            // 停嘱硬顶未满盒空解析、或时点均已终态：不拦出院
            return false;
        }
        return true;
    }

    /**
     * 出院门禁/ATD-03：长期已停嘱仍待执行的 {@code planTime} 列表（与 {@link #hasLongTermStoppedPendingExecutionSlots} 同源）。
     *
     * <p>仅回传门禁判定为 pending 的计划点；一盒一点/拆零下限切批后至多一个锚点。
     * 供出 Tab 展示与批量不执行勾选，禁止另算一套日频次点。</p>
     *
     * @param medDetail         药品请求；非药嘱传 null
     * @param svcDetail         诊疗请求；非诊嘱传 null
     * @param procedures        该 request 下全部 Procedure 实体
     * @param executionDeadline 截止时刻（出院门禁通常为当前时刻）
     * @return pending 计划点（升序）；无则空列表
     */
    public List<LocalDateTime> listLongTermStoppedPendingPlanTimes(
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<Procedure> procedures,
        LocalDateTime executionDeadline) {
        return this.listLongTermStoppedPendingPlanTimesFromRecords(
            medDetail,
            svcDetail,
            this.mapProceduresToRecordModels(procedures),
            executionDeadline);
    }

    /**
     * 出院门禁/ATD-03：临时嘱整单净态是否仍阻塞出院（BR-IP-ATD-011②，与 Q-IP-EXE-001 / BR-IP-EXE-006/008 同源）。
     *
     * <p>仅用于 {@code generate_source=doctor-prescription} 候选；护士划价（{@code nurse-pricing}）不在 ATD-03/05 范围。
     * 执行-取消对冲后净态为 hedged pending，须再次执行或登记不执行后方可出院。</p>
     *
     * @param procedures 该 request 下全部 Procedure 实体
     * @return true 表示净态仍为 pending 或 hedged pending
     */
    public boolean isTemporaryOrderNetPendingForDischarge(List<Procedure> procedures) {
        List<ProcedureRecordQueryModel> records = this.mapProceduresToRecordModels(procedures);
        OccurrenceNetSlotState state = this.resolveTemporaryNetSlotState(records);
        if (OccurrenceNetSlotState.PENDING.equals(state)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 出院门禁/ATD-03：长期已停嘱 pending 计划点（Procedure 读模型入参）。
     *
     * <p>Pre：药嘱预热目录与用法术语，再按全局可拆开关走与执行列表同源的
     * {@link #buildSlotBuckets}（含 BR-IP-EXE-035/036）。</p>
     *
     * @param medDetail         药品请求；非药嘱传 null
     * @param svcDetail         诊疗请求；非诊嘱传 null
     * @param procedures        该 request 下全部 Procedure 读模型
     * @param executionDeadline 截止时刻
     * @return pending 计划点；无则空列表
     */
    private List<LocalDateTime> listLongTermStoppedPendingPlanTimesFromRecords(
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline) {
        if (executionDeadline == null) {
            // 无截止：无法与执行列表分桶对齐，视为无 pending
            return Collections.emptyList();
        }
        InpatientOrderListSkeleton order = null;
        if (medDetail != null) {
            order = InpatientOrderListSkeleton.fromMedicationListModel(medDetail);
        } else if (svcDetail != null) {
            order = InpatientOrderListSkeleton.fromServiceListModel(svcDetail);
        }
        if (order == null) {
            // 非药非诊：出院门禁不拦未知类型
            return Collections.emptyList();
        }
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        String statusEnum = order.getStatusEnum();
        if (medDetail != null && StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
            statusEnum = medDetail.getStatusEnum();
        }
        if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
            statusEnum = svcDetail.getStatusEnum();
        }
        if (!TherapyType.LONG_TERM.getCode().equals(therapy)) {
            return Collections.emptyList();
        }
        if (!RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            // 未停嘱走 LONG_NOT_STOPPED，不在本方法收集时点
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> procedureRows = procedures;
        if (procedureRows == null) {
            procedureRows = Collections.emptyList();
        }
        // Pre：与 IF-IP-EXE-01 / BR-IP-NUR-005 同源——不可拆时一盒一点，目录供 035/036 切批
        boolean applyStandingOrderPackageBatch = !standingOrderSplittableSettingResolver.isSplittable();
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey = Collections.emptyMap();
        Map<String, String> methodLabelByCode = Collections.emptyMap();
        if (medDetail != null) {
            Map<Long, MedicationRequestListQueryModel> medById = new HashMap<>();
            Long requestId = medDetail.getId();
            if (requestId == null) {
                requestId = Long.valueOf(0L);
            }
            medById.put(requestId, medDetail);
            medicationProductByKey = this.loadMedicationProductsForPackageBatch(medById);
            methodLabelByCode = terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.METHOD_CODE);
        }
        SlotBuckets buckets = this.buildSlotBuckets(
            order,
            medDetail,
            svcDetail,
            procedureRows,
            executionDeadline,
            applyStandingOrderPackageBatch,
            medicationProductByKey,
            methodLabelByCode,
            false,
            true);
        // 与 IF-IP-EXE-01 同源：一盒一点/拆零下限剩余按天抑制（改量后仍够用不拦出院）
        if (medDetail != null
            && (buckets.packageBatchApplied || buckets.minSplitUnitBatchApplied)) {
            StandingOrderOralPackageInventorySupport.UnitClaimMode claimMode =
                StandingOrderOralPackageInventorySupport.UnitClaimMode.PACKAGE;
            if (buckets.minSplitUnitBatchApplied && !buckets.packageBatchApplied) {
                claimMode = StandingOrderOralPackageInventorySupport.UnitClaimMode.MIN_SPLIT;
            }
            NurseStationOrderExecuteRowDto suppressCarrier = new NurseStationOrderExecuteRowDto();
            suppressCarrier.setPendingSlots(buckets.pendingSlots);
            this.applyUnitClaimInventoryToExecuteRow(
                suppressCarrier,
                medDetail,
                procedureRows,
                applyStandingOrderPackageBatch,
                claimMode,
                medicationProductByKey,
                methodLabelByCode,
                executionDeadline);
            buckets.pendingSlots = suppressCarrier.getPendingSlots();
        }
        return this.collectPendingPlanTimes(buckets);
    }

    /**
     * 从与门禁同源的槽位分桶取出 pending 计划点（去掉空时刻）。
     *
     * @param buckets 四类槽位；可 null
     * @return pending planTime 列表；无则空列表
     */
    private List<LocalDateTime> collectPendingPlanTimes(SlotBuckets buckets) {
        if (buckets == null || buckets.pendingSlots == null || buckets.pendingSlots.isEmpty()) {
            return Collections.emptyList();
        }
        List<LocalDateTime> planTimes = new ArrayList<>();
        for (NurseStationOrderExecuteSlotDto slot : buckets.pendingSlots) {
            if (slot == null || slot.getPlanTime() == null) {
                continue;
            }
            planTimes.add(slot.getPlanTime());
        }
        return planTimes;
    }

    /**
     * Procedure 实体 → 执行列表槽位归并读模型（出院门禁批量 Procedure 复用）。
     *
     * @param procedures 同一 request 下 Procedure 实体列表
     * @return 读模型列表；入参为空时空列表
     */
    public List<ProcedureRecordQueryModel> mapProceduresToRecordModels(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> models = new ArrayList<>();
        for (Procedure row : procedures) {
            if (row == null) {
                continue;
            }
            ProcedureRecordQueryModel model = new ProcedureRecordQueryModel();
            model.setId(row.getId());
            model.setRequestId(row.getRequestId());
            model.setRequestTable(row.getRequestTable());
            model.setStatusEnum(row.getStatusEnum());
            model.setRecordedTime(row.getRecordedTime());
            model.setRefundId(row.getRefundId());
            model.setEncounterId(row.getEncounterId());
            model.setPatientId(row.getPatientId());
            model.setCategoryEnum(row.getCategoryEnum());
            model.setOccurrenceTime(row.getOccurrenceTime());
            model.setRecorderId(row.getRecorderId());
            model.setResultText(row.getResultText());
            models.add(model);
        }
        return models;
    }

    private static final InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<NurseStationOrderExecuteRowDto>
        EXECUTE_TCM_ROW_ACCESSOR = new InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<>() {
        @Override
        public Long getRequestId(NurseStationOrderExecuteRowDto row) {
            return row.getOrderId();
        }

        @Override
        public Long getEncounterId(NurseStationOrderExecuteRowDto row) {
            return row.getEncounterId();
        }

        @Override
        public String getOrderType(NurseStationOrderExecuteRowDto row) {
            return row.getOrderType();
        }

        @Override
        public Integer getSortNumber(NurseStationOrderExecuteRowDto row) {
            return null;
        }

        @Override
        public String getAdviceDisplayName(NurseStationOrderExecuteRowDto row) {
            return row.getAdviceDisplayName();
        }

        @Override
        public String getDosageDisplay(NurseStationOrderExecuteRowDto row) {
            return row.getDosageDisplay();
        }

        @Override
        public void applyTcmPrescriptionCollapse(
            NurseStationOrderExecuteRowDto representative,
            MedicationRequestListQueryModel repMed,
            List<Long> lineRequestIds,
            String adviceDisplayName,
            String dosageDisplay,
            LocalDateTime prescriptionOpenedTime) {
            representative.setTcmFlag(Boolean.TRUE);
            if (repMed != null) {
                representative.setPrescriptionNo(repMed.getPrescriptionNo());
                representative.setSufferingFlag(repMed.getSufferingFlag());
                representative.setChineseHerbsDoseQuantity(repMed.getChineseHerbsDoseQuantity());
            }
            representative.setLineRequestIds(lineRequestIds);
            representative.setAdviceDisplayName(adviceDisplayName);
            representative.setSpecificationText(null);
            representative.setDosageDisplay(dosageDisplay);
            // 方级开立时间覆盖为代表行展示用最晚时刻（BR-IP-NUR-004-TCM-002）
            if (prescriptionOpenedTime != null) {
                representative.setReqOpenedTime(prescriptionOpenedTime);
            }
        }
    };

    /**
     * 写路径「不执行」Gate：occurrence 净态须为 pending（BR-IP-EXE-021 / EX-IP-EXE-401）。
     *
     * <p>已执行、已不执行、已取消执行（含 CANCEL 对冲后）均不可再登记不执行。</p>
     *
     * @param order       医嘱读模型
     * @param medDetail   药品明细；非药嘱可 null
     * @param svcDetail   诊疗明细；非诊嘱可 null
     * @param planTime    occurrence 锚点
     * @param procedures  已加载 Procedure 行
     * @return true 表示该 occurrence 仍为待执行净态
     */
    public boolean isPlanTimePending(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        OccurrenceNetSlotState netState = this.resolveNetSlotState(order, medDetail, svcDetail, planTime, procedures);
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 执行-取消对冲净值待执行，待执行 Tab 可再次执行
            return true;
        }
        return false;
    }

    /**
     * 写路径「执行」Gate：occurrence 净态为 pending 或 cancelled 时可写（BR-IP-EXE-014 / EX-IP-EXE-301）。
     *
     * <p>执行一次再取消一次视为对冲，净态归 hedged pending，待执行/取消 Tab 均可再次执行；无 refund 指向的孤立 CANCEL 仍为 cancelled 净态。
     * 多次执行/取消往返时以 occurrence 组内 id 最大记录 + CANCEL.refund_id 判定净态，避免误读历史行。</p>
     *
     * @param order       医嘱读模型
     * @param medDetail   药品明细；非药嘱可 null
     * @param svcDetail   诊疗明细；非诊嘱可 null
     * @param planTime    occurrence 锚点
     * @param procedures  已加载 Procedure 行
     * @return true 表示该 occurrence 允许正向执行
     */
    public boolean isPlanTimeExecutable(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        OccurrenceNetSlotState netState = this.resolveNetSlotState(order, medDetail, svcDetail, planTime, procedures);
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 执行-取消对冲净值待执行，待执行/取消 Tab 均可再次执行
            return true;
        }
        if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
            // 孤立 CANCEL 净态：允许再次执行，写路径追加新 COMPLETED
            return true;
        }
        return false;
    }

    /**
     * 写 Gate：planTime（occurrence）是否晚于停嘱时刻。
     *
     * <p>写路径唯一的时间边界校验；与 actualTime、executionDeadline、当前日期无关。</p>
     *
     * @param order     医嘱骨架
     * @param medDetail 药品明细；非药嘱可 null
     * @param svcDetail 诊疗明细；非诊嘱可 null
     * @param planTime  计划执行时刻
     * @return {@code true} 表示 planTime 严格晚于停嘱时刻
     */
    public boolean isPlanTimeAfterStopTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime) {
        if (planTime == null) {
            return false;
        }
        LocalDateTime stopTime = this.resolveOrderStopTime(order, medDetail, svcDetail);
        return stopTime != null && planTime.isAfter(stopTime);
    }

    /**
     * 写 Gate：请求状态是否允许正向执行（与列表 Q-IP-EXE-001 对齐：VERIFIED + STOPPED）。
     *
     * @param order     医嘱骨架
     * @param medDetail 药品明细；非药嘱可 null
     * @param svcDetail 诊疗明细；非诊嘱可 null
     * @return true 表示已校对或在执停嘱态，可进入 planTime 边界校验
     */
    public boolean isRequestStatusExecutableForWrite(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        String status = this.resolveRequestStatusCode(order, medDetail, svcDetail);
        if (RequestStatus.VERIFIED.getCode().equals(status)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 请求状态：域明细优先于 OrderInfo 骨架（与列表展示、停嘱 Gate 同源）。
     */
    private String resolveRequestStatusCode(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
                return medDetail.getStatusEnum();
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
                return svcDetail.getStatusEnum();
            }
        }
        return order.getStatusEnum();
    }

    /**
     * 解析停嘱右边界；未停嘱返回 null。
     */
    private LocalDateTime resolveOrderStopTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            LocalDateTime stopTime = null;
            if (medDetail != null
                && RequestStatus.STOPPED.getCode().equals(medDetail.getStatusEnum())
                && medDetail.getStopTime() != null) {
                stopTime = medDetail.getStopTime();
            }
            if (RequestStatus.STOPPED.getCode().equals(order.getStatusEnum())
                && order.getStopTime() != null) {
                if (stopTime == null || order.getStopTime().isBefore(stopTime)) {
                    stopTime = order.getStopTime();
                }
            }
            return stopTime;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            LocalDateTime stopTime = null;
            if (svcDetail != null
                && RequestStatus.STOPPED.getCode().equals(svcDetail.getStatusEnum())
                && svcDetail.getStopTime() != null) {
                stopTime = svcDetail.getStopTime();
            }
            if (RequestStatus.STOPPED.getCode().equals(order.getStatusEnum())
                && order.getStopTime() != null) {
                if (stopTime == null || order.getStopTime().isBefore(stopTime)) {
                    stopTime = order.getStopTime();
                }
            }
            return stopTime;
        }
        return null;
    }

    /**
     * 写 Gate 用：剔除长期无频次等不可执行医嘱（不停嘱整单拒绝，停嘱边界见 {@link #isPlanTimeAfterStopTime}）。
     */
    private boolean shouldExcludeExecuteRowForWrite(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svcDetail);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
            // 床位滚费配置长期 SR 禁止手动执行（BR-IP-ARG-019）
            if (this.shouldExcludeAutoRollBedServiceRequest(svcDetail)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (medDetail != null && StringUtils.isNotEmpty(medDetail.getRateCode())) {
                    rateCode = medDetail.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = svcDetail;
            if (svc != null && StringUtils.isNotEmpty(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotEmpty(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 剔除床位滚费配置 ensure 的长期 SR（generate_source=auto-roll-fees；BR-IP-ARG-019）。
     *
     * @param svc 诊疗申请读模型
     * @return 应剔除时 true
     */
    private boolean shouldExcludeAutoRollBedServiceRequest(ServiceRequestListModel svc) {
        if (svc == null) {
            return false;
        }
        return GenerateSource.AUTO_ROLL_FEES.getCode().equals(svc.getGenerateSourceEnum());
    }

    /**
     * planTime 比对（秒级，避免 JSON 序列化丢纳秒导致 Gate 误判）。
     */
    private boolean planTimesEqual(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        LocalDateTime normalizedLeft = left.withNano(0);
        LocalDateTime normalizedRight = right.withNano(0);
        return normalizedLeft.equals(normalizedRight);
    }

    /**
     * 剔除无 rateCode 长期、转科/出院/护理等特殊诊疗等（BR-IP-EXE-003）；已停嘱医嘱仍保留在执行列表展示。
     */
    private boolean shouldExcludeExecuteRow(
        InpatientOrderListSkeleton order,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        Map<Long, ServiceRequestListModel> svcDetailById) {
        MedicationRequestListQueryModel med = null;
        ServiceRequestListModel svc = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            med = medDetailById.get(order.getMedReqId());
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            svc = svcDetailById.get(order.getOrderId());
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svc);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
            // 床位滚费配置长期 SR 禁止手动执行（BR-IP-ARG-019）
            if (this.shouldExcludeAutoRollBedServiceRequest(svc)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (med != null && StringUtils.isNotEmpty(med.getTherapyEnum())) {
                therapy = med.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (med != null && StringUtils.isNotEmpty(med.getRateCode())) {
                    rateCode = med.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    // 长期药嘱无频次无法展开计划点（BR-IP-EXE-002）
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svc != null && StringUtils.isNotEmpty(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotEmpty(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    // 长期诊嘱无频次同理剔除（BR-IP-EXE-003）
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 单条 OrderInfo → 执行列表行（含四类槽位 + bindItems）。
     *
     * @param encounterOrgIds              就诊→机构，供 bindItems 模板 org 收窄
     * @param applyStandingOrderPackageBatch 是否启用长嘱不可拆一盒一点（仅 IF-IP-EXE-01 列表）
     * @param medicationProductByKey       药品复合键 → 目录；不可拆时非空
     * @param executionRecordHistory       true=事实行分桶；false=净态分桶
     * @param pendingOnly                  true=仅待执行 Tab，跳过终态槽 DTO
     * @param bindRowsCache                绑定模板请求内缓存；可空
     * @return 行与是否已按包装一盒一点；不可映射时 null
     */
    private PackageBatchExpandOutcome mapOrderRow(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        NurseStationOrderViewDisplayContext displayContext,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline,
        Map<Long, Long> encounterOrgIds,
        boolean applyStandingOrderPackageBatch,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        boolean executionRecordHistory,
        boolean pendingOnly,
        Map<String, List<AdviceOrderBindRowDto>> bindRowsCache) {
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildMedicationDisplayCore(order, medDetail, displayContext);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildServiceDisplayCore(order, svcDetail, displayContext);
        } else {
            // 本期列表仅药/诊，其他 orderType 跳过
            return null;
        }
        if (core == null) {
            return null;
        }
        NurseStationOrderExecuteRowDto line = new NurseStationOrderExecuteRowDto();
        this.applyViewCoreToExecuteRow(core, line);
        if (svcDetail != null && svcDetail.getActivityId() != null) {
            line.setActivityDefinitionId(svcDetail.getActivityId());
            specimenTubeMergeBindResolveSupport.findMergeDeviceIdByActivityId(svcDetail.getActivityId())
                .ifPresent(line::setSpecimenTubeMergeDeviceId);
        }
        // 药嘱途径 code / 输液标志：供打印中心等按 code 筛选，禁止前端用 usageDisplay 文案分支
        if (medDetail != null) {
            line.setMethodCode(medDetail.getMethodCode());
            // 频次 code：输液贴按每日次数拆张（与门诊 rateCode→dayTimes 同源）
            line.setRateCode(medDetail.getRateCode());
            Boolean infusionFlag = medDetail.getInfusionFlag();
            // 药嘱未落库 infusion_flag 时回落药品目录 inject_flag（与门诊护士站处置同源）
            if (infusionFlag == null) {
                ProductDefinitionDisplayDto product =
                    this.resolveMedicationProduct(medDetail, medicationProductByKey);
                if (product != null) {
                    infusionFlag = product.getInjectFlag();
                }
            }
            line.setInfusionFlag(infusionFlag);
        } else if (svcDetail != null) {
            // 诊嘱频次 code（长期诊疗执行槽展开同源）
            line.setRateCode(svcDetail.getRateCode());
        }
        Map<String, String> methodLabelByCode = Collections.emptyMap();
        if (displayContext != null && displayContext.getMethodLabelByCode() != null) {
            methodLabelByCode = displayContext.getMethodLabelByCode();
        }
        // 频次计划点 + Procedure 归并 → 四类槽位（可选包装一盒一点）
        SlotBuckets buckets = this.buildSlotBuckets(
            order,
            medDetail,
            svcDetail,
            procedures,
            executionDeadline,
            applyStandingOrderPackageBatch,
            medicationProductByKey,
            methodLabelByCode,
            executionRecordHistory,
            pendingOnly);
        line.setPendingSlots(buckets.pendingSlots);
        line.setExecutedSlots(buckets.executedSlots);
        line.setSkippedSlots(buckets.skippedSlots);
        line.setCancelledSlots(buckets.cancelledSlots);
        // 待执行 Tab 且无 pending：整行跳过（不装 bind / 单位领用）
        if (pendingOnly
            && (buckets.pendingSlots == null || buckets.pendingSlots.isEmpty())) {
            return null;
        }
        // 单位领用剩余展示 + 按天抑制下一单位 pending（BR-IP-EXE-038：一盒一点 / 拆零下限同源）
        if (medDetail != null
            && (buckets.packageBatchApplied || buckets.minSplitUnitBatchApplied)) {
            StandingOrderOralPackageInventorySupport.UnitClaimMode claimMode =
                StandingOrderOralPackageInventorySupport.UnitClaimMode.PACKAGE;
            if (buckets.minSplitUnitBatchApplied && !buckets.packageBatchApplied) {
                // 035 优先；仅命中拆零下限切批时走 MIN_SPLIT
                claimMode = StandingOrderOralPackageInventorySupport.UnitClaimMode.MIN_SPLIT;
            }
            this.applyUnitClaimInventoryToExecuteRow(
                line,
                medDetail,
                procedures,
                applyStandingOrderPackageBatch,
                claimMode,
                medicationProductByKey,
                methodLabelByCode,
                executionDeadline);
            // 抑制后可能清空 pending
            if (pendingOnly
                && (line.getPendingSlots() == null || line.getPendingSlots().isEmpty())) {
                return null;
            }
        }
        Long orgId = null;
        if (encounterOrgIds != null) {
            orgId = encounterOrgIds.get(order.getEncounterId());
        }
        // bindItems 供前端勾选；写路径按勾选项派生
        line.setBindItems(
            this.loadBindItems(order, medDetail, svcDetail, orgId, displayContext.getUnitLabelByCode(), bindRowsCache));
        PackageBatchExpandOutcome outcome = new PackageBatchExpandOutcome();
        outcome.row = line;
        outcome.packageBatchApplied = buckets.packageBatchApplied;
        return outcome;
    }

    /**
     * 命中一盒一点或拆零下限切批后：回填剩余字段，并按自然日抑制过早的下一单位 pending。
     *
     * @param line                           执行行
     * @param medDetail                      药嘱明细
     * @param currentProcedures              本行已加载 Procedure
     * @param applyStandingOrderPackageBatch 全局不可拆
     * @param claimMode                      PACKAGE / MIN_SPLIT
     * @param medicationProductByKey         目录
     * @param methodLabelByCode              用法名
     * @param executionDeadline              列表截止（仅展开待执行窗口；已吃 asOf 在 Support 内钳到今天）
     */
    private void applyUnitClaimInventoryToExecuteRow(
        NurseStationOrderExecuteRowDto line,
        MedicationRequestListQueryModel medDetail,
        List<ProcedureRecordQueryModel> currentProcedures,
        boolean applyStandingOrderPackageBatch,
        StandingOrderOralPackageInventorySupport.UnitClaimMode claimMode,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        Map<String, String> methodLabelByCode,
        LocalDateTime executionDeadline) {
        if (line == null || medDetail == null || medDetail.getId() == null || claimMode == null) {
            return;
        }
        MedicationRequest display = medicationRequestQueryService.findById(medDetail.getId());
        if (display == null) {
            return;
        }
        ProductDefinitionDisplayDto product = this.resolveMedicationProduct(medDetail, medicationProductByKey);
        List<MedicationRequest> chain =
            standingOrderOralPackageInventorySupport.loadDoseChangeChain(display.getId());
        Map<Long, List<ProcedureRecordQueryModel>> proceduresByRequestId = new HashMap<>();
        // 本行 Procedure 先放入，减少重复 IO
        List<ProcedureRecordQueryModel> currentList = currentProcedures;
        if (currentList == null) {
            currentList = List.of();
        }
        proceduresByRequestId.put(display.getId(), currentList);
        for (MedicationRequest mr : chain) {
            if (mr == null || mr.getId() == null) {
                continue;
            }
            if (proceduresByRequestId.containsKey(mr.getId())) {
                continue;
            }
            proceduresByRequestId.put(
                mr.getId(),
                procedureQueryService.getProcedureRecords(mr.getId(), TableNameConstants.MED_MEDICATION_REQUEST));
        }
        // 截止可晚于今天（为看未来待执行）；已吃统计日传截止日，由 Support 钳到今天，避免「剩余0但领用点未出」
        LocalDate asOf = LocalDate.now();
        if (executionDeadline != null) {
            asOf = executionDeadline.toLocalDate();
        }
        StandingOrderOralPackageInventorySupport.OralPackageInventorySnapshot snap =
            standingOrderOralPackageInventorySupport.compute(
                display,
                proceduresByRequestId,
                product,
                methodLabelByCode,
                asOf,
                claimMode,
                applyStandingOrderPackageBatch);
        if (!snap.isApplied() || !snap.isDisplayHost()) {
            return;
        }
        // 有净领用才挂剩余展示；未领第一单位不展示 0/0（抑制逻辑仍用 snap）
        if (snap.getClaimedSplitQuantity() != null
            && snap.getClaimedSplitQuantity().compareTo(BigDecimal.ZERO) > 0) {
            line.setOralPackageBatchInventoryApplied(Boolean.TRUE);
            line.setOralPackageConsumedSplitQuantity(snap.getConsumedSplitQuantity());
            line.setOralPackageRemainSplitQuantity(snap.getRemainSplitQuantity());
            line.setOralPackageSplitUnitDisplay(snap.getSplitUnitDisplay());
            line.setOralPackageNextClaimDate(snap.getNextClaimDate());
        }
        // 按天抑制过早单位首槽；已领用后若切批锚点被抑制殆尽，须在预计领用日补回首频次点（改量链续算空窗）
        List<NurseStationOrderExecuteSlotDto> kept = new ArrayList<>();
        if (line.getPendingSlots() != null) {
            for (NurseStationOrderExecuteSlotDto slot : line.getPendingSlots()) {
                if (slot == null || slot.getPlanTime() == null) {
                    continue;
                }
                if (standingOrderOralPackageInventorySupport.shouldSuppressPending(
                    snap, slot.getPlanTime().toLocalDate())) {
                    // 本日理论剩余仍够：不回传该单位首槽
                    continue;
                }
                kept.add(slot);
            }
        }
        this.ensureNextClaimPendingSlot(display, snap, kept, executionDeadline);
        line.setPendingSlots(kept);
    }

    /**
     * 已领用且截止覆盖「预计下次领用日」时，保证待执行至少有该日首频次锚点。
     *
     * <p>切批按单条新嘱开立日累计量，改量续算后下一单位真实领用日往往不是切批「首槽」；
     * 抑制开立日假锚点后若不再补点，列表会出现「有下次领用日、无待执行时间点」。</p>
     *
     * @param display           链尾药嘱
     * @param snap              剩余快照
     * @param kept              抑制后的 pending（可被本方法改写）
     * @param executionDeadline 列表截止
     */
    private void ensureNextClaimPendingSlot(
        MedicationRequest display,
        StandingOrderOralPackageInventorySupport.OralPackageInventorySnapshot snap,
        List<NurseStationOrderExecuteSlotDto> kept,
        LocalDateTime executionDeadline) {
        if (kept == null || display == null || snap == null || executionDeadline == null) {
            return;
        }
        if (snap.getClaimedSplitQuantity() == null
            || snap.getClaimedSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            // 从未领盒：第一盒仍由切盒展开负责
            return;
        }
        LocalDate nextClaim = snap.getNextClaimDate();
        if (nextClaim == null) {
            // 前瞻仍够用：不应出领盒点
            return;
        }
        if (nextClaim.isAfter(executionDeadline.toLocalDate())) {
            // 截止尚未覆盖领盒日：本窗不展
            return;
        }
        boolean hasOnNextClaim = false;
        for (NurseStationOrderExecuteSlotDto slot : kept) {
            if (slot != null
                && slot.getPlanTime() != null
                && nextClaim.equals(slot.getPlanTime().toLocalDate())) {
                hasOnNextClaim = true;
                break;
            }
        }
        if (hasOnNextClaim) {
            return;
        }
        LocalDateTime anchor =
            standingOrderOralPackageInventorySupport.resolveNextClaimPendingPlanTime(display, nextClaim);
        if (anchor == null) {
            return;
        }
        LocalDateTime normalized = anchor.withNano(0);
        if (normalized.isAfter(executionDeadline)) {
            return;
        }
        LocalDateTime openedTime = display.getReqOpenedTime();
        LocalDateTime stopTime = display.getStopTime();
        LocalDateTime firstPlanTime = LongTermFirstExecutionSlotSupport.resolveFirstPlanTimeOnOpenedDay(
            List.of(normalized), openedTime);
        LocalDateTime lastPlanTimeOnStopDay = LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(
            List.of(normalized), stopTime);
        // 一盒一点同时最多一个 pending：清掉抑制后残留的错位锚点，只留领盒日
        kept.clear();
        kept.add(
            this.buildPendingSlotWithBoundaryDefaults(
                normalized, firstPlanTime, openedTime, lastPlanTimeOnStopDay, stopTime));
    }

    /**
     * Pre：按活动定义主键批量加载套餐子项（检查/检验申请单打印「明细」列）。
     *
     * @param svcDetails 本批诊嘱明细
     * @return activityId → 套餐子项；无子项时对应键可缺省
     */
    private Map<Long, List<AdvicePackageChildItemDto>> loadPackageChildrenByActivityId(
        Collection<ServiceRequestListModel> svcDetails) {
        if (svcDetails == null || svcDetails.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel svc : svcDetails) {
            if (svc != null && svc.getActivityId() != null) {
                activityIds.add(svc.getActivityId());
            }
        }
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ActivityDefinitionChildQueryModel>> modelsByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityIds);
        Map<Long, List<AdvicePackageChildItemDto>> result = new HashMap<>();
        for (Map.Entry<Long, List<ActivityDefinitionChildQueryModel>> entry : modelsByActivityId.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey(), AdviceExtensionJsonParser.toPackageChildItems(entry.getValue()));
        }
        return result;
    }

    /**
     * Pre：按服务申请主键批量加载部位/标本明细摘要（申请单打印用）。
     *
     * @param svcDetails 本批诊嘱明细
     * @return serviceRequestId → 明细摘要；无明细时对应键可缺省
     */
    private Map<Long, List<ServiceRequestDetailSummaryDto>> loadServiceRequestDetailSummaries(
        Collection<ServiceRequestListModel> svcDetails) {
        if (svcDetails == null || svcDetails.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> serviceRequestIds = new ArrayList<>();
        for (ServiceRequestListModel svc : svcDetails) {
            if (svc != null && svc.getId() != null) {
                serviceRequestIds.add(svc.getId());
            }
        }
        if (serviceRequestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ServiceRequestDetailListModel>> modelsByRequestId =
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(serviceRequestIds);
        Map<Long, List<ServiceRequestDetailSummaryDto>> result = new HashMap<>();
        for (Map.Entry<Long, List<ServiceRequestDetailListModel>> entry : modelsByRequestId.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey(), this.toServiceRequestDetailSummaries(entry.getValue()));
        }
        return result;
    }

    /**
     * Asm：领域明细模型 → 打印用明细摘要 DTO（不经门诊申请单 Orchestrator，避免跨 Feature support 依赖）。
     *
     * @param models 已 enrich 展示名的明细行
     * @return 摘要列表；无数据时空列表
     */
    private List<ServiceRequestDetailSummaryDto> toServiceRequestDetailSummaries(
        List<ServiceRequestDetailListModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServiceRequestDetailSummaryDto> result = new ArrayList<>();
        for (ServiceRequestDetailListModel model : models) {
            if (model == null) {
                continue;
            }
            ServiceRequestDetailSummaryDto dto = new ServiceRequestDetailSummaryDto();
            dto.setTargetTable(model.getTargetTable());
            dto.setTargetId(model.getTargetId());
            dto.setTargetDisplayName(model.getTargetDisplayName());
            dto.setRemark(model.getRemark());
            result.add(dto);
        }
        return result;
    }

    /**
     * Asm：诊嘱行挂载套餐子项与申请明细（仅 SERVICE_REQUEST；列表页不展示，供打印映射）。
     *
     * @param line                         执行列表行
     * @param svcDetail                    诊疗明细；非诊嘱为 null
     * @param packageChildrenByActivityId  activityId → 套餐子项
     * @param detailsByServiceRequestId    serviceRequestId → 部位/标本摘要
     */
    private void attachServiceRequestPrintDetails(
        NurseStationOrderExecuteRowDto line,
        ServiceRequestListModel svcDetail,
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId,
        Map<Long, List<ServiceRequestDetailSummaryDto>> detailsByServiceRequestId) {
        if (line == null || svcDetail == null) {
            return;
        }
        if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(line.getOrderType())) {
            return;
        }
        List<AdvicePackageChildItemDto> children = Collections.emptyList();
        if (svcDetail.getActivityId() != null && packageChildrenByActivityId != null) {
            List<AdvicePackageChildItemDto> found = packageChildrenByActivityId.get(svcDetail.getActivityId());
            if (found != null) {
                children = found;
            }
        }
        line.setPackageChildren(children);
        List<ServiceRequestDetailSummaryDto> details = Collections.emptyList();
        if (svcDetail.getId() != null && detailsByServiceRequestId != null) {
            List<ServiceRequestDetailSummaryDto> found = detailsByServiceRequestId.get(svcDetail.getId());
            if (found != null) {
                details = found;
            }
        }
        line.setServiceRequestDetails(details);
    }

    /**
     * 医嘱查看展示内核 → 执行列表行 DTO（不含槽位与 bindItems）。
     */
    private void applyViewCoreToExecuteRow(NurseStationOrderViewDisplayCore core, NurseStationOrderExecuteRowDto line) {
        line.setListRowKey(core.getListRowKey());
        line.setOrderId(core.getRequestId());
        line.setOrderType(core.getOrderType());
        line.setEncounterId(core.getEncounterId());
        line.setPatientId(core.getPatientId());
        line.setStatusEnum(core.getStatusEnum());
        line.setStatusEnumText(core.getStatusDisplay());
        line.setTherapyEnum(core.getTherapyEnum());
        line.setTherapyEnumText(core.getTherapyDisplay());
        line.setCategoryEnum(core.getCategoryEnum());
        // 类型列与校对同源（含嘱托「嘱托」，禁止前端仅按 orderType 回落「诊疗」）
        line.setOrderKindDisplay(core.getOrderKindDisplay());
        line.setAdviceDisplayName(core.getAdviceDisplayName());
        line.setRemark(core.getRemark());
        line.setSpecificationText(core.getSpecificationText());
        line.setPartPercent(core.getPartPercent());
        line.setPackageSplitRatioText(core.getPackageSplitRatioText());
        line.setPackageUnitDisplay(core.getPackageUnitDisplay());
        line.setDosageDisplay(core.getDosageDisplay());
        // 临嘱/出院带药开立总量；长期为 null（摆药总量另走 dose×次数或一盒一点）
        line.setQuantityDisplay(core.getQuantityDisplay());
        line.setDailyDoseDisplay(core.getDailyDoseDisplay());
        line.setUsageDisplay(core.getUsageDisplay());
        line.setFrequencyDisplay(core.getFrequencyDisplay());
        line.setSpeedCode(core.getSpeedCode());
        line.setSpeedDisplay(core.getSpeedDisplay());
        line.setReqOpenedTime(core.getReqOpenedTime());
        line.setReqAuthoredTime(core.getReqAuthoredTime());
        line.setStopTime(core.getStopTime());
        line.setRequesterName(core.getRequesterName());
        line.setStopperId(core.getStopperId());
        line.setStopperName(core.getStopperName());
        line.setPerformerCheckOrgDisplay(core.getPerformerCheckOrgDisplay());
        line.setGroupId(core.getGroupId());
        line.setBasedOnMedicationRequestId(core.getBasedOnMedicationRequestId());
        line.setSkinTestFlag(core.getSkinTestFlag());
        line.setSkinTestOutcomeCode(core.getSkinTestOutcomeCode());
        line.setSkinTestOutcomeText(core.getSkinTestOutcomeText());
    }

    /**
     * 槽位四类分桶（BR-IP-EXE-007～009）。
     *
     * <p>计划点集合 = 频次展开 ∪ Procedure 已有 occurrence；无 Procedure 覆盖的计划点 → pending；
     * 有 Procedure 时取组内最后一条为终态；执行-取消对冲净值 hedged pending，同时归入 pending 与 cancelled 桶。
     * <strong>不执行（STOPPED）只占用对应 occurrence</strong>，同医嘱其它 planTime 仍可 pending；
     * 禁止按「整条 Request 已有 STOPPED」清空其余计划点。</p>
     *
     * @param applyStandingOrderPackageBatch 全局不可拆时为 true（IF-IP-EXE-01 列表与出院门禁同源）
     * @param medicationProductByKey         药品目录 Map；一盒一点/拆零下限切盒用
     * @param methodLabelByCode              用法 code → 展示名；口服类判定用
     * @param executionRecordHistory         true=事实行（医生站执行记录）；false=净态（护士站）
     * @param pendingOnly                    true=仅构建 pending 槽，跳过终态 DTO
     */
    private SlotBuckets buildSlotBuckets(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline,
        boolean applyStandingOrderPackageBatch,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        Map<String, String> methodLabelByCode,
        boolean executionRecordHistory,
        boolean pendingOnly) {
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        LocalDateTime openedTime = this.resolveOpenedTime(order, medDetail, svcDetail);
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            // 临时嘱整单归并：有净执行记录即已执行，不再按 occurrence 与开立时刻并列 pending（BR-IP-EXE-006）
            return this.buildTemporarySlotBuckets(procedures, openedTime, executionRecordHistory, pendingOnly);
        }
        SlotBuckets buckets = new SlotBuckets();
        buckets.pendingSlots = new ArrayList<>();
        buckets.executedSlots = new ArrayList<>();
        buckets.skippedSlots = new ArrayList<>();
        buckets.cancelledSlots = new ArrayList<>();
        // 长期频次展开（含开立日首次槽裁剪；可选包装一盒一点 BR-IP-EXE-035）
        PlannedOccurrenceExpandResult expandResult = this.resolvePlannedOccurrenceTimes(
            order,
            medDetail,
            svcDetail,
            executionDeadline,
            applyStandingOrderPackageBatch,
            medicationProductByKey,
            methodLabelByCode);
        Set<LocalDateTime> plannedTimes = expandResult.planTimes;
        buckets.packageBatchApplied = expandResult.packageBatchApplied;
        buckets.minSplitUnitBatchApplied = expandResult.minSplitUnitBatchApplied;
        buckets.batchedAnchorApplied = expandResult.packageBatchApplied || expandResult.minSplitUnitBatchApplied;
        // 秒级归一，与 Procedure occurrence 分组合并键一致
        Set<LocalDateTime> normalizedPlanned = new LinkedHashSet<>();
        for (LocalDateTime planned : plannedTimes) {
            if (planned != null) {
                normalizedPlanned.add(planned.withNano(0));
            }
        }
        plannedTimes = normalizedPlanned;
        LocalDateTime firstPlanTime = LongTermFirstExecutionSlotSupport.resolveFirstPlanTimeOnOpenedDay(
            new ArrayList<>(plannedTimes), openedTime);
        LocalDateTime stopTime = this.resolveOrderStopTime(order, medDetail, svcDetail);
        LocalDateTime lastPlanTimeOnStopDay = LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(
            new ArrayList<>(plannedTimes), stopTime);
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> byOccurrence = this.groupProceduresByOccurrence(procedures);
        Set<LocalDateTime> allOccurrences = new LinkedHashSet<>(plannedTimes);
        allOccurrences.addAll(byOccurrence.keySet());
        for (LocalDateTime occurrence : allOccurrences) {
            if (occurrence == null) {
                continue;
            }
            LocalDateTime occurrenceKey = occurrence.withNano(0);
            List<ProcedureRecordQueryModel> group = byOccurrence.get(occurrenceKey);
            if (group == null || group.isEmpty()) {
                // 频次计划点尚无 Procedure 记录 → 待执行
                if (plannedTimes.contains(occurrenceKey)) {
                    buckets.pendingSlots.add(
                        this.buildPendingSlotWithBoundaryDefaults(
                            occurrenceKey, firstPlanTime, openedTime, lastPlanTimeOnStopDay, stopTime));
                }
                continue;
            }
            // 执行记录历史：同 occurrence 下每条终态 Procedure 各占一行（含对冲前 COMPLETED）
            if (executionRecordHistory) {
                if (!pendingOnly) {
                    this.appendHistoryTerminalSlots(buckets, group, occurrenceKey);
                }
                OccurrenceNetSlotState historyNet =
                    this.resolveOccurrenceNetSlotState(group, procedures);
                if (OccurrenceNetSlotState.PENDING.equals(historyNet)
                    || OccurrenceNetSlotState.HEDGED_PENDING.equals(historyNet)) {
                    if (plannedTimes.contains(occurrenceKey)) {
                        buckets.pendingSlots.add(
                            this.buildPendingSlotWithBoundaryDefaults(
                                occurrenceKey, firstPlanTime, openedTime, lastPlanTimeOnStopDay, stopTime));
                    }
                }
                continue;
            }
            // 同一 occurrence 可能有多条 Procedure（执行/取消往返），按净态分桶
            OccurrenceNetSlotState netState = this.resolveOccurrenceNetSlotState(group, procedures);
            if (OccurrenceNetSlotState.PENDING.equals(netState)
                || OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
                if (plannedTimes.contains(occurrenceKey)) {
                    buckets.pendingSlots.add(
                        this.buildPendingSlotWithBoundaryDefaults(
                            occurrenceKey, firstPlanTime, openedTime, lastPlanTimeOnStopDay, stopTime));
                }
                // 待执行 Tab 不镜像取消槽；其它 Tab 保留对冲双展示
                if (!pendingOnly && OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
                    ProcedureRecordQueryModel hedgeFinalRecord = this.pickFinalProcedureRecord(group);
                    NurseStationOrderExecuteSlotDto cancelledSlot = this.toSlotDto(hedgeFinalRecord, occurrenceKey);
                    if (cancelledSlot != null) {
                        buckets.cancelledSlots.add(cancelledSlot);
                    }
                }
                continue;
            }
            // 待执行 Tab：终态槽跳过 DTO 装配
            if (pendingOnly) {
                continue;
            }
            ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(group);
            NurseStationOrderExecuteSlotDto slot = this.toSlotDto(finalRecord, occurrenceKey);
            if (slot == null) {
                continue;
            }
            if (OccurrenceNetSlotState.EXECUTED.equals(netState)) {
                buckets.executedSlots.add(slot);
            } else if (OccurrenceNetSlotState.SKIPPED.equals(netState)) {
                buckets.skippedSlots.add(slot);
            } else if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
                buckets.cancelledSlots.add(slot);
            }
        }
        // 各桶内按 planTime 升序，便于护士按时间顺序操作
        this.sortSlots(buckets.pendingSlots);
        if (!pendingOnly) {
            this.sortSlots(buckets.executedSlots);
            this.sortSlots(buckets.skippedSlots);
            this.sortSlots(buckets.cancelledSlots);
        }
        // 一盒一点 / 拆零下限切批：同时最多一个待执行锚点（上一单位未执行完前不提前出下一单位）
        if (buckets.batchedAnchorApplied
            && buckets.pendingSlots != null
            && buckets.pendingSlots.size() > 1) {
            NurseStationOrderExecuteSlotDto earliestPending = buckets.pendingSlots.get(0);
            buckets.pendingSlots = new ArrayList<>();
            buckets.pendingSlots.add(earliestPending);
        }
        return buckets;
    }

    /**
     * 临时嘱槽位分桶（BR-IP-EXE-006～008）。
     *
     * <p>临时<strong>不展开频次</strong>，整单仅一个逻辑槽（开立时刻作 planTime 锚）。不执行/执行仍按该 occurrence
     * 单点占用（STOPPED/COMPLETED），<strong>不是</strong>把长期多计划点整单作废。临嘱写路径强制
     * occurrence=recorded；已执行槽读侧对齐该同值时刻并回传 procedureId/operatorName（BR-IP-EXE-006/030）。
     * 待执行回传 {@code defaultActualTime}=开立延后（与长嘱首次槽同口径，BR-IP-EXE-032）。</p>
     *
     * @param procedures   该 request 下全部 Procedure
     * @param openedTime 开立时刻，待执行 planTime
     * @param executionRecordHistory true=事实行；false=净态
     * @param pendingOnly true=仅 pending 槽
     * @return 四类槽位分桶
     */
    private SlotBuckets buildTemporarySlotBuckets(
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime openedTime,
        boolean executionRecordHistory,
        boolean pendingOnly) {
        SlotBuckets buckets = new SlotBuckets();
        buckets.pendingSlots = new ArrayList<>();
        buckets.executedSlots = new ArrayList<>();
        buckets.skippedSlots = new ArrayList<>();
        buckets.cancelledSlots = new ArrayList<>();
        if (executionRecordHistory) {
            // 临时嘱历史：每条终态 Procedure 各一行；净态仍决定是否回待执行
            if (!pendingOnly) {
                this.appendHistoryTemporaryTerminalSlots(buckets, procedures);
            }
            OccurrenceNetSlotState historyNet = this.resolveTemporaryNetSlotState(procedures);
            if (OccurrenceNetSlotState.PENDING.equals(historyNet)
                || OccurrenceNetSlotState.HEDGED_PENDING.equals(historyNet)) {
                if (openedTime != null) {
                    buckets.pendingSlots.add(this.buildTemporaryPendingSlot(openedTime));
                }
            }
            return buckets;
        }
        OccurrenceNetSlotState netState = this.resolveTemporaryNetSlotState(procedures);
        if (OccurrenceNetSlotState.EXECUTED.equals(netState)) {
            if (!pendingOnly) {
                ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
                if (lastNetCompleted != null) {
                    // 临时已执行：写路径 occurrence=recorded 同值；读侧回传 procedureId 供取消
                    buckets.executedSlots.add(this.toTemporaryTerminalSlotDto(lastNetCompleted));
                }
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            if (openedTime != null) {
                buckets.pendingSlots.add(this.buildTemporaryPendingSlot(openedTime));
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 临时嘱执行-取消对冲：待执行/取消 Tab 双展示
            if (openedTime != null) {
                buckets.pendingSlots.add(this.buildTemporaryPendingSlot(openedTime));
            }
            if (!pendingOnly) {
                ProcedureRecordQueryModel hedgeFinalRecord = this.pickNetFinalProcedureRecord(procedures);
                if (hedgeFinalRecord != null) {
                    buckets.cancelledSlots.add(this.toTemporaryTerminalSlotDto(hedgeFinalRecord));
                }
            }
            return buckets;
        }
        if (pendingOnly) {
            // 待执行 Tab：其余终态不装配；净态非 pending/hedged 时无待执槽
            if (OccurrenceNetSlotState.SKIPPED.equals(netState)
                || OccurrenceNetSlotState.CANCELLED.equals(netState)) {
                return buckets;
            }
            if (openedTime != null) {
                buckets.pendingSlots.add(this.buildTemporaryPendingSlot(openedTime));
            }
            return buckets;
        }
        ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            if (openedTime != null) {
                buckets.pendingSlots.add(this.buildTemporaryPendingSlot(openedTime));
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.SKIPPED.equals(netState)) {
            // 不执行仅占用本 occurrence 单槽；planTime 仍为计划锚点，禁止用登记时刻冒充
            buckets.skippedSlots.add(this.toTemporaryTerminalSlotDto(netFinal));
            return buckets;
        }
        if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
            buckets.cancelledSlots.add(this.toTemporaryTerminalSlotDto(netFinal));
        }
        return buckets;
    }

    /**
     * 长期频次展开 / 临时单槽（BR-IP-EXE-004/005/006；开立日首次槽见 BR-IP-ORD-030⑥；
     * 长嘱不可拆一盒一点见 BR-IP-EXE-035）。
     *
     * <p>可拆口径：左边界仍取开立自然日 00:00；右边界 = {@code min(停嘱时刻, executionDeadline)}；
     * 再按开立时刻裁首次槽。不可拆且长期 + 包装≠拆零 + 口服类：在首日逻辑之后按包装拆零量切盒，
     * 筛选截止落在第 N 盒时仅回传第 1～N 盒<strong>盒首锚点</strong>（一盒一点）；
     * 单槽超量/累加无法整除 → 降级可拆；停嘱硬顶截断未凑满当前盒 → <strong>不</strong>降级可拆，
     * 仅回传已完整盒首（无则空；已执行锚点由 Procedure 并集保留）。</p>
     *
     * @param applyStandingOrderPackageBatch 是否尝试不可拆一盒一点
     * @param medicationProductByKey         药品目录
     * @param methodLabelByCode              用法展示名 Map
     * @return 计划点与是否已按包装一盒一点
     */
    private PlannedOccurrenceExpandResult resolvePlannedOccurrenceTimes(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime executionDeadline,
        boolean applyStandingOrderPackageBatch,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        Map<String, String> methodLabelByCode) {
        PlannedOccurrenceExpandResult result = new PlannedOccurrenceExpandResult();
        result.planTimes = new LinkedHashSet<>();
        result.packageBatchApplied = false;
        result.minSplitUnitBatchApplied = false;
        if (executionDeadline == null) {
            return result;
        }
        LocalDateTime openedTime = this.resolveOpenedTime(order, medDetail, svcDetail);
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        String statusEnum = order.getStatusEnum();
        LocalDateTime stopHardCap = null;
        String rateCode = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null) {
                if (StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
                    statusEnum = medDetail.getStatusEnum();
                }
                // 右边界：仅停嘱（STOPPED）时取 stop_time，禁止与 check_time / 计划结束混用
                if (RequestStatus.STOPPED.getCode().equals(medDetail.getStatusEnum())
                    && medDetail.getStopTime() != null) {
                    stopHardCap = medDetail.getStopTime();
                }
                if (StringUtils.isNotEmpty(medDetail.getRateCode())) {
                    rateCode = medDetail.getRateCode();
                }
            }
            if (RequestStatus.STOPPED.getCode().equals(statusEnum)
                && order.getStopTime() != null) {
                if (stopHardCap == null || order.getStopTime().isBefore(stopHardCap)) {
                    // 读模型骨架上的停嘱时间进一步收窄硬上界
                    stopHardCap = order.getStopTime();
                }
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null) {
                if (StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
                    statusEnum = svcDetail.getStatusEnum();
                }
                if (RequestStatus.STOPPED.getCode().equals(svcDetail.getStatusEnum())
                    && svcDetail.getStopTime() != null) {
                    stopHardCap = svcDetail.getStopTime();
                }
                if (StringUtils.isNotEmpty(svcDetail.getRateCode())) {
                    rateCode = svcDetail.getRateCode();
                }
            }
            if (RequestStatus.STOPPED.getCode().equals(statusEnum)
                && order.getStopTime() != null) {
                if (stopHardCap == null || order.getStopTime().isBefore(stopHardCap)) {
                    stopHardCap = order.getStopTime();
                }
            }
        }
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            // 临时医嘱：仅一个计划点 = 开立时刻（完整时分秒）
            if (openedTime != null) {
                result.planTimes.add(openedTime);
            }
            return result;
        }
        if (openedTime == null || StringUtils.isEmpty(rateCode)) {
            return result;
        }
        boolean tryPackageBatch = applyStandingOrderPackageBatch
            && ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())
            && TherapyType.LONG_TERM.getCode().equals(therapy)
            && medDetail != null;
        StandingOrderPackageBatchPlanTimeResolver.MeasureInput measure = null;
        ProductDefinitionDisplayDto product = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())
            && TherapyType.LONG_TERM.getCode().equals(therapy)
            && medDetail != null) {
            // Pre：药嘱槽量/用法 + 目录，供一盒一点或拆零下限切批
            measure = new StandingOrderPackageBatchPlanTimeResolver.MeasureInput();
            measure.setTherapyEnum(therapy);
            measure.setQuantity(medDetail.getQuantity());
            measure.setUnitCode(medDetail.getUnitCode());
            measure.setFirstDose(medDetail.getFirstDose());
            measure.setFirstDoseUnitCode(medDetail.getFirstDoseUnitCode());
            measure.setMethodCode(medDetail.getMethodCode());
            product = this.resolveMedicationProduct(medDetail, medicationProductByKey);
        }
        try {
            // Asm：可拆频次 / 一盒一点 / 拆零下限切批 与待执计数同源（BR-IP-EXE-035/036 / BR-IP-NUR-005）
            StandingOrderPackageBatchPlanTimeResolver.ExpandResult expand =
                standingOrderPackageBatchPlanTimeResolver.resolveLongTermPlanTimes(
                    rateCode,
                    openedTime,
                    executionDeadline,
                    stopHardCap,
                    tryPackageBatch,
                    measure,
                    product,
                    methodLabelByCode);
            result.planTimes = expand.getPlanTimes();
            result.packageBatchApplied = expand.isPackageBatchApplied();
            result.minSplitUnitBatchApplied = expand.isMinSplitUnitBatchApplied();
            return result;
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_201,
                "医嘱引用频次不可用");
        }
    }

    /**
     * 批量加载长期药品目录，供不可拆切盒取 {@code packageUnitRatio}。
     *
     * @param medDetailById 药品申请明细
     * @return 复合键 → 目录；无药时返回空 Map
     */
    private Map<String, ProductDefinitionDisplayDto> loadMedicationProductsForPackageBatch(
        Map<Long, MedicationRequestListQueryModel> medDetailById) {
        if (medDetailById == null || medDetailById.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel med : medDetailById.values()) {
            if (med == null || med.getMedicationId() == null) {
                continue;
            }
            if (!TherapyType.LONG_TERM.getCode().equals(med.getTherapyEnum())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, med.getMedicationId());
            if (key == null || seenKeys.contains(key)) {
                continue;
            }
            seenKeys.add(key);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(med.getMedicationId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Collections.emptyMap();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 解析本行药品目录展示 DTO。
     *
     * @param medDetail              药品申请
     * @param medicationProductByKey 预热 Map
     * @return 目录；缺失时 null
     */
    private ProductDefinitionDisplayDto resolveMedicationProduct(
        MedicationRequestListQueryModel medDetail,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey) {
        if (medDetail == null || medDetail.getMedicationId() == null) {
            return null;
        }
        if (medicationProductByKey == null || medicationProductByKey.isEmpty()) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medDetail.getMedicationId());
        if (key == null) {
            return null;
        }
        return medicationProductByKey.get(key);
    }

    /**
     * 解析医嘱开立时刻，供列表频次展开窗口左边界使用。
     *
     * <p>优先 {@code req_opened_time}（开立）；空则回落 {@code req_authored_time}（签发，兼容存量）。</p>
     *
     * @param order     OrderInfo 骨架行
     * @param medDetail 药品明细，可空
     * @param svcDetail 诊疗明细，可空
     * @return 开立时刻；无法解析时返回 null
     */
    private LocalDateTime resolveOpenedTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        // 频次展开左界：明细 req_opened_time 优先，空回落 req_authored_time / 骨架。
        LocalDateTime openedTime = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && medDetail.getReqOpenedTime() != null) {
                openedTime = medDetail.getReqOpenedTime();
            }
            if (openedTime == null && medDetail != null && medDetail.getReqAuthoredTime() != null) {
                openedTime = medDetail.getReqAuthoredTime();
            }
            if (openedTime == null) {
                openedTime = order.resolveOrderOpenedTime();
            }
            return openedTime;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && svcDetail.getReqOpenedTime() != null) {
                openedTime = svcDetail.getReqOpenedTime();
            }
            if (openedTime == null && svcDetail != null && svcDetail.getReqAuthoredTime() != null) {
                openedTime = svcDetail.getReqAuthoredTime();
            }
            if (openedTime == null) {
                openedTime = order.resolveOrderOpenedTime();
            }
            return openedTime;
        }
        return order.resolveOrderOpenedTime();
    }

    /**
     * 解析医嘱长临 code；域明细优先，回落 OrderInfo 骨架。
     *
     * @param order     OrderInfo 骨架行
     * @param medDetail 药品明细，可空
     * @param svcDetail 诊疗明细，可空
     * @return TherapyType code；无法解析时返回 null
     */
    private String resolveTherapyEnum(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            return therapy;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getTherapyEnum())) {
                therapy = svcDetail.getTherapyEnum();
            }
            return therapy;
        }
        return therapy;
    }

    /**
     * 临时嘱：取 id 最大的一条 Procedure 作为整单末态（BR-IP-EXE-007 临时口径）。
     *
     * @param procedures 该 request 下全部 Procedure
     * @return 末态记录；无记录时 null
     */
    private ProcedureRecordQueryModel pickNetFinalProcedureRecord(List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        ProcedureRecordQueryModel netFinal = null;
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null) {
                continue;
            }
            if (netFinal == null) {
                netFinal = row;
                continue;
            }
            Long rowId = row.getId();
            Long finalId = netFinal.getId();
            if (rowId != null && (finalId == null || rowId.compareTo(finalId) > 0)) {
                netFinal = row;
            }
        }
        return netFinal;
    }

    /**
     * 临时嘱：取最后一条未被 CANCEL 对冲的 COMPLETED 记录（净执行）。
     *
     * @param procedures 该 request 下全部 Procedure
     * @return 净 COMPLETED；不存在时 null
     */
    private ProcedureRecordQueryModel pickLastNetCompletedProcedure(List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        ProcedureRecordQueryModel lastNet = null;
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (this.isProcedureOffsetByCancel(procedures, row.getId())) {
                continue;
            }
            if (lastNet == null) {
                lastNet = row;
                continue;
            }
            Long rowId = row.getId();
            Long lastId = lastNet.getId();
            if (rowId != null && (lastId == null || rowId.compareTo(lastId) > 0)) {
                lastNet = row;
            }
        }
        return lastNet;
    }

    /**
     * 临时嘱终态槽位装配（已执行 / 不执行 / 取消）。
     *
     * <p>临时嘱读侧仍为<strong>整单单逻辑槽</strong>。不执行/取消：{@code planTime} 取
     * {@code occurrence_time}。已执行：临嘱写路径强制 occurrence=recorded 同值；读侧
     * {@code planTime} 仍锚 occurrence（截止过滤），{@code recordedTime} 供已执行 Tab 展示；
     * 历史脏数据缺 recorded 时回落 planTime，保证时间列与 procedureId 可勾选取消
     * （BR-IP-EXE-006/030）。</p>
     *
     * @param record 净末态 Procedure
     * @return 槽位 DTO；无可用时刻时 null
     */
    private NurseStationOrderExecuteSlotDto toTemporaryTerminalSlotDto(ProcedureRecordQueryModel record) {
        if (record == null) {
            return null;
        }
        LocalDateTime planTime = record.getOccurrenceTime();
        if (planTime == null) {
            // 历史脏数据无 occurrence：回落 recorded，避免槽位键为空
            planTime = record.getRecordedTime();
        }
        if (planTime == null) {
            return null;
        }
        NurseStationOrderExecuteSlotDto slot = this.toSlotDto(record, planTime);
        if (slot == null) {
            return null;
        }
        // 已执行：缺 recorded 时回落 planTime，前端时间列与操作人拼接有据
        if (EventStatus.COMPLETED.getCode().equals(record.getStatusEnum()) && slot.getRecordedTime() == null) {
            slot.setRecordedTime(planTime);
        }
        return slot;
    }

    /**
     * Procedure 按 occurrence_time 分组（BR-IP-EXE-007，仅长期）。
     *
     * <p>occurrence 秒级归一，与 {@link #planTimesEqual} / 写 Gate 同口径，避免纳秒差拆成多个槽。</p>
     */
    private Map<LocalDateTime, List<ProcedureRecordQueryModel>> groupProceduresByOccurrence(
        List<ProcedureRecordQueryModel> procedures) {
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> grouped = new HashMap<>();
        if (procedures == null) {
            return grouped;
        }
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null || row.getOccurrenceTime() == null) {
                // 无 occurrence 的 Procedure 不参与槽位归并
                continue;
            }
            LocalDateTime occurrenceKey = row.getOccurrenceTime().withNano(0);
            List<ProcedureRecordQueryModel> bucket = grouped.get(occurrenceKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(occurrenceKey, bucket);
            }
            bucket.add(row);
        }
        return grouped;
    }

    /**
     * 组装待执行槽：开立日首次点 {@code defaultActualTime}=开立+延后分钟（BR-IP-EXE-032）；
     * 停嘱日末点=停嘱（对称易用，同槽开嘱优先）。
     *
     * @param occurrence            频次计划点（逻辑锚）
     * @param firstPlanTime         开立日首次 planTime
     * @param openedTime            开立时刻
     * @param lastPlanTimeOnStopDay 停嘱日末槽 planTime
     * @param stopTime              停嘱时刻（仅已停嘱非空）
     * @return pending 槽位 DTO
     */
    private NurseStationOrderExecuteSlotDto buildPendingSlotWithBoundaryDefaults(
        LocalDateTime occurrence,
        LocalDateTime firstPlanTime,
        LocalDateTime openedTime,
        LocalDateTime lastPlanTimeOnStopDay,
        LocalDateTime stopTime) {
        NurseStationOrderExecuteSlotDto pending = this.buildPendingSlot(occurrence);
        // 开嘱优先：同槽既是首次又是停嘱末槽时盖开立延后默认 actualTime
        if (LongTermFirstExecutionSlotSupport.isFirstExecutionSlot(occurrence, firstPlanTime)
            && openedTime != null) {
            pending.setDefaultActualTime(
                LongTermFirstExecutionSlotSupport.resolveFirstSlotDefaultActualTime(openedTime));
            return pending;
        }
        // 停嘱日末槽：建议 actualTime=停嘱时刻（逻辑仍锚 planTime）
        if (LongTermFirstExecutionSlotSupport.isLastExecutionSlotOnStopDay(occurrence, lastPlanTimeOnStopDay)
            && stopTime != null) {
            pending.setDefaultActualTime(stopTime);
        }
        return pending;
    }

    /**
     * 组内取最后一条为槽位终态（BR-IP-EXE-007）。
     */
    private ProcedureRecordQueryModel pickFinalProcedureRecord(List<ProcedureRecordQueryModel> group) {
        List<ProcedureRecordQueryModel> copy = new ArrayList<>(group);
        // id 最大 = 时间上最后写入的记录，作为该 planTime 的展示终态
        copy.sort(Comparator.comparing(ProcedureRecordQueryModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy.get(copy.size() - 1);
    }

    /**
     * 解析 occurrence 净态（长期嘱：按 occurrence 分组；写/读 Gate 共用）。
     *
     * @param order      医嘱读模型
     * @param medDetail  药品明细
     * @param svcDetail  诊疗明细
     * @param planTime   occurrence 锚点
     * @param procedures 该 request 下全部 Procedure
     * @return 净态枚举；不可写医嘱返回 null
     */
    private OccurrenceNetSlotState resolveNetSlotState(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        if (order == null || planTime == null) {
            return null;
        }
        if (this.shouldExcludeExecuteRowForWrite(order, medDetail, svcDetail)) {
            return null;
        }
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            return this.resolveTemporaryNetSlotState(procedures);
        }
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> byOccurrence = this.groupProceduresByOccurrence(procedures);
        List<ProcedureRecordQueryModel> group = null;
        for (Map.Entry<LocalDateTime, List<ProcedureRecordQueryModel>> entry : byOccurrence.entrySet()) {
            if (entry.getKey() != null && this.planTimesEqual(entry.getKey(), planTime)) {
                group = entry.getValue();
                break;
            }
        }
        if (group == null || group.isEmpty()) {
            // 该 occurrence 尚无 Procedure → 待执行
            return OccurrenceNetSlotState.PENDING;
        }
        return this.resolveOccurrenceNetSlotState(group, procedures);
    }

    /**
     * 长期嘱：单 occurrence 组内净态（BR-IP-EXE-007/008）。
     *
     * <p>组内 id 最大为末态；COMPLETED 若存在 CANCEL.refund_id 指向则净态为 hedged pending（执行-取消对冲，BR-NUR-005 净值）。</p>
     */
    private OccurrenceNetSlotState resolveOccurrenceNetSlotState(
        List<ProcedureRecordQueryModel> occurrenceGroup,
        List<ProcedureRecordQueryModel> allProceduresAtRequest) {
        ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(occurrenceGroup);
        if (finalRecord == null) {
            return OccurrenceNetSlotState.PENDING;
        }
        String status = finalRecord.getStatusEnum();
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(allProceduresAtRequest, finalRecord.getId())) {
                // 末态 COMPLETED 已被 CANCEL 对冲 → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.EXECUTED;
        }
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return OccurrenceNetSlotState.SKIPPED;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            if (finalRecord.getRefundId() != null) {
                // CANCEL 行指向被对冲 COMPLETED → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.CANCELLED;
        }
        // 进行中等非终态 → 仍视为待执行
        return OccurrenceNetSlotState.PENDING;
    }

    /**
     * 临时嘱：整单净态（BR-IP-EXE-006/008）。
     *
     * <p>存在净 COMPLETED → executed；否则按 id 最大末态归类；无记录 → pending。</p>
     */
    private OccurrenceNetSlotState resolveTemporaryNetSlotState(List<ProcedureRecordQueryModel> procedures) {
        ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
        if (lastNetCompleted != null) {
            return OccurrenceNetSlotState.EXECUTED;
        }
        ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            return OccurrenceNetSlotState.PENDING;
        }
        String status = netFinal.getStatusEnum();
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return OccurrenceNetSlotState.SKIPPED;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            if (netFinal.getRefundId() != null) {
                // 临时嘱：取消执行已对冲净 COMPLETED → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.CANCELLED;
        }
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(procedures, netFinal.getId())) {
                // 临时嘱：末态 COMPLETED 已被对冲 → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.EXECUTED;
        }
        return OccurrenceNetSlotState.PENDING;
    }

    /**
     * COMPLETED 是否已被 CANCEL 对冲（BR-NUR-005）。
     */
    private boolean isProcedureOffsetByCancel(List<ProcedureRecordQueryModel> procedures, Long completedProcedureId) {
        if (procedures == null || completedProcedureId == null) {
            return false;
        }
        for (ProcedureRecordQueryModel sibling : procedures) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                // 存在 CANCEL 记录指向该 COMPLETED → 执行-取消对冲，读侧净值归 pending
                return true;
            }
        }
        return false;
    }

    /**
     * 临时待执行单槽：planTime 仍锚开立时刻；{@code defaultActualTime}=开立延后（BR-IP-EXE-032）。
     *
     * @param openedTime 开立时刻（非空）
     * @return 带建议实际时分的 pending 槽
     */
    private NurseStationOrderExecuteSlotDto buildTemporaryPendingSlot(LocalDateTime openedTime) {
        NurseStationOrderExecuteSlotDto pending = this.buildPendingSlot(openedTime);
        // 临嘱单槽等同「首次」：列表展示/写侧默认 actualTime 开立延后两分钟
        pending.setDefaultActualTime(
            LongTermFirstExecutionSlotSupport.resolveFirstSlotDefaultActualTime(openedTime));
        return pending;
    }

    /**
     * 构建 PREPARATION 态 pending 槽位 DTO（无 procedureId）。
     */
    private NurseStationOrderExecuteSlotDto buildPendingSlot(LocalDateTime planTime) {
        NurseStationOrderExecuteSlotDto slot = new NurseStationOrderExecuteSlotDto();
        slot.setPlanTime(planTime);
        slot.setStatusEnum(EventStatus.PREPARATION.getCode());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, slot.getStatusEnum());
        if (status != null) {
            // 待执行槽位展示「准备中」等人读文案
            slot.setStatusEnumText(status.getDisplay());
        }
        return slot;
    }

    /**
     * 终态槽位批量回填操作人展示名（BR-IP-EXE-031）。
     *
     * <p>已执行优先 {@code cln_procedure_performer} 主执行人；不执行/取消等无执行人行时回落
     * Procedure {@code recorder_id}（经 {@code mapDisplayNameByEntererIds} 按医务人员主键解析姓名）。</p>
     *
     * @param rowLists 按就诊分组的医嘱行集合
     */
    private void enrichTerminalSlotOperatorNames(Collection<List<NurseStationOrderExecuteRowDto>> rowLists) {
        if (rowLists == null || rowLists.isEmpty()) {
            return;
        }
        // 收集已执行/不执行/取消槽位上的 procedureId（待执行无操作人）
        Set<Long> procedureIds = new LinkedHashSet<>();
        for (List<NurseStationOrderExecuteRowDto> rows : rowLists) {
            if (rows == null) {
                continue;
            }
            for (NurseStationOrderExecuteRowDto row : rows) {
                this.collectTerminalSlotProcedureIds(row, procedureIds);
            }
        }
        if (procedureIds.isEmpty()) {
            return;
        }
        // Pre：主执行人 practitionerId；无则用 recorderId（不执行/取消）
        Map<Long, Long> performerByProcedureId =
            procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        Map<Long, Long> recorderByProcedureId = new HashMap<>();
        // CANCEL 无 recorder 时回落被对冲 COMPLETED（refund_id）
        Map<Long, Long> refundIdByProcedureId = new HashMap<>();
        List<Procedure> procedures = procedureQueryService.listByIds(procedureIds);
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getId() == null) {
                continue;
            }
            if (procedure.getRecorderId() != null) {
                recorderByProcedureId.put(procedure.getId(), procedure.getRecorderId());
            }
            if (procedure.getRefundId() != null) {
                refundIdByProcedureId.put(procedure.getId(), procedure.getRefundId());
            }
        }
        // 存量 CANCEL 可能未写 recorder：补查 refund 指向的 COMPLETED 执行人/记录人
        Set<Long> refundLookupIds = new LinkedHashSet<>();
        for (Long refundId : refundIdByProcedureId.values()) {
            if (refundId != null && !procedureIds.contains(refundId)) {
                refundLookupIds.add(refundId);
            }
        }
        if (!refundLookupIds.isEmpty()) {
            Map<Long, Long> refundPerformers =
                procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(refundLookupIds);
            performerByProcedureId.putAll(refundPerformers);
            List<Procedure> refundedProcedures = procedureQueryService.listByIds(refundLookupIds);
            for (Procedure refunded : refundedProcedures) {
                if (refunded == null || refunded.getId() == null || refunded.getRecorderId() == null) {
                    continue;
                }
                recorderByProcedureId.put(refunded.getId(), refunded.getRecorderId());
            }
        }
        Set<Long> nameLookupIds = new LinkedHashSet<>();
        for (Long performerId : performerByProcedureId.values()) {
            if (performerId != null) {
                nameLookupIds.add(performerId);
            }
        }
        for (Long recorderId : recorderByProcedureId.values()) {
            if (recorderId != null) {
                nameLookupIds.add(recorderId);
            }
        }
        Map<Long, String> nameById = practitionerQueryService.mapDisplayNameByEntererIds(nameLookupIds);
        // Asm：按槽位 procedureId 写回 operatorName
        for (List<NurseStationOrderExecuteRowDto> rows : rowLists) {
            if (rows == null) {
                continue;
            }
            for (NurseStationOrderExecuteRowDto row : rows) {
                this.applyOperatorNameToTerminalSlots(
                    row,
                    performerByProcedureId,
                    recorderByProcedureId,
                    refundIdByProcedureId,
                    nameById);
            }
        }
    }

    /**
     * 从医嘱行终态槽位收集 procedureId。
     *
     * @param row          医嘱行
     * @param procedureIds 去重收集目标
     */
    private void collectTerminalSlotProcedureIds(
        NurseStationOrderExecuteRowDto row, Set<Long> procedureIds) {
        if (row == null || procedureIds == null) {
            return;
        }
        this.collectSlotProcedureIds(row.getExecutedSlots(), procedureIds);
        this.collectSlotProcedureIds(row.getSkippedSlots(), procedureIds);
        this.collectSlotProcedureIds(row.getCancelledSlots(), procedureIds);
    }

    /**
     * 槽位列表中非空 procedureId 写入集合。
     *
     * @param slots        槽位列表
     * @param procedureIds 去重收集目标
     */
    private void collectSlotProcedureIds(
        List<NurseStationOrderExecuteSlotDto> slots, Set<Long> procedureIds) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        for (NurseStationOrderExecuteSlotDto slot : slots) {
            if (slot == null || slot.getProcedureId() == null) {
                continue;
            }
            procedureIds.add(slot.getProcedureId());
        }
    }

    /**
     * 终态槽位写入操作人姓名。
     *
     * @param row                     医嘱行
     * @param performerByProcedureId  procedureId → 主执行人
     * @param recorderByProcedureId   procedureId → 记录人
     * @param refundIdByProcedureId   CANCEL procedureId → 被对冲 COMPLETED id
     * @param nameById                医务人员/用户主键 → 姓名
     */
    private void applyOperatorNameToTerminalSlots(
        NurseStationOrderExecuteRowDto row,
        Map<Long, Long> performerByProcedureId,
        Map<Long, Long> recorderByProcedureId,
        Map<Long, Long> refundIdByProcedureId,
        Map<Long, String> nameById) {
        if (row == null) {
            return;
        }
        this.applyOperatorNameToSlots(
            row.getExecutedSlots(),
            performerByProcedureId,
            recorderByProcedureId,
            refundIdByProcedureId,
            nameById);
        this.applyOperatorNameToSlots(
            row.getSkippedSlots(),
            performerByProcedureId,
            recorderByProcedureId,
            refundIdByProcedureId,
            nameById);
        this.applyOperatorNameToSlots(
            row.getCancelledSlots(),
            performerByProcedureId,
            recorderByProcedureId,
            refundIdByProcedureId,
            nameById);
    }

    /**
     * 单类槽位写回 operatorName（主执行人优先，否则记录人；CANCEL 无记录人再回落 refund 行）。
     *
     * @param slots                   槽位列表
     * @param performerByProcedureId  procedureId → 主执行人
     * @param recorderByProcedureId   procedureId → 记录人
     * @param refundIdByProcedureId   CANCEL → 被对冲 COMPLETED
     * @param nameById                主键 → 姓名
     */
    private void applyOperatorNameToSlots(
        List<NurseStationOrderExecuteSlotDto> slots,
        Map<Long, Long> performerByProcedureId,
        Map<Long, Long> recorderByProcedureId,
        Map<Long, Long> refundIdByProcedureId,
        Map<Long, String> nameById) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        for (NurseStationOrderExecuteSlotDto slot : slots) {
            if (slot == null || slot.getProcedureId() == null) {
                continue;
            }
            Long procedureId = slot.getProcedureId();
            // 已执行：优先主执行人；不执行/取消通常无 performer，回落 recorder
            Long nameKey = this.resolveOperatorNameKey(
                procedureId, performerByProcedureId, recorderByProcedureId);
            // 存量 CANCEL 未写 recorder：回落被对冲 COMPLETED 的执行人/记录人
            if (nameKey == null && refundIdByProcedureId != null) {
                Long refundId = refundIdByProcedureId.get(procedureId);
                if (refundId != null) {
                    nameKey = this.resolveOperatorNameKey(
                        refundId, performerByProcedureId, recorderByProcedureId);
                }
            }
            if (nameKey == null || nameById == null) {
                continue;
            }
            String operatorName = nameById.get(nameKey);
            if (StringUtils.isEmpty(operatorName)) {
                continue;
            }
            slot.setOperatorName(operatorName.trim());
        }
    }

    /**
     * 解析操作人姓名查找键：主执行人优先，否则记录人。
     *
     * @param procedureId             执行记录主键
     * @param performerByProcedureId  procedureId → 主执行人
     * @param recorderByProcedureId   procedureId → 记录人
     * @return 姓名查找键；均无则 null
     */
    private Long resolveOperatorNameKey(
        Long procedureId,
        Map<Long, Long> performerByProcedureId,
        Map<Long, Long> recorderByProcedureId) {
        if (procedureId == null) {
            return null;
        }
        Long nameKey = null;
        if (performerByProcedureId != null) {
            nameKey = performerByProcedureId.get(procedureId);
        }
        if (nameKey == null && recorderByProcedureId != null) {
            nameKey = recorderByProcedureId.get(procedureId);
        }
        return nameKey;
    }

    /**
     * Procedure 记录 → 槽位 DTO（含 procedureId、status、recordedTime）。
     */
    private NurseStationOrderExecuteSlotDto toSlotDto(ProcedureRecordQueryModel record, LocalDateTime planTime) {
        if (record == null) {
            return null;
        }
        NurseStationOrderExecuteSlotDto slot = new NurseStationOrderExecuteSlotDto();
        slot.setPlanTime(planTime);
        slot.setProcedureId(record.getId());
        slot.setStatusEnum(record.getStatusEnum());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, record.getStatusEnum());
        if (status != null) {
            slot.setStatusEnumText(status.getDisplay());
        }
        // recordedTime 供已执行/不执行 Tab 展示护士实际操作时刻
        slot.setRecordedTime(record.getRecordedTime());
        return slot;
    }

    /**
     * 执行记录历史：长期同 occurrence 组内终态事实行分桶（含已被 CANCEL 对冲的 COMPLETED）。
     *
     * @param buckets       四类槽位桶
     * @param group         同 occurrence 的 Procedure 组
     * @param occurrenceKey 计划时点（秒级）
     */
    private void appendHistoryTerminalSlots(
        SlotBuckets buckets,
        List<ProcedureRecordQueryModel> group,
        LocalDateTime occurrenceKey) {
        if (group == null || group.isEmpty()) {
            return;
        }
        for (ProcedureRecordQueryModel record : group) {
            if (record == null) {
                continue;
            }
            String status = record.getStatusEnum();
            NurseStationOrderExecuteSlotDto slot = this.toSlotDto(record, occurrenceKey);
            if (slot == null) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(status)) {
                buckets.executedSlots.add(slot);
            } else if (EventStatus.STOPPED.getCode().equals(status)) {
                buckets.skippedSlots.add(slot);
            } else if (EventStatus.isCancelExecutionStatusCode(status)) {
                buckets.cancelledSlots.add(slot);
            }
        }
    }

    /**
     * 执行记录历史：临时嘱终态事实行分桶（含对冲前 COMPLETED）。
     *
     * @param buckets    四类槽位桶
     * @param procedures 该 request 下全部 Procedure
     */
    private void appendHistoryTemporaryTerminalSlots(
        SlotBuckets buckets,
        List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return;
        }
        for (ProcedureRecordQueryModel record : procedures) {
            if (record == null) {
                continue;
            }
            String status = record.getStatusEnum();
            if (!EventStatus.COMPLETED.getCode().equals(status)
                && !EventStatus.STOPPED.getCode().equals(status)
                && !EventStatus.isCancelExecutionStatusCode(status)) {
                continue;
            }
            NurseStationOrderExecuteSlotDto slot = this.toTemporaryTerminalSlotDto(record);
            if (slot == null) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(status)) {
                buckets.executedSlots.add(slot);
            } else if (EventStatus.STOPPED.getCode().equals(status)) {
                buckets.skippedSlots.add(slot);
            } else {
                buckets.cancelledSlots.add(slot);
            }
        }
    }

    /**
     * 槽位列表按 planTime 升序排序。
     */
    private void sortSlots(List<NurseStationOrderExecuteSlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        slots.sort(Comparator.comparing(NurseStationOrderExecuteSlotDto::getPlanTime,
            Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 批量挂载 bindItems（BR-IP-EXE-013）。
     *
     * <p>药嘱：用法 {@code method_code}（术语 code）→ {@code USAGE} 来源绑定模板；
     * 诊嘱：按 activityId 查 {@code ACTIVITY} 来源绑定（按 targetItemTable 区分诊疗/耗材）。
     * <b>标本合管 / 耗材</b>：检验已合管或目标为器材定义时，本列表<strong>不</strong>挂载
     * （AS-BAS-CFG-001 §7.7；住院开立与执行均不自动带耗材）。药嘱用法绑耗材仍按模板挂载。</p>
     *
     * @param organizationUnitId 机构主键，模板查询 org 收窄；可 null
     */
    private List<NurseStationOrderExecuteBindItemDto> loadBindItems(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        Long organizationUnitId,
        Map<String, String> unitLabelByCode,
        Map<String, List<AdviceOrderBindRowDto>> bindRowsCache) {
        List<NurseStationOrderExecuteBindItemDto> bindItems = new ArrayList<>();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            // 药嘱：用法 method_code（术语 code）→ USAGE 来源绑定模板
            String methodCode = null;
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getMethodCode())) {
                methodCode = medDetail.getMethodCode().trim();
            }
            if (StringUtils.isNotEmpty(methodCode)) {
                List<AdviceOrderBindRowDto> rows = this.resolveBindRowsCached(
                    DoctorStationAdviceConstants.BIND_TYPE_USAGE,
                    methodCode,
                    organizationUnitId,
                    bindRowsCache);
                bindItems.addAll(this.mapBindRows(rows, unitLabelByCode));
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            // 诊嘱：住院不自动带耗材——已合管短路；未合管仅保留非耗材模板目标（如诊疗绑诊疗）
            Long activityId = order.getActivityId();
            if (svcDetail != null && svcDetail.getActivityId() != null) {
                activityId = svcDetail.getActivityId();
            }
            if (activityId != null) {
                // Pre：合管已配 → 执行侧不挂 bindItems
                boolean mergeConfigured =
                    specimenTubeMergeBindResolveSupport.findMergeDeviceIdByActivityId(activityId).isPresent();
                if (!mergeConfigured) {
                    List<AdviceOrderBindRowDto> rows = this.resolveBindRowsCached(
                        DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY,
                        String.valueOf(activityId),
                        organizationUnitId,
                        bindRowsCache);
                    List<NurseStationOrderExecuteBindItemDto> mapped =
                        this.mapBindRows(rows, unitLabelByCode);
                    for (NurseStationOrderExecuteBindItemDto item : mapped) {
                        // 跳过耗材目标（deviceId 有值）
                        if (item == null || item.getDeviceId() != null) {
                            continue;
                        }
                        bindItems.add(item);
                    }
                }
            }
        }
        return bindItems;
    }

    /**
     * 按绑定类型+源键+机构解析模板行（请求内缓存，禁同键重复打库）。
     *
     * @param bindType           绑定类型
     * @param sourceItemKey      源键
     * @param organizationUnitId 机构；可空
     * @param bindRowsCache      缓存；空则直接查
     * @return 模板行
     */
    private List<AdviceOrderBindRowDto> resolveBindRowsCached(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId,
        Map<String, List<AdviceOrderBindRowDto>> bindRowsCache) {
        if (StringUtils.isEmpty(bindType) || StringUtils.isEmpty(sourceItemKey)) {
            return Collections.emptyList();
        }
        String cacheKey = bindType + "|" + sourceItemKey + "|" + organizationUnitId;
        if (bindRowsCache != null && bindRowsCache.containsKey(cacheKey)) {
            List<AdviceOrderBindRowDto> cached = bindRowsCache.get(cacheKey);
            if (cached == null) {
                return Collections.emptyList();
            }
            return cached;
        }
        List<AdviceOrderBindRowDto> rows = orderBindQueryLoader.listBindingsBySourceItemKey(
            bindType, sourceItemKey, organizationUnitId);
        if (rows == null) {
            rows = Collections.emptyList();
        }
        if (bindRowsCache != null) {
            bindRowsCache.put(cacheKey, rows);
        }
        return rows;
    }

    /**
     * 长期医嘱：用上次 ORDER_EXECUTE 派生单数量覆盖 bindItems 模板量。
     *
     * <p>批量按 based_on 反查绑定诊疗 SR / 绑定耗材 DR（含取消软删记录），按主键取最新一条，
     * 写回 {@code quantityNumerator}（分母置 1）。临时医嘱不覆盖。无历史时保持模板量。</p>
     *
     * @param rowsByEncounter 就诊 → 已装配执行行（含 bindItems）
     */
    private void overlayLongTermBindQuantitiesFromLastExecution(
        Map<Long, List<NurseStationOrderExecuteRowDto>> rowsByEncounter) {
        if (rowsByEncounter == null || rowsByEncounter.isEmpty()) {
            return;
        }
        // Pre：收集长期医嘱主键，分药嘱/诊嘱两表批量反查派生单
        LinkedHashSet<Long> medBasedOnIds = new LinkedHashSet<>();
        LinkedHashSet<Long> svcBasedOnIds = new LinkedHashSet<>();
        for (List<NurseStationOrderExecuteRowDto> rows : rowsByEncounter.values()) {
            if (rows == null) {
                continue;
            }
            for (NurseStationOrderExecuteRowDto row : rows) {
                if (row == null || row.getOrderId() == null) {
                    continue;
                }
                // 仅长期：临时通常一次性执行，无需记住
                if (!TherapyType.LONG_TERM.getCode().equals(trimToEmpty(row.getTherapyEnum()))) {
                    continue;
                }
                if (row.getBindItems() == null || row.getBindItems().isEmpty()) {
                    continue;
                }
                if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
                    medBasedOnIds.add(row.getOrderId());
                } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    svcBasedOnIds.add(row.getOrderId());
                }
            }
        }
        if (medBasedOnIds.isEmpty() && svcBasedOnIds.isEmpty()) {
            return;
        }
        // activity 键：basedOnTable|basedOnId|activityId → 最新数量
        Map<String, BigDecimal> latestActivityQtyByKey = new HashMap<>();
        // device 键：basedOnTable|basedOnId|deviceId → 最新数量
        Map<String, BigDecimal> latestDeviceQtyByKey = new HashMap<>();
        Map<String, Long> latestActivityIdByKey = new HashMap<>();
        Map<String, Long> latestDeviceIdByKey = new HashMap<>();
        this.collectLatestBindActivityQuantities(
            TableNameConstants.MED_MEDICATION_REQUEST, medBasedOnIds, latestActivityQtyByKey, latestActivityIdByKey);
        this.collectLatestBindDeviceQuantities(
            TableNameConstants.MED_MEDICATION_REQUEST, medBasedOnIds, latestDeviceQtyByKey, latestDeviceIdByKey);
        this.collectLatestBindActivityQuantities(
            TableNameConstants.WKF_SERVICE_REQUEST, svcBasedOnIds, latestActivityQtyByKey, latestActivityIdByKey);
        this.collectLatestBindDeviceQuantities(
            TableNameConstants.WKF_SERVICE_REQUEST, svcBasedOnIds, latestDeviceQtyByKey, latestDeviceIdByKey);
        if (latestActivityQtyByKey.isEmpty() && latestDeviceQtyByKey.isEmpty()) {
            return;
        }
        // Asm：写回长期行 bindItems 数量
        for (List<NurseStationOrderExecuteRowDto> rows : rowsByEncounter.values()) {
            if (rows == null) {
                continue;
            }
            for (NurseStationOrderExecuteRowDto row : rows) {
                if (row == null || row.getOrderId() == null) {
                    continue;
                }
                if (!TherapyType.LONG_TERM.getCode().equals(trimToEmpty(row.getTherapyEnum()))) {
                    continue;
                }
                List<NurseStationOrderExecuteBindItemDto> bindItems = row.getBindItems();
                if (bindItems == null || bindItems.isEmpty()) {
                    continue;
                }
                String basedOnTable;
                if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
                    basedOnTable = TableNameConstants.MED_MEDICATION_REQUEST;
                } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                    basedOnTable = TableNameConstants.WKF_SERVICE_REQUEST;
                } else {
                    continue;
                }
                for (NurseStationOrderExecuteBindItemDto bindItem : bindItems) {
                    if (bindItem == null) {
                        continue;
                    }
                    if (bindItem.getActivityId() != null) {
                        String key = this.buildBindTargetQtyKey(
                            basedOnTable, row.getOrderId(), "A", bindItem.getActivityId());
                        BigDecimal qty = latestActivityQtyByKey.get(key);
                        if (qty != null) {
                            bindItem.setQuantityNumerator(qty);
                            bindItem.setQuantityDenominator(BigDecimal.ONE);
                        }
                        continue;
                    }
                    if (bindItem.getDeviceId() != null) {
                        String key = this.buildBindTargetQtyKey(
                            basedOnTable, row.getOrderId(), "D", bindItem.getDeviceId());
                        BigDecimal qty = latestDeviceQtyByKey.get(key);
                        if (qty != null) {
                            bindItem.setQuantityNumerator(qty);
                            bindItem.setQuantityDenominator(BigDecimal.ONE);
                        }
                    }
                }
            }
        }
    }

    /**
     * 批量装载绑定诊疗上次执行数量索引。
     *
     * <p>优先取 ORDER_EXECUTE 收费明细 {@code quantityValue}（历史派生 SR 曾写死 1，账单量才是护士手改真值）；
     * 无账单时回落派生 SR.quantity。允许 0（本趟不带出的记忆）。</p>
     */
    private void collectLatestBindActivityQuantities(
        String basedOnTable,
        Collection<Long> basedOnIds,
        Map<String, BigDecimal> latestQtyByKey,
        Map<String, Long> latestRecordIdByKey) {
        if (basedOnIds == null || basedOnIds.isEmpty()) {
            return;
        }
        List<ServiceRequest> derivedList =
            serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
        if (derivedList == null || derivedList.isEmpty()) {
            return;
        }
        String orderExecute = GenerateSource.ORDER_EXECUTE.getCode();
        Map<Long, ServiceRequest> derivedById = new HashMap<>();
        List<Long> serviceIds = new ArrayList<>();
        for (ServiceRequest derived : derivedList) {
            if (derived == null || derived.getId() == null || derived.getBasedOnId() == null) {
                continue;
            }
            // 仅医嘱执行派生；跳过划价/其它来源
            if (!orderExecute.equals(trimToEmpty(derived.getGenerateSourceEnum()))) {
                continue;
            }
            if (derived.getActivityId() == null) {
                continue;
            }
            derivedById.put(derived.getId(), derived);
            serviceIds.add(derived.getId());
            BigDecimal qty = derived.getQuantity();
            // 先用派生 SR 数量占位；后续有账单则覆盖
            if (qty == null || qty.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            String key = this.buildBindTargetQtyKey(
                basedOnTable, derived.getBasedOnId(), "A", derived.getActivityId());
            Long previousId = latestRecordIdByKey.get(key);
            if (previousId != null && previousId >= derived.getId()) {
                continue;
            }
            latestRecordIdByKey.put(key, derived.getId());
            latestQtyByKey.put(key, qty);
        }
        if (serviceIds.isEmpty()) {
            return;
        }
        // Pre：按派生 SR 批量拉 ORDER_EXECUTE 账单，用账单数量覆盖（纠正历史 SR=1）
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setServiceIds(serviceIds);
        List<ChargeItem> charges = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
        if (charges == null || charges.isEmpty()) {
            return;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getId() == null || charge.getServiceId() == null) {
                continue;
            }
            if (!orderExecute.equals(trimToEmpty(charge.getGenerateSourceEnum()))) {
                continue;
            }
            ServiceRequest derived = derivedById.get(charge.getServiceId());
            if (derived == null || derived.getActivityId() == null || derived.getBasedOnId() == null) {
                continue;
            }
            BigDecimal qty = charge.getQuantityValue();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            String key = this.buildBindTargetQtyKey(
                basedOnTable, derived.getBasedOnId(), "A", derived.getActivityId());
            Long previousId = latestRecordIdByKey.get(key);
            long chargeRank = charge.getId();
            // 账单主键更大则覆盖（纠正历史派生 SR 写死 1）
            if (previousId != null && previousId >= chargeRank) {
                continue;
            }
            latestRecordIdByKey.put(key, chargeRank);
            latestQtyByKey.put(key, qty);
        }
    }

    /**
     * 批量装载绑定耗材上次执行数量索引。
     *
     * <p>优先 ORDER_EXECUTE 账单数量；无则回落派生 DR.quantity；跳过退单负向行；允许 0。</p>
     */
    private void collectLatestBindDeviceQuantities(
        String basedOnTable,
        Collection<Long> basedOnIds,
        Map<String, BigDecimal> latestQtyByKey,
        Map<String, Long> latestRecordIdByKey) {
        if (basedOnIds == null || basedOnIds.isEmpty()) {
            return;
        }
        List<DeviceRequestListModel> derivedList =
            deviceRequestQueryService.listDeviceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
        if (derivedList == null || derivedList.isEmpty()) {
            return;
        }
        String orderExecute = GenerateSource.ORDER_EXECUTE.getCode();
        Map<Long, DeviceRequestListModel> derivedById = new HashMap<>();
        List<Long> serviceIds = new ArrayList<>();
        for (DeviceRequestListModel derived : derivedList) {
            if (derived == null || derived.getId() == null || derived.getBasedOnId() == null) {
                continue;
            }
            if (!orderExecute.equals(trimToEmpty(derived.getGenerateSourceEnum()))) {
                continue;
            }
            // 退单负向行不参与回显
            if (derived.getRefundDeviceId() != null) {
                continue;
            }
            if (derived.getDeviceDefId() == null) {
                continue;
            }
            derivedById.put(derived.getId(), derived);
            serviceIds.add(derived.getId());
            BigDecimal qty = derived.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            String key = this.buildBindTargetQtyKey(
                basedOnTable, derived.getBasedOnId(), "D", derived.getDeviceDefId());
            Long previousId = latestRecordIdByKey.get(key);
            if (previousId != null && previousId >= derived.getId()) {
                continue;
            }
            latestRecordIdByKey.put(key, derived.getId());
            latestQtyByKey.put(key, qty);
        }
        if (serviceIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setServiceIds(serviceIds);
        List<ChargeItem> charges = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
        if (charges == null || charges.isEmpty()) {
            return;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getId() == null || charge.getServiceId() == null) {
                continue;
            }
            if (!orderExecute.equals(trimToEmpty(charge.getGenerateSourceEnum()))) {
                continue;
            }
            DeviceRequestListModel derived = derivedById.get(charge.getServiceId());
            if (derived == null || derived.getDeviceDefId() == null || derived.getBasedOnId() == null) {
                continue;
            }
            BigDecimal qty = charge.getQuantityValue();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            String key = this.buildBindTargetQtyKey(
                basedOnTable, derived.getBasedOnId(), "D", derived.getDeviceDefId());
            Long previousId = latestRecordIdByKey.get(key);
            long chargeRank = charge.getId();
            if (previousId != null && previousId >= chargeRank) {
                continue;
            }
            latestRecordIdByKey.put(key, chargeRank);
            latestQtyByKey.put(key, qty);
        }
    }

    /**
     * 绑定目标数量索引键：来源表|来源主键|A/D|目标主键。
     */
    private String buildBindTargetQtyKey(
        String basedOnTable, Long basedOnId, String targetKind, Long targetId) {
        return basedOnTable + "|" + basedOnId + "|" + targetKind + "|" + targetId;
    }

    /**
     * 绑定模板行 → 执行 bindItem DTO；按 bindType/targetItemTable 回填 activityId 或 deviceId。
     */
    private List<NurseStationOrderExecuteBindItemDto> mapBindRows(
        List<AdviceOrderBindRowDto> rows,
        Map<String, String> unitLabelByCode) {
        List<NurseStationOrderExecuteBindItemDto> mapped = new ArrayList<>();
        if (rows == null) {
            return mapped;
        }
        for (AdviceOrderBindRowDto row : rows) {
            if (row == null) {
                continue;
            }
            NurseStationOrderExecuteBindItemDto item = new NurseStationOrderExecuteBindItemDto();
            item.setBindingId(row.getBindingId());
            item.setBindType(row.getBindType());
            item.setDisplayName(row.getTargetItemName());
            item.setBusNo(row.getBusNo());
            item.setSpecificationText(row.getSpecificationText());
            item.setUnitPrice(row.getUnitPrice());
            item.setChargeItemDefinitionId(row.getChargeItemDefinitionId());
            item.setQuantityNumerator(row.getQuantityNumerator());
            item.setQuantityDenominator(row.getQuantityDenominator());
            item.setUnitCode(row.getUnitCode());
            if (StringUtils.isNotEmpty(row.getUnitName())) {
                item.setUnitCode_dictText(row.getUnitName());
            } else {
                item.setUnitCode_dictText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getUnitCode()));
            }
            item.setPriority(row.getPriority());
            this.applyBindTargetIds(item, row);
            mapped.add(item);
        }
        return mapped;
    }

    /**
     * 按绑定类型与目标表回填 {@code activityId}/{@code deviceId}。
     */
    private void applyBindTargetIds(
        NurseStationOrderExecuteBindItemDto item,
        AdviceOrderBindRowDto row) {
        if (item == null || row == null || row.getTargetItemId() == null) {
            return;
        }
        String targetTable = trimToEmpty(row.getTargetItemTable());
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(targetTable)) {
            item.setDeviceId(row.getTargetItemId());
            return;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(targetTable)) {
            item.setActivityId(row.getTargetItemId());
            return;
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE.equals(row.getBindType())) {
            item.setDeviceId(row.getTargetItemId());
            return;
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(row.getBindType())) {
            item.setActivityId(row.getTargetItemId());
        }
    }

    /**
     * 组内医嘱行排序：开立时间（{@code reqOpenedTime}，空回落签发）倒序；成组药按组内最晚开立时刻聚邻。
     *
     * @param rows 待排序医嘱行
     * @return 排序后的新列表
     */
    private List<NurseStationOrderExecuteRowDto> sortRows(List<NurseStationOrderExecuteRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<NurseStationOrderExecuteRowDto> copy = new ArrayList<>(rows);
        Map<Long, LocalDateTime> groupMaxOpenedTime = new HashMap<>();
        Map<Long, LocalDateTime> medicationOpenedTimeByOrderId = new HashMap<>();
        for (NurseStationOrderExecuteRowDto row : copy) {
            LocalDateTime sortTime = this.resolveRowOpenedTimeForSort(row);
            Long groupId = row.getGroupId();
            if (groupId != null && sortTime != null) {
                LocalDateTime existing = groupMaxOpenedTime.get(groupId);
                // 组内取最晚开立时刻，保证同组执行行连续展示。
                if (existing == null || sortTime.isAfter(existing)) {
                    groupMaxOpenedTime.put(groupId, sortTime);
                }
            }
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
                && row.getOrderId() != null
                && sortTime != null) {
                medicationOpenedTimeByOrderId.put(row.getOrderId(), sortTime);
            }
        }
        copy.sort(Comparator
            .comparing((NurseStationOrderExecuteRowDto row) ->
                    this.resolveExecuteClusterOpenedTime(row, groupMaxOpenedTime, medicationOpenedTimeByOrderId),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NurseStationOrderExecuteRowDto::getGroupId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveExecuteSkinTestClusterKey, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveExecuteSkinTestCompanionRank)
            .thenComparing(NurseStationOrderExecuteRowDto::getOrderId, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    /**
     * 执行列表簇排序键：成组药用组内最晚开立时刻；皮试 companion 用锚定皮试药开立时刻。
     *
     * @param row                           执行行
     * @param groupMaxOpenedTime            成组 groupId → 最晚开立时刻
     * @param medicationOpenedTimeByOrderId 药品 orderId → 开立时刻
     * @return 簇排序时刻；无时刻时为 null
     */
    private LocalDateTime resolveExecuteClusterOpenedTime(
        NurseStationOrderExecuteRowDto row,
        Map<Long, LocalDateTime> groupMaxOpenedTime,
        Map<Long, LocalDateTime> medicationOpenedTimeByOrderId) {
        // companion：跟锚皮试药同簇
        if (row != null && row.getBasedOnMedicationRequestId() != null
            && medicationOpenedTimeByOrderId != null) {
            LocalDateTime linked = medicationOpenedTimeByOrderId.get(row.getBasedOnMedicationRequestId());
            if (linked != null) {
                return linked;
            }
        }
        if (row != null && row.getGroupId() != null) {
            LocalDateTime groupOpened = groupMaxOpenedTime.get(row.getGroupId());
            if (groupOpened != null) {
                return groupOpened;
            }
        }
        return this.resolveRowOpenedTimeForSort(row);
    }

    /**
     * 皮试聚邻键：皮试药=自身 orderId；companion=basedOn；其它 null。
     *
     * @param row 执行行
     * @return 聚邻键
     */
    private Long resolveExecuteSkinTestClusterKey(NurseStationOrderExecuteRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return row.getBasedOnMedicationRequestId();
        }
        // 药品行：与 companion 同键时聚邻（非皮试药独自一簇无副作用）
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
            return row.getOrderId();
        }
        return null;
    }

    /**
     * 同皮试簇内次序：药品 0，companion 1，其它 2。
     *
     * @param row 执行行
     * @return 次序
     */
    private int resolveExecuteSkinTestCompanionRank(NurseStationOrderExecuteRowDto row) {
        if (row == null) {
            return 2;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return 1;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
            return 0;
        }
        return 2;
    }

    /**
     * 行排序时刻：优先 {@code reqOpenedTime}，空回落 {@code reqAuthoredTime}。
     *
     * @param row 执行行
     * @return 排序时刻；均空时 null
     */
    private LocalDateTime resolveRowOpenedTimeForSort(NurseStationOrderExecuteRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getReqOpenedTime() != null) {
            return row.getReqOpenedTime();
        }
        return row.getReqAuthoredTime();
    }

    /**
     * occurrence 净态（读侧分桶与写 Gate 共用，避免多次往返误读历史 Procedure）。
     */
    private enum OccurrenceNetSlotState {
        /** 待执行（无终态或进行中）。 */
        PENDING,
        /** 执行-取消对冲后净值待执行（读侧 pending+cancelled 双 Tab 展示）。 */
        HEDGED_PENDING,
        /** 净已执行（COMPLETED 且未被 CANCEL 对冲）。 */
        EXECUTED,
        /** 已登记不执行（STOPPED）。 */
        SKIPPED,
        /** 已取消执行（孤立 CANCEL，无 refund 对冲 COMPLETED）。 */
        CANCELLED
    }

    /**
     * 四类槽位分桶容器（pending/executed/skipped/cancelled）。
     */
    private static class SlotBuckets {
        /** 待执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> pendingSlots;
        /** 已执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> executedSlots;
        /** 不执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> skippedSlots;
        /** 已取消执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> cancelledSlots;
        /** 本行是否已按一盒一点回传盒首锚点。 */
        private boolean packageBatchApplied;
        /** 本行是否已按拆零下限回传单位首槽。 */
        private boolean minSplitUnitBatchApplied;
        /** 一盒一点或拆零下限切批：pending 至多保留最早一个锚点。 */
        private boolean batchedAnchorApplied;
    }

    /**
     * 列表行装配结果：行 DTO + 是否已启用一盒一点。
     */
    private static class PackageBatchExpandOutcome {
        /** 执行列表行。 */
        private NurseStationOrderExecuteRowDto row;
        /** true 表示计划点为一盒一点盒首锚点，pending 须串行。 */
        private boolean packageBatchApplied;
    }

    /**
     * 长期计划点展开结果。
     */
    private static class PlannedOccurrenceExpandResult {
        /** 计划点集合。 */
        private Set<LocalDateTime> planTimes;
        /** 是否已成功按一盒一点回传盒首锚点。 */
        private boolean packageBatchApplied;
        /** 是否已成功按拆零单位下限切批回传单位首槽。 */
        private boolean minSplitUnitBatchApplied;
    }

    /**
     * 构造护士站可见性查询（启用 nurseStationQueue + encounterIdsIn + managedWardIds）。
     *
     * @param encounterIds   调用方请求的就诊主键；可空表示仅按管辖病区收窄
     * @param managedWardIds 当前操作者管辖病区；可空
     * @return 住院就诊分页查询入参
     */
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 长临筛选入参 → {@link TherapyType} code；{@code all} 或空时返回 null（不过滤）。
     *
     * @param therapyType {@link InpatientNurseStationConstants} 长临筛选码
     * @return {@code LONG_TERM}/{@code TEMPORARY} code，或 null
     */
    public String resolveTherapyEnum(String therapyType) {
        String normalized = trimToEmpty(therapyType);
        if (StringUtils.isEmpty(normalized)
            || InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 患者分组列表 → IF-IP-EXE-01 读结果包装。
     *
     * @param groups 按就诊分组的执行行；null 时 groups 为空列表
     * @return 读结果
     */
    public NurseStationOrderExecuteListResult toListResult(List<NurseStationOrderExecutePatientGroupDto> groups) {
        NurseStationOrderExecuteListResult result = new NurseStationOrderExecuteListResult();
        if (groups == null) {
            result.setGroups(Collections.emptyList());
            return result;
        }
        result.setGroups(groups);
        return result;
    }

    /**
     * 按 executeTab 收窄槽位回传（BR-IP-EXE-010/012）：仅当前 Tab 对应槽位且
     * planTime ∈ [planTimeFrom, executionDeadline]（下界可空）。
     *
     * @param row                医嘱执行行（原地修改四类槽位字段）
     * @param executeTab         pending / executed / skipped / cancelled
     * @param executionDeadline  计划点上界
     * @param planTimeFrom       计划点下界（含）；空表示不下界过滤
     */
    public void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline,
        LocalDateTime planTimeFrom) {
        if (row == null || executionDeadline == null) {
            return;
        }
        String tab = trimToEmpty(executeTab);
        List<NurseStationOrderExecuteSlotDto> pendingSource = row.getPendingSlots();
        List<NurseStationOrderExecuteSlotDto> executedSource = row.getExecutedSlots();
        List<NurseStationOrderExecuteSlotDto> skippedSource = row.getSkippedSlots();
        List<NurseStationOrderExecuteSlotDto> cancelledSource = row.getCancelledSlots();
        row.setPendingSlots(Collections.emptyList());
        row.setExecutedSlots(Collections.emptyList());
        row.setSkippedSlots(Collections.emptyList());
        row.setCancelledSlots(Collections.emptyList());
        if (InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)) {
            row.setPendingSlots(
                this.filterSlotsWithinPlanTimeRange(pendingSource, planTimeFrom, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)) {
            row.setExecutedSlots(
                this.filterSlotsWithinPlanTimeRange(executedSource, planTimeFrom, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)) {
            row.setSkippedSlots(
                this.filterSlotsWithinPlanTimeRange(skippedSource, planTimeFrom, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab)) {
            row.setCancelledSlots(
                this.filterSlotsWithinPlanTimeRange(cancelledSource, planTimeFrom, executionDeadline));
        }
    }

    /**
     * 兼容旧调用：仅上界过滤（planTimeFrom 为空）。
     *
     * @param row                医嘱执行行
     * @param executeTab         执行 Tab
     * @param executionDeadline  计划点上界
     */
    public void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline) {
        this.applyExecuteTabSlotFilter(row, executeTab, executionDeadline, null);
    }

    /**
     * 执行槽过滤：保留 planTime ∈ [planTimeFrom, executionDeadline] 的槽位；下界空则仅上界。
     *
     * @param slots              源槽列表
     * @param planTimeFrom       计划点下界（含）；可空
     * @param executionDeadline  执行截止时刻（上界，含）
     * @return 过滤后列表
     */
    private List<NurseStationOrderExecuteSlotDto> filterSlotsWithinPlanTimeRange(
        List<NurseStationOrderExecuteSlotDto> slots,
        LocalDateTime planTimeFrom,
        LocalDateTime executionDeadline) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<NurseStationOrderExecuteSlotDto> filtered = new ArrayList<>();
        for (NurseStationOrderExecuteSlotDto slot : slots) {
            if (slot == null || slot.getPlanTime() == null) {
                continue;
            }
            LocalDateTime planTime = slot.getPlanTime();
            // 上界：planTime ≤ executionDeadline
            if (planTime.isAfter(executionDeadline)) {
                continue;
            }
            // 下界：空则不过滤；有则 planTime ≥ planTimeFrom
            if (planTimeFrom != null && planTime.isBefore(planTimeFrom)) {
                continue;
            }
            filtered.add(slot);
        }
        return filtered;
    }
}
