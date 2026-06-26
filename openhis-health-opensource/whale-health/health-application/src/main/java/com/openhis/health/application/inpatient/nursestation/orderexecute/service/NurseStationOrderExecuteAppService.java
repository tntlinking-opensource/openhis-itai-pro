package com.openhis.health.application.inpatient.nursestation.orderexecute.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.application.inpatient.nursestation.orderexecute.assembler.NurseStationExecutionInventoryAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.assembler.NurseStationOrderExecuteListAssembler;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.mapper.NurseStationOrderExecuteMapper;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ExecuteNurseStationOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ListNurseStationOrderExecuteRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.SkipNurseStationOrderSlotsRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ListNurseStationOrderExecuteResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.SkipNurseStationOrderSlotsResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.InpatientTcmDecoctionExecuteSupport;
import com.openhis.health.module.querysupport.nursestation.InpatientNurseStationTcmSupport;
import com.openhis.health.module.querysupport.order.InpatientOrderLookupLoader;
import com.openhis.health.module.querysupport.nursestation.NurseStationOrderExecuteListLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.module.common.assembler.advice.InpatientMedicationRequestExecutionMeasureSupport;
import com.openhis.health.module.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogDispenseLocationResolveSupport;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.module.orchestration.nursestation.DeviceDispenseNurseWorkflowSupport;
import com.openhis.health.module.orchestration.nursestation.ProcedureExecutionPerformerOrchestrationSupport;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateExecutedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行应用服务（AS-IP-NUR-004，IF-IP-EXE-01/02/04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>读路径 IF-IP-EXE-01</b>：可见性收窄 → OrderInfo 骨架 → 域明细预热 → 频次展开槽位 → bindItems 挂载 → Tab 过滤</li>
 *   <li><b>写路径 IF-IP-EXE-02</b>：Gate 校验 → <b>仅药嘱</b>库存 FIFO dry-run → Procedure 三态 → 长临分轨发药/账单 → 绑定项派生</li>
 *   <li><b>写路径 IF-IP-EXE-04</b>：Gate 校验（不做库存 Gate）→ Procedure STOPPED 登记</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationOrderExecute/{methodName}}。</p>
 *
 * <p>关键业务口径（与 AS v2.5 对齐）：</p>
 * <ul>
 *   <li>BR-IP-EXE-014：库存 Gate <b>仅药嘱</b>；绑定耗材不参与 Gate/扣减，由耗材发放等其他页面统一处理</li>
 *   <li>BR-IP-EXE-019：绑定诊疗走 {@code createExecutedServiceRequest}；绑定耗材走 DeviceRequest → 草稿 Dispense → promote 待发（对齐护士划价，不 Gate 库存）</li>
 *   <li>BR-IP-EXE-022：批量写路径在同一 AppService UoW 内提交，任一槽位失败整批回滚</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationOrderExecuteAppService {

    /** 写路径操作人门控。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 当前医务人员解析。 */
    /** 管理病区范围。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 住院就诊上下文：可见 encounter 收窄。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    /** 医嘱全景读模型。 */
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;
    /** 药品申请读侧。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗申请读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** Procedure 读侧。 */
    private final ProcedureQueryService procedureQueryService;
    /** 发药读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** Procedure 写侧。 */
    private final ProcedureDomainService procedureDomainService;
    /** 发药写侧。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 收费写侧。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 收费读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 收费定价读侧（执行新建费用时补全 definitionId）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;
    /** 诊疗写侧（绑定派生）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 耗材请求写侧（绑定耗材派生）。 */
    private final DeviceRequestDomainService deviceRequestDomainService;
    /** 耗材发放写侧（绑定耗材派生）。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    /** 耗材发放读侧（绑定耗材 Charge 锚点）。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final DeviceDispenseNurseWorkflowSupport deviceDispenseNurseWorkflowSupport;
    /** Procedure 完成后登记主执行人（cln_procedure_performer，1:1）。 */
    private final ProcedureExecutionPerformerOrchestrationSupport procedureExecutionPerformerOrchestrationSupport;
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 映射器。 */
    private final NurseStationOrderExecuteMapper nurseStationOrderExecuteMapper;
    /** 列表装配。 */
    private final NurseStationOrderExecuteListAssembler nurseStationOrderExecuteListAssembler;
    private final NurseStationOrderExecuteListLoader nurseStationOrderExecuteListLoader;
    /** 库存 Gate FIFO。 */
    private final NurseStationExecutionInventoryAssembler nurseStationExecutionInventoryAssembler;

    /** 中药整方扩行。 */
    private final InpatientNurseStationTcmSupport inpatientNurseStationTcmSupport;

    /** 住院执行代煎费（BR-IP-TCM-008）。 */
    private final InpatientTcmDecoctionExecuteSupport inpatientTcmDecoctionExecuteSupport;
    /** 药品目录批量读侧（执行新建 Charge 比例换算单价）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    /** 发放库房模板解析（绑定耗材 performLocation，对齐护士划价）。 */
    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;

    /**
     * IF-IP-EXE-01：医嘱执行列表查询。
     *
     * @param request 多选就诊、Tab、长临、截止时间
     * @return 按就诊分组的医嘱行（含槽位与 bindItems）
     */
    public ListNurseStationOrderExecuteResponse listNurseStationOrderExecute(ListNurseStationOrderExecuteRequest request) {
        return nurseStationOrderExecuteMapper.toListResponse(
            nurseStationOrderExecuteListLoader.listNurseStationOrderExecute(
                nurseStationOrderExecuteMapper.toListQuery(request)));
    }

    /**
     * IF-IP-EXE-02：批量执行医嘱计划点。
     *
     * @param request 待执行槽位列表
     * @return 处理结果
     */
    public ExecuteNurseStationOrdersResponse executeNurseStationOrders(ExecuteNurseStationOrdersRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求不能为空");
        }
        // 写槽位入参 Gate：批量上限 50、锚点完整、执行路径要求 actualTime（BR-IP-EXE-014 前置）
        this.assertWriteSlotsGate(request.getItems(), true);
        // operatorId = 登录账号；performerId = 当前医务人员（可能不同，如代执行场景）
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_EXE_303,
            "未登录或登录已失效，无法执行医嘱");
        // performerId 写入 Procedure/Charge 的执行人维度
        Long performerId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_EXE_303,
            "未解析到当前医务人员，无法执行医嘱");
        // Pre·中药整方扩行：同 planTime 扩全方各味药槽位（Gate 已按扩行前计）
        List<NurseStationOrderExecuteWriteSlotDto> items =
            this.expandExecuteWriteSlotsByTcmPrescription(request.getItems());
        // 预加载：OrderInfo、可见性、药诊明细、Procedure 历史——避免写循环内单条查库
        WriteContextBundle context = this.prepareWriteContext(items);
        // Pre：批量预热产品定义/定价行/临时发药单，避免写循环内按槽位重复查库
        this.warmWritePathCatalogCaches(items, context);
        // 逐槽位业务 Gate：可见性 + VERIFIED + 该 planTime 未被 Procedure 终态占用（EX-IP-EXE-301）
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            this.assertWriteSlotExecutable(item, context, performerId);
        }
        // === 库存 Gate 阶段（整批 dry-run，任一药品不足则本批全部拒绝）===
        List<NurseStationExecutionInventoryAssembler.InventoryGateDemandLine> gateLines =
            this.buildInventoryGateDemands(items, context);
        // FIFO dry-run：按槽位顺序分段分配，跨批时 working 库存逐行扣减（BR-IP-EXE-014/016）
        NurseStationExecutionInventoryAssembler.MedicationFifoBatchPlan fifoBatchPlan =
            nurseStationExecutionInventoryAssembler.prepareMedicationFifoBatchPlan(gateLines);
        Map<String, List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment>> fifoSegmentsBySlotKey =
            this.indexFifoSegmentsByMedicationSlot(gateLines, items, fifoBatchPlan);
        int processed = 0;
        Set<String> decoctionKeys = new LinkedHashSet<>();
        // 逐槽位写 Procedure + Dispense/Charge + bindItems；同事务，失败即回滚整批
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            try {
                List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment> fifoSegments =
                    fifoSegmentsBySlotKey.get(this.buildMedicationGateSlotKey(item));
                this.executeSingleSlot(item, context, operatorId, performerId, fifoSegments, decoctionKeys);
                processed = processed + 1;
            } catch (BusinessException ex) {
                // 业务拒绝（Gate/库存/槽位变更）整批回滚
                throw ex;
            } catch (RuntimeException ex) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_304,
                    "医嘱执行失败，请稍后重试");
            }
        }
        if (!context.getDeviceInventoryDecreaseAccumulator().isEmpty()) {
            inventoryItemDomainService.decreaseQuantitiesMerged(
                operatorId, context.getDeviceInventoryDecreaseAccumulator());
        }
        return nurseStationOrderExecuteMapper.toExecuteResponse(processed);
    }

    /**
     * IF-IP-EXE-04：批量登记计划点不执行。
     *
     * @param request 待不执行槽位列表
     * @return 处理结果
     */
    public SkipNurseStationOrderSlotsResponse skipNurseStationOrderSlots(SkipNurseStationOrderSlotsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求不能为空");
        }
        // 不执行路径：不要求 actualTime，且 deliberately 不做库存 Gate（BR-IP-EXE-021）
        this.assertWriteSlotsGate(request.getItems(), false);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_EXE_403,
            "未登录或登录已失效，无法登记不执行");
        // Pre·中药整方扩行：不执行亦须覆盖方内全部味药行
        List<NurseStationOrderExecuteWriteSlotDto> items =
            this.expandExecuteWriteSlotsByTcmPrescription(request.getItems());
        WriteContextBundle context = this.prepareWriteContext(items);
        LocalDateTime recordedTime = LocalDateTime.now();
        // 仅允许该 planTime 尚未被 Procedure 终态占用（EX-IP-EXE-401）
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            this.assertWriteSlotSkippable(item, context);
        }
        int processed = 0;
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            try {
                // 从预加载上下文取 OrderInfo，避免写循环内再查读模型
                InpatientOrderListSkeleton order = context.orderByKey.get(this.buildOrderKey(item));
                OrderExecutionContext orderCtx = context.resolveOrderContext(item);
                String requestTable = this.resolveRequestTable(item.getOrderType());
                Long requestId = item.getOrderId();
                // categoryEnum 写入 Procedure，供后续报表按长临归类
                String categoryEnum = this.resolveProcedureCategory(order, orderCtx);
                // 写 Procedure STOPPED，skipDuplicate 允许同一 planTime 幂等登记
                procedureDomainService.skipProcedureRecord(
                    nurseStationOrderExecuteMapper.toSkipProcedureRecordCommand(
                        item,
                        requestId,
                        requestTable,
                        order.getEncounterId(),
                        order.getPatientId(),
                        categoryEnum,
                        operatorId,
                        recordedTime));
                processed = processed + 1;
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                // 不执行写失败统一 EX-IP-EXE-403，同事务回滚
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_403,
                    "不执行登记失败，请稍后重试");
            }
        }
        return nurseStationOrderExecuteMapper.toSkipResponse(processed);
    }

    /**
     * 单槽位执行编排（Procedure + 长临 Dispense/Charge + 绑定项）。
     *
     * <p>主路径：create → start → complete 三态 Procedure（BR-IP-EXE-015）；
     * occurrence=planTime，recorded=actualTime；随后按 orderType 分轨写发药/账单，
     * 最后处理前端勾选的 bindItems（BR-IP-EXE-019～020）。</p>
     *
     * @param item             写槽位（含 planTime、actualTime、bindItems）
     * @param context          预加载上下文
     * @param operatorId       登录操作人
     * @param performerId      当前医务人员（执行人）
     * @param fifoSegments 本槽位 FIFO 分段（仅药嘱；诊嘱为 null 或空）
     */
    private void executeSingleSlot(
        NurseStationOrderExecuteWriteSlotDto item,
        WriteContextBundle context,
        Long operatorId,
        Long performerId,
        List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment> fifoSegments,
        Set<String> decoctionKeys) {
        InpatientOrderListSkeleton order = context.orderByKey.get(this.buildOrderKey(item));
        OrderExecutionContext orderCtx = context.resolveOrderContext(item);
        // requestTable/requestId 供 Procedure 锚定到具体 Request 聚合
        String requestTable = this.resolveRequestTable(item.getOrderType());
        Long requestId = item.getOrderId();
        // Procedure category 取自药/诊明细的长临，回落 OrderInfo
        String categoryEnum = this.resolveProcedureCategory(order, orderCtx);
        LocalDateTime actualTime = item.getActualTime();
        if (actualTime == null) {
            // 防御：Gate 已校验 actualTime，此处仅兜底
            actualTime = LocalDateTime.now();
        }
        // ① 创建 Procedure 锚点（skipDuplicate=true，防并发重复创建）
        CreateProcedureRecordCommand createCmd = nurseStationOrderExecuteMapper.toCreateProcedureRecordCommand(
            item, requestId, requestTable, order.getEncounterId(), order.getPatientId(), categoryEnum, operatorId);
        Long procedureId = procedureDomainService.createProcedureRecord(createCmd);
        // ② 启动执行
        procedureDomainService.startProcedureExecution(
            nurseStationOrderExecuteMapper.toStartProcedureExecutionCommand(procedureId, operatorId));
        // ③ 完成执行（recordedTime 由领域层取当前时刻）
        procedureDomainService.completeProcedureRecord(
            nurseStationOrderExecuteMapper.toCompleteProcedureRecordCommand(procedureId, operatorId));
        // Cmd：登记主执行人（与 Procedure 1:1 写入 cln_procedure_performer）。
        procedureExecutionPerformerOrchestrationSupport.registerPrimaryPerformer(
            procedureId, performerId, operatorId, actualTime);
        Long orgId = context.encounterOrgIds.get(order.getEncounterId());
        // ④ 主医嘱联动：药嘱 FIFO 分段新建 Dispense+Charge；诊嘱新建 Charge（长临同构，BR-IP-EXE-016/018）
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            this.executeMedicationPath(item, order, orderCtx, procedureId, operatorId, orgId, fifoSegments, context);
            this.maybeCreateTcmDecoctionChargeAfterExecute(
                item, order, orderCtx, procedureId, operatorId, orgId, decoctionKeys);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            this.executeServicePath(item, order, orderCtx, procedureId, operatorId, orgId, context);
        }
        // ⑤ 绑定项派生：列表回传 bindItems 非空时，按 activityId/deviceId 分支写下游聚合
        this.executeBindItems(
            item,
            order,
            operatorId,
            performerId,
            actualTime,
            procedureId,
            orgId,
            context);
    }

    /**
     * 药品执行：FIFO 分段新建 Dispense + Charge（长临同构，BR-IP-EXE-016/026）。
     *
     * <p>每次执行（含取消后再执行）均新建发放行与 {@code ORDER_EXECUTE} 待结算费用行，
     * 跨批时按段写入库位/批号/数量，与药房 1:1 分段口径一致。</p>
     */
    private void executeMedicationPath(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        OrderExecutionContext orderCtx,
        Long procedureId,
        Long operatorId,
        Long orgId,
        List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment> fifoSegments,
        WriteContextBundle context) {
        MedicationRequestListQueryModel med = orderCtx.medDetail;
        if (med == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "药品明细不存在");
        }
        if (fifoSegments == null || fifoSegments.isEmpty()) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_302, "库存 Gate 未通过");
        }
        String unitCode = InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseUnitCodeFromRequest(
            med.getUnitCode());
        ChargeItem template = this.findChargeTemplate(context, order.getEncounterId(),
            TableNameConstants.MED_MEDICATION_REQUEST, med.getId());
        ProductDefinitionDisplayDto medicationProduct =
            this.loadMedicationProductDefinition(context, med.getMedicationId());
        // Cmd：每 FIFO 分段各建一条 Dispense(PREPARATION) 与一条待结算 Charge
        for (NurseStationExecutionInventoryAssembler.MedicationFifoSegment segment : fifoSegments) {
            if (segment == null || segment.getQuantity() == null) {
                continue;
            }
            BigDecimal segmentQuantity = segment.getQuantity();
            Long dispenseId = medicationDispenseDomainService.generateDispenseFromExecution(
                nurseStationOrderExecuteMapper.toGenerateDispenseFromExecutionCommand(
                    med.getId(),
                    procedureId,
                    item.getPlanTime(),
                    segment.getLocationId(),
                    segment.getLotNumber(),
                    segmentQuantity,
                    unitCode,
                    med.getMedicationId(),
                    order.getEncounterId(),
                    order.getPatientId()));
            ChargeItemDefinition priceLine = this.requirePriceLineForExecution(
                context,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                med.getMedicationId(),
                null,
                segment.getLotNumber());
            chargeItemDomainService.createPlannedChargeItemFromOrderExecution(
                nurseStationOrderExecuteMapper.toCreatePlannedChargeItemFromOrderExecutionCommand(
                    order,
                    template,
                    priceLine,
                    item.getAccountId(),
                    procedureId,
                    item.getPlanTime(),
                    segmentQuantity,
                    unitCode,
                    medicationProduct,
                    dispenseId,
                    TableNameConstants.MED_MEDICATION_DISPENSE,
                    operatorId,
                    orgId));
        }
    }

    /**
     * 诊疗执行：每次 planTime 新建待结算 Charge（长临同构，BR-IP-EXE-018）。
     */
    private void executeServicePath(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        OrderExecutionContext orderCtx,
        Long procedureId,
        Long operatorId,
        Long orgId,
        WriteContextBundle context) {
        ServiceRequestListModel svc = orderCtx.svcDetail;
        if (svc == null || svc.getQuantity() == null || svc.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_303,
                DoctorStationAdviceConstants.MSG_CHARGE_QUANTITY_REQUIRED);
        }
        BigDecimal quantity = svc.getQuantity();
        String unitCode = trimToNull(svc.getUnitCode());
        Long serviceId = item.getOrderId();
        ChargeItem template = this.findChargeTemplate(context, order.getEncounterId(),
            TableNameConstants.WKF_SERVICE_REQUEST, serviceId);
        Long activityId = order.getActivityId();
        if (svc.getActivityId() != null) {
            activityId = svc.getActivityId();
        }
        Long preferredDefinitionId = null;
        if (template != null) {
            preferredDefinitionId = template.getDefinitionId();
        }
        ChargeItemDefinition priceLine = this.requirePriceLineForExecution(
            context,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            activityId,
            preferredDefinitionId,
            null);
        ProductDefinitionDisplayDto activityProduct =
            this.loadProductDefinition(context, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(
            nurseStationOrderExecuteMapper.toCreatePlannedChargeItemFromOrderExecutionCommand(
                order,
                template,
                priceLine,
                item.getAccountId(),
                procedureId,
                item.getPlanTime(),
                quantity,
                unitCode,
                activityProduct,
                null,
                null,
                operatorId,
                orgId));
    }

    /**
     * 绑定项写路径（BR-IP-EXE-019～020）。
     *
     * <p>前端勾选/回传的 bindItems 逐条处理：{@code activityId} 走绑定诊疗派生，
     * {@code deviceId} 走绑定耗材派生。无 bindItems 或空列表时直接返回，不影响主医嘱执行。</p>
     */
    private void executeBindItems(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        Long operatorId,
        Long performerId,
        LocalDateTime actualTime,
        Long parentProcedureId,
        Long orgId,
        WriteContextBundle context) {
        if (item.getBindItems() == null || item.getBindItems().isEmpty()) {
            // 护士未勾选绑定项：主医嘱执行完成即可，不派生下游
            return;
        }
        for (NurseStationOrderExecuteBindItemDto bindItem : item.getBindItems()) {
            if (bindItem == null) {
                // 忽略前端误传的空元素
                continue;
            }
            if (bindItem.getActivityId() != null) {
                // 绑定诊疗：派生独立 ServiceRequest + Procedure + Charge
                this.executeBindActivityItem(
                    item, order, bindItem, operatorId, performerId, actualTime, orgId, context);
            } else if (bindItem.getDeviceId() != null) {
                // 绑定耗材：DeviceRequest + Dispense + Charge； deliberately 不做库存 Gate/扣减
                this.executeBindDeviceItem(
                    item, order, bindItem, operatorId, performerId, parentProcedureId, orgId, context);
            }
            // activityId 与 deviceId 均为空时跳过：模板数据异常，不阻断主医嘱
        }
    }

    /**
     * 绑定诊疗派生（BR-IP-EXE-019 activity 分支）。
     *
     * <p>基于主医嘱 Request 锚点 {@code createExecutedServiceRequest}，随后为派生 Request
     * 独立走 Procedure 三态，并新建待结算 Charge（产品=ActivityDefinition）。</p>
     */
    private void executeBindActivityItem(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        Long operatorId,
        Long performerId,
        LocalDateTime actualTime,
        Long orgId,
        WriteContextBundle context) {
        // ① 派生已完成态 ServiceRequest，basedOn 指向主医嘱 Request
        CreateExecutedServiceRequestCommand command = new CreateExecutedServiceRequestCommand();
        command.setOperatorId(operatorId);
        CreateExecutedServiceRequestCommand.CreateExecutedServiceRequestItemCommand line =
            new CreateExecutedServiceRequestCommand.CreateExecutedServiceRequestItemCommand();
        line.setBasedOnTable(this.resolveRequestTable(item.getOrderType()));
        line.setBasedOnId(item.getOrderId());
        line.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        line.setActivityId(bindItem.getActivityId());
        line.setEncounterId(order.getEncounterId());
        line.setPatientId(order.getPatientId());
        line.setRequesterId(performerId);
        command.setItems(List.of(line));
        // 领域层返回派生 serviceRequestId，后续 Procedure/Charge 均挂此 id
        Long derivedServiceRequestId = serviceRequestDomainService.createExecutedServiceRequest(command);
        // ② 为派生 Request 创建独立 Procedure 三态（类目固定临时，与绑定项语义一致）
        NurseStationOrderExecuteWriteSlotDto derivedSlot = new NurseStationOrderExecuteWriteSlotDto();
        derivedSlot.setOrderId(derivedServiceRequestId);
        derivedSlot.setOrderType(ReadModelOrderTypeConstants.SERVICE_REQUEST);
        derivedSlot.setPlanTime(item.getPlanTime());
        derivedSlot.setActualTime(actualTime);
        CreateProcedureRecordCommand createCmd = nurseStationOrderExecuteMapper.toCreateProcedureRecordCommand(
            derivedSlot,
            derivedServiceRequestId,
            TableNameConstants.WKF_SERVICE_REQUEST,
            order.getEncounterId(),
            order.getPatientId(),
            TherapyType.TEMPORARY.getCode(),
            operatorId);
        Long bindProcedureId = procedureDomainService.createProcedureRecord(createCmd);
        // 绑定诊疗也需完整三态，与主医嘱 Procedure 独立
        procedureDomainService.startProcedureExecution(
            nurseStationOrderExecuteMapper.toStartProcedureExecutionCommand(bindProcedureId, operatorId));
        procedureDomainService.completeProcedureRecord(
            nurseStationOrderExecuteMapper.toCompleteProcedureRecordCommand(bindProcedureId, operatorId));
        // Cmd：绑定诊疗 Procedure 同样登记主执行人。
        procedureExecutionPerformerOrchestrationSupport.registerPrimaryPerformer(
            bindProcedureId, performerId, operatorId, actualTime);
        // ③ 新建待结算 Charge，service=派生 Request，product=ActivityDefinition
        BigDecimal quantity = this.resolveBindItemQuantity(bindItem);
        String unitCode = trimToNull(bindItem.getUnitCode());
        ChargeItemDefinition priceLine = this.requirePriceLineForExecution(
            context,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            bindItem.getActivityId(),
            bindItem.getChargeItemDefinitionId(),
            null);
        ProductDefinitionDisplayDto activityProduct =
            this.loadProductDefinition(context, TableNameConstants.WKF_ACTIVITY_DEFINITION, bindItem.getActivityId());
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(
            nurseStationOrderExecuteMapper.toCreatePlannedChargeItemFromBindExecutionCommand(
                order.getPatientId(),
                order.getEncounterId(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                derivedServiceRequestId,
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                bindItem.getActivityId(),
                priceLine,
                item.getAccountId(),
                bindProcedureId,
                item.getPlanTime(),
                quantity,
                unitCode,
                activityProduct,
                null,
                null,
                operatorId,
                orgId));
    }

    /**
     * 绑定耗材派生（BR-IP-EXE-019 device 分支）。
     *
     * <p>DeviceRequest（已完成，挂主医嘱 based_on + performLocation）→ 临时路径 Dispense →
     * 按「耗材是否自动扣库」开关推进已配药或 FIFO 已发药扣库 → 待结算 Charge。</p>
     */
    private void executeBindDeviceItem(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        Long operatorId,
        Long performerId,
        Long parentProcedureId,
        Long orgId,
        WriteContextBundle context) {
        Long deviceDefId = bindItem.getDeviceId();
        BigDecimal quantity = this.resolveBindItemQuantity(bindItem);
        ProductDefinitionDisplayDto deviceProduct =
            this.loadProductDefinition(context, TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId);
        String unitCode = this.resolveBindItemUnitCode(bindItem, deviceProduct);
        Long performLocation = this.resolveBindDevicePerformLocation(orgId, deviceDefId, deviceProduct);
        String basedOnTable = this.resolveRequestTable(item.getOrderType());
        Long basedOnId = item.getOrderId();
        List<Long> deviceReqIds = deviceRequestDomainService.createExecutedDeviceOrder(
            nurseStationOrderExecuteMapper.toCreateExecutedDeviceOrderCommand(
                deviceDefId,
                quantity,
                unitCode,
                performLocation,
                order.getEncounterId(),
                order.getPatientId(),
                basedOnTable,
                basedOnId,
                operatorId));
        Long deviceReqId = deviceReqIds.get(0);
        deviceDispenseDomainService.generateDeviceDispenseFromTemporaryPath(
            nurseStationOrderExecuteMapper.toGenerateDeviceDispenseFromTemporaryPathCommand(
                deviceReqId,
                deviceDefId,
                order.getEncounterId(),
                order.getPatientId(),
                quantity,
                unitCode,
                item.getPlanTime(),
                operatorId));
        deviceDispenseNurseWorkflowSupport.finalizeInpatientTemporaryPathDispense(
            deviceReqId,
            operatorId,
            performerId,
            context.getDeviceInventoryDecreaseAccumulator());
        Long dispenseId = this.resolvePrimaryDeviceDispenseId(deviceReqId);
        ChargeItemDefinition priceLine = this.requirePriceLineForExecution(
            context,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            deviceDefId,
            bindItem.getChargeItemDefinitionId(),
            null);
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(
            nurseStationOrderExecuteMapper.toCreatePlannedChargeItemFromBindExecutionCommand(
                order.getPatientId(),
                order.getEncounterId(),
                TableNameConstants.WKF_DEVICE_REQUEST,
                deviceReqId,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                deviceDefId,
                priceLine,
                item.getAccountId(),
                parentProcedureId,
                item.getPlanTime(),
                quantity,
                unitCode,
                deviceProduct,
                dispenseId,
                TableNameConstants.WKF_DEVICE_DISPENSE,
                operatorId,
                orgId));
    }

    /**
     * 解析耗材请求下主发放行 id（id 最小者），供收费锚点关联。
     *
     * @param deviceReqId 耗材请求主键
     * @return 主发放行 id；无则 null
     */
    private Long resolvePrimaryDeviceDispenseId(Long deviceReqId) {
        List<Long> ids = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        Long primary = null;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (primary == null || id < primary) {
                primary = id;
            }
        }
        return primary;
    }

    /**
     * 绑定耗材默认发放库房：按机构 + 耗材分类解析 ServiceLocationTemplate（对齐护士划价）。
     *
     * @param orgId          机构主键
     * @param deviceDefId    耗材定义主键
     * @param deviceProduct  耗材读模型；可 null
     * @return 发放库房主键
     */
    private Long resolveBindDevicePerformLocation(
        Long orgId,
        Long deviceDefId,
        ProductDefinitionDisplayDto deviceProduct) {
        if (orgId == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_304,
                "绑定耗材执行失败：未配置该住院科室的发放库房");
        }
        String distributionCategory = MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
        if (deviceProduct != null && StringUtils.isNotBlank(deviceProduct.getCategoryCode())) {
            distributionCategory = deviceProduct.getCategoryCode();
        }
        Long performLocation = clinicalCatalogDispenseLocationResolveSupport.matchDefaultDeviceDispenseLocationId(
            orgId, deviceDefId, distributionCategory);
        if (performLocation == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_304,
                "绑定耗材执行失败：未配置该住院科室的发放库房");
        }
        return performLocation;
    }

    /**
     * 绑定项数量换算：分子/分母；缺省为 1。
     *
     * @param bindItem 绑定项；可 null
     * @return 业务数量；null 分子时返回 1
     */
    private BigDecimal resolveBindItemQuantity(NurseStationOrderExecuteBindItemDto bindItem) {
        if (bindItem == null || bindItem.getQuantityNumerator() == null) {
            // 模板未配置数量时按 1 次/1 个处理
            return BigDecimal.ONE;
        }
        BigDecimal denominator = bindItem.getQuantityDenominator();
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            // 分母缺失或为 0 时按 1 处理，避免除零
            denominator = BigDecimal.ONE;
        }
        return bindItem.getQuantityNumerator().divide(denominator, 6, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 绑定耗材单位：优先 bindItem.unitCode，否则回落耗材目录住院默认/拆零/包装单位。
     *
     * @param bindItem      绑定项
     * @param deviceProduct 耗材读模型；可 null
     * @return 业务单位编码
     */
    private String resolveBindItemUnitCode(
        NurseStationOrderExecuteBindItemDto bindItem,
        ProductDefinitionDisplayDto deviceProduct) {
        String rawUnitCode = null;
        if (bindItem != null) {
            rawUnitCode = bindItem.getUnitCode();
        }
        String unitCode = trimToNull(rawUnitCode);
        if (unitCode != null) {
            return unitCode;
        }
        if (deviceProduct != null) {
            unitCode = org.apache.commons.lang3.StringUtils.firstNonBlank(
                deviceProduct.getDefInhospitalUnitCode(),
                deviceProduct.getSplitUnitCode(),
                deviceProduct.getPackageUnitCode(),
                deviceProduct.getStockUnitCode());
        }
        if (unitCode == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_304,
                "绑定耗材执行失败：缺少单位编码");
        }
        return unitCode;
    }

    /**
     * 构建库存 Gate 需求行（BR-IP-EXE-014，<b>仅药嘱</b>）。
     *
     * <p>遍历批量写槽位，跳过非 MED_REQUEST、无 medicationId、长临未知的行；
     * 绑定耗材 deliberately 不进入 demandLines。</p>
     *
     * @param items   待执行槽位
     * @param context 预加载上下文
     * @return Gate 输入行；可能为空（全为诊嘱时）
     */
    private List<NurseStationExecutionInventoryAssembler.InventoryGateDemandLine> buildInventoryGateDemands(
        List<NurseStationOrderExecuteWriteSlotDto> items,
        WriteContextBundle context) {
        List<NurseStationExecutionInventoryAssembler.InventoryGateDemandLine> lines = new ArrayList<>();
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            // 非药嘱不参与库存 Gate
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            OrderExecutionContext orderCtx = context.resolveOrderContext(item);
            MedicationRequestListQueryModel med = orderCtx.medDetail;
            if (med == null || med.getMedicationId() == null) {
                // 无药品定义则无法做 FIFO，跳过（Gate 阶段不应到达写路径）
                continue;
            }
            if (!TherapyType.LONG_TERM.getCode().equals(orderCtx.therapyEnum)
                && !TherapyType.TEMPORARY.getCode().equals(orderCtx.therapyEnum)) {
                // 长临未知的不纳入 Gate 汇总
                continue;
            }
            NurseStationExecutionInventoryAssembler.InventoryGateDemandLine line =
                new NurseStationExecutionInventoryAssembler.InventoryGateDemandLine();
            InpatientOrderListSkeleton order = context.orderByKey.get(this.buildOrderKey(item));
            // orgId 用于解析候选药房模板
            line.setOrgId(context.encounterOrgIds.get(order.getEncounterId()));
            line.setMedicationId(med.getMedicationId());
            line.setMethodCode(med.getMethodCode());
            // categoryCode 由库存装配器按产品读模型 category_code 解析（与住院医生站 FIFO 口径一致）
            line.setBusinessQuantity(InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseQuantityFromRequest(
                med.getQuantity()));
            line.setUnitCode(InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseUnitCodeFromRequest(
                med.getUnitCode()));
            lines.add(line);
        }
        return lines;
    }

    /**
     * 将 FIFO 分段计划按药嘱槽位键索引（与 {@link #buildInventoryGateDemands} 顺序一致）。
     */
    private Map<String, List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment>> indexFifoSegmentsByMedicationSlot(
        List<NurseStationExecutionInventoryAssembler.InventoryGateDemandLine> gateLines,
        List<NurseStationOrderExecuteWriteSlotDto> items,
        NurseStationExecutionInventoryAssembler.MedicationFifoBatchPlan fifoBatchPlan) {
        Map<String, List<NurseStationExecutionInventoryAssembler.MedicationFifoSegment>> indexed = new HashMap<>();
        int gateIndex = 0;
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            indexed.put(this.buildMedicationGateSlotKey(item), fifoBatchPlan.getSegments(gateIndex));
            gateIndex = gateIndex + 1;
        }
        if (gateLines != null && gateIndex != gateLines.size()) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_302, "库存 Gate 索引不一致");
        }
        return indexed;
    }

    /**
     * 药嘱库存 Gate 槽位键：orderKey + planTime。
     */
    private String buildMedicationGateSlotKey(NurseStationOrderExecuteWriteSlotDto item) {
        String planTimeText = "";
        if (item.getPlanTime() != null) {
            planTimeText = item.getPlanTime().toString();
        }
        return this.buildOrderKey(item) + "|" + planTimeText;
    }

    /**
     * 加载单条药品定义读模型，供护士执行新建 Charge 时按单位槽换算单价。
     *
     * @param context      写上下文缓存
     * @param medicationId 药品定义主键
     * @return 读模型；未命中时 {@code null}
     */
    private ProductDefinitionDisplayDto loadMedicationProductDefinition(
        WriteContextBundle context,
        Long medicationId) {
        return this.loadProductDefinition(context, TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
    }

    /**
     * 按产品定义表与主键加载读模型，供执行计费比例换算（优先读写上下文缓存）。
     *
     * @param context              写上下文缓存
     * @param productTable         产品定义表
     * @param productDefinitionId  产品定义主键
     * @return 读模型；未命中时 {@code null}
     */
    private ProductDefinitionDisplayDto loadProductDefinition(
        WriteContextBundle context,
        String productTable,
        Long productDefinitionId) {
        if (productDefinitionId == null) {
            return null;
        }
        String cacheKey = ProductDefinitionKeyUtil.buildCompositeKey(productTable, productDefinitionId);
        ProductDefinitionDisplayDto cached = context.getProductDefinition(cacheKey);
        if (cached != null) {
            return cached;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(productTable);
        lookup.setProductDefinitionId(productDefinitionId);
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        ProductDefinitionDisplayDto product = loaded.get(cacheKey);
        if (product != null) {
            context.putProductDefinition(cacheKey, product);
        }
        return product;
    }

    /**
     * 执行写路径解析 {@link ChargeItemDefinition}（对齐列表 bindItems 已回填的 definitionId 与 BR-IP-EXE-026）。
     *
     * <p>优先顺序：① 调用方已解析的 {@code preferredDefinitionId}（绑定项/诊嘱模板）；② 按实例批量查价，
     * 药耗路径按 {@code executionLotNumber} 命中批号售价；③ 产品读模型 {@code chargeItemDefinitionId} 回落。</p>
     *
     * @param context                写上下文缓存
     * @param instanceTable          定价实例表
     * @param instanceId             定价实例主键
     * @param preferredDefinitionId  列表或模板已选定价主键；无则 {@code null}
     * @param executionLotNumber     FIFO/执行批号；仅药嘱长期执行传入
     * @return 用于比例换算的定价行
     * @throws BusinessException 实例与回落均无法命中开立口径定价时抛出
     */
    private ChargeItemDefinition requirePriceLineForExecution(
        WriteContextBundle context,
        String instanceTable,
        Long instanceId,
        Long preferredDefinitionId,
        String executionLotNumber) {
        if (instanceId == null && preferredDefinitionId == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_303,
                "产品定义主键缺失，无法生成执行费用");
        }
        // 列表 bindItems / 诊嘱费用模板已解析 definitionId 时直接复用，避免与 IF-IP-EXE-01 展示口径不一致。
        if (preferredDefinitionId != null) {
            ChargeItemDefinition preferred = context.getChargeDefinitionById(preferredDefinitionId);
            if (preferred == null) {
                Map<Long, ChargeItemDefinition> preferredById =
                    chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(List.of(preferredDefinitionId));
                preferred = preferredById.get(preferredDefinitionId);
                if (preferred != null) {
                    context.putChargeDefinition(preferred);
                }
            }
            if (preferred != null) {
                return preferred;
            }
        }
        if (instanceId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                this.resolveExecutionChargeDefinitionMissingMessage(instanceTable));
        }
        List<ChargeItemDefinition> priceLines = context.getPriceLinesForInstance(instanceTable, instanceId);
        if (priceLines == null) {
            ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
            priceQuery.setInstanceTable(instanceTable);
            priceQuery.setInstanceIds(List.of(instanceId));
            priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
            context.putPriceLinesForInstance(instanceTable, instanceId, priceLines);
        }
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolveSupport.selectExecutionPriceLineForChargeDraft(
            instanceTable, priceLines, executionLotNumber);
        if (priceLine != null) {
            return priceLine;
        }
        // 与绑定列表 assemble 相同：产品定义行上挂接的 chargeItemDefinitionId 作末级回落。
        ProductDefinitionDisplayDto productRow = this.loadProductDefinition(context, instanceTable, instanceId);
        if (productRow != null && productRow.getChargeItemDefinitionId() != null) {
            ChargeItemDefinition fallback = context.getChargeDefinitionById(productRow.getChargeItemDefinitionId());
            if (fallback == null) {
                Map<Long, ChargeItemDefinition> fallbackById = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(
                    List.of(productRow.getChargeItemDefinitionId()));
                fallback = fallbackById.get(productRow.getChargeItemDefinitionId());
                if (fallback != null) {
                    context.putChargeDefinition(fallback);
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
            this.resolveExecutionChargeDefinitionMissingMessage(instanceTable));
    }

    /**
     * 按产品表返回执行路径定价缺失文案（诊疗 SALE / 药耗 LOT_NUMBER_PRICE）。
     *
     * @param instanceTable 定价实例表
     * @return 业务异常提示
     */
    private String resolveExecutionChargeDefinitionMissingMessage(String instanceTable) {
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(instanceTable)) {
            return DoctorStationAdviceConstants.MSG_CHARGE_DEFINITION_MISSING_FOR_SAVE;
        }
        return DoctorStationAdviceConstants.MSG_CHARGE_DEFINITION_MISSING_LOT_NUMBER_PRICE;
    }

    /**
     * 查找长期医嘱费用模板行（签发侧 Charge，供 ORDER_EXECUTE 新建时复制单价/科目）。
     *
     * @param context      写上下文缓存
     * @param encounterId  就诊主键
     * @param serviceTable 服务表名
     * @param serviceId    服务主键
     * @return 模板 ChargeItem；不存在时 null（由领域层决定是否允许）
     */
    private ChargeItem findChargeTemplate(
        WriteContextBundle context,
        Long encounterId,
        String serviceTable,
        Long serviceId) {
        List<ChargeItem> charges = context.chargesByEncounterId.get(encounterId);
        if (charges == null) {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterId(encounterId);
            charges = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
            context.chargesByEncounterId.put(encounterId, charges);
        }
        for (ChargeItem row : charges) {
            if (row == null) {
                continue;
            }
            if (!serviceTable.equals(row.getServiceTable())) {
                continue;
            }
            if (!serviceId.equals(row.getServiceId())) {
                continue;
            }
            // 长期新建费用时复制首条匹配行的单价/科目（不限状态）
            return row;
        }
        return null;
    }

    /**
     * 中药方勾选槽位时按 prescription_no 扩成全方各味药行（同 planTime）。
     *
     * @param items 原始写槽位
     * @return 扩行后的槽位（按 orderType+orderId+planTime 去重）
     */
    private List<NurseStationOrderExecuteWriteSlotDto> expandExecuteWriteSlotsByTcmPrescription(
        List<NurseStationOrderExecuteWriteSlotDto> items) {
        List<NurseStationOrderExecuteWriteSlotDto> expanded = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return expanded;
        }
        boolean needMedByEncounter = false;
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            if (item == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            if (item.getLineRequestIds() == null || item.getLineRequestIds().isEmpty()) {
                needMedByEncounter = true;
                break;
            }
        }
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
        if (needMedByEncounter) {
            Set<Long> encounterIds = new LinkedHashSet<>();
            for (NurseStationOrderExecuteWriteSlotDto item : items) {
                if (item == null) {
                    continue;
                }
                Long encounterId = inpatientOrderLookupLoader.resolveEncounterId(item.getOrderType(), item.getOrderId());
                if (encounterId != null) {
                    encounterIds.add(encounterId);
                }
            }
            medByEncounter = this.loadMedByEncounterForExecuteWrite(encounterIds);
        }
        Set<String> dedupe = new LinkedHashSet<>();
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            if (item == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                // 诊疗槽位原样进入写路径
                expanded.add(item);
                continue;
            }
            List<Long> requestIds = this.resolveExecuteWriteMedRequestIds(item, medByEncounter);
            for (Long requestId : requestIds) {
                String dedupeKey = item.getOrderType() + ":" + requestId + ":" + item.getPlanTime();
                if (dedupe.contains(dedupeKey)) {
                    continue;
                }
                dedupe.add(dedupeKey);
                NurseStationOrderExecuteWriteSlotDto clone = new NurseStationOrderExecuteWriteSlotDto();
                clone.setOrderId(requestId);
                clone.setOrderType(item.getOrderType());
                clone.setPlanTime(item.getPlanTime());
                clone.setActualTime(item.getActualTime());
                clone.setAccountId(item.getAccountId());
                clone.setBindItems(item.getBindItems());
                clone.setPrescriptionNo(item.getPrescriptionNo());
                expanded.add(clone);
            }
        }
        return expanded;
    }

    /**
     * 解析写槽位对应药品 req id：优先列表回传 {@code lineRequestIds}，否则按处方号扩行。
     *
     * @param item           写槽位
     * @param medByEncounter 就诊药品明细（仅回退扩行时需要）
     * @return 本槽位应对应的全部 req id
     */
    private List<Long> resolveExecuteWriteMedRequestIds(
        NurseStationOrderExecuteWriteSlotDto item,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter) {
        List<Long> fromList = item.getLineRequestIds();
        if (fromList != null && !fromList.isEmpty()) {
            List<Long> result = new ArrayList<>();
            for (Long requestId : fromList) {
                if (requestId != null) {
                    result.add(requestId);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        Long encounterId = inpatientOrderLookupLoader.resolveEncounterId(item.getOrderType(), item.getOrderId());
        List<MedicationRequestListQueryModel> encounterMeds = null;
        if (encounterId != null && medByEncounter != null) {
            encounterMeds = medByEncounter.get(encounterId);
        }
        return inpatientNurseStationTcmSupport.expandMedRequestIdsByPrescriptionNo(
            encounterId, item.getOrderId(), encounterMeds);
    }

    /**
     * 加载写路径涉及的就诊药品明细（整方扩行用）。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊 → 药品明细
     */
    private Map<Long, List<MedicationRequestListQueryModel>> loadMedByEncounterForExecuteWrite(Set<Long> encounterIds) {
        Map<Long, List<MedicationRequestListQueryModel>> map = new HashMap<>();
        if (encounterIds == null) {
            return map;
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            map.put(encounterId,
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                    encounterId, doctorSource));
        }
        return map;
    }

    /**
     * BR-IP-TCM-008：中药方执行成功后按付数挂代煎费（同方同 planTime 仅一次）。
     *
     * @param item           当前槽位
     * @param order          OrderInfo 骨架
     * @param orderCtx       药诊明细上下文
     * @param procedureId    本槽位 Procedure 主键
     * @param operatorId     操作人
     * @param orgId          机构主键
     * @param decoctionKeys  已处理代煎费去重键
     */
    private void maybeCreateTcmDecoctionChargeAfterExecute(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        OrderExecutionContext orderCtx,
        Long procedureId,
        Long operatorId,
        Long orgId,
        Set<String> decoctionKeys) {
        MedicationRequestListQueryModel med = orderCtx.medDetail;
        if (med == null || !Boolean.TRUE.equals(med.getTcmFlag())) {
            // 非中药方不挂代煎费
            return;
        }
        if (!Boolean.TRUE.equals(med.getSufferingFlag())) {
            // 方头未代煎则跳过
            return;
        }
        Long encounterId = order.getEncounterId();
        LocalDateTime planTime = item.getPlanTime();
        String prescriptionNo = trimToNull(item.getPrescriptionNo());
        if (prescriptionNo == null) {
            prescriptionNo = trimToNull(med.getPrescriptionNo());
        }
        String dedupeKey = String.valueOf(encounterId) + ":" + prescriptionNo + ":" + planTime;
        if (!decoctionKeys.add(dedupeKey)) {
            // 整方多味药行共享同一 planTime，代煎费只挂一次
            return;
        }
        inpatientTcmDecoctionExecuteSupport.replaceOrderExecuteDecoctionChargeIfNeeded(
            encounterId,
            order.getPatientId(),
            item.getAccountId(),
            orgId,
            operatorId,
            procedureId,
            planTime,
            prescriptionNo,
            med.getSufferingFlag(),
            med.getChineseHerbsDoseQuantity());
    }

    /**
     * 写路径预加载上下文，避免 Gate/写循环内逐条查库。
     *
     * <p>装载：OrderInfo、护士可见就诊、药诊明细索引、Procedure 历史、
     * Charge/Dispense 懒加载缓存容器。</p>
     *
     * @param items 写槽位列表
     * @return 写上下文 bundle
     */
    private WriteContextBundle prepareWriteContext(List<NurseStationOrderExecuteWriteSlotDto> items) {
        WriteContextBundle bundle = new WriteContextBundle();
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        Set<Long> encounterIds = new LinkedHashSet<>();
        Set<String> loadedOrderKeys = new LinkedHashSet<>();
        // 批量解析 OrderInfo 骨架：同一医嘱多计划点只查一次
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            String orderKey = this.buildOrderKey(item);
            if (loadedOrderKeys.contains(orderKey)) {
                continue;
            }
            loadedOrderKeys.add(orderKey);
            InpatientOrderListSkeleton order = inpatientOrderLookupLoader.mustGetByOrderTypeAndId(
                item.getOrderType(), item.getOrderId());
            bundle.orderByKey.put(orderKey, order);
            if (order.getEncounterId() != null) {
                encounterIds.add(order.getEncounterId());
            }
        }
        // 可见性二次校验：写路径涉及的 encounter 须在护士管理病区范围内
        InpatientEncounterPageQuery query = nurseStationOrderExecuteMapper.toInpatientEncounterPageQuery(
            new ArrayList<>(encounterIds), managedWardIds);
        List<InpatientEncounterContextQueryModel> visible =
            encounterContextFacade.listInpatientEncounterContexts(query);
        List<Long> requestedEncounterIds = new ArrayList<>(encounterIds);
        List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(requestedEncounterIds, visible);
        bundle.visibleEncounterIds = new HashSet<>(visibleEncounterIds);
        Set<Long> visibleEncounterIdSet = new HashSet<>(visibleEncounterIds);
        List<InpatientEncounterContextQueryModel> matchedVisibleRows = new ArrayList<>();
        for (InpatientEncounterContextQueryModel row : visible) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (visibleEncounterIdSet.contains(row.getEncounterId())) {
                matchedVisibleRows.add(row);
            }
        }
        bundle.encounterOrgIds = this.indexEncounterOrgIds(matchedVisibleRows);
        // 按就诊预热药/诊域明细，供长临判定与 Gate 数量解析
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (Long encounterId : encounterIds) {
            List<MedicationRequestListQueryModel> medRows =
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(encounterId, doctorSource);
            for (MedicationRequestListQueryModel row : medRows) {
                if (row.getId() != null) {
                    bundle.medDetailById.put(row.getId(), row);
                }
            }
            List<ServiceRequestListModel> svcRows =
                serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
            for (ServiceRequestListModel row : svcRows) {
                if (row.getId() != null) {
                    bundle.svcDetailById.put(row.getId(), row);
                }
            }
        }
        // 按就诊预热费用行，供执行写路径定价模板复用
        for (Long encounterId : encounterIds) {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterId(encounterId);
            List<ChargeItem> charges = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
            bundle.chargesByEncounterId.put(encounterId, charges);
        }
        // 写 Gate 需比对「当前 pending 槽位」与 DB 中 Procedure 历史是否一致（按医嘱去重）
        Set<String> loadedProcedureKeys = new LinkedHashSet<>();
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            String orderKey = this.buildOrderKey(item);
            if (loadedProcedureKeys.contains(orderKey)) {
                continue;
            }
            loadedProcedureKeys.add(orderKey);
            String requestTable = this.resolveRequestTable(item.getOrderType());
            List<ProcedureRecordQueryModel> procedures =
                procedureQueryService.getProcedureRecords(item.getOrderId(), requestTable);
            bundle.proceduresByOrderKey.put(orderKey, procedures);
        }
        return bundle;
    }

    /**
     * 执行写路径批量预热：产品读模型与定价行（避免按槽位 N 次重复查库）。
     *
     * @param items   待执行槽位
     * @param context 预加载写上下文
     */
    private void warmWritePathCatalogCaches(
        List<NurseStationOrderExecuteWriteSlotDto> items,
        WriteContextBundle context) {
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        Set<String> productLookupKeys = new LinkedHashSet<>();
        Map<String, Set<Long>> instanceIdsByTable = new HashMap<>();
        Set<Long> preferredDefinitionIds = new LinkedHashSet<>();
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            String orderKey = this.buildOrderKey(item);
            InpatientOrderListSkeleton order = context.orderByKey.get(orderKey);
            if (order == null) {
                continue;
            }
            OrderExecutionContext orderCtx = context.resolveOrderContext(item);
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                MedicationRequestListQueryModel med = orderCtx.medDetail;
                if (med != null && med.getMedicationId() != null) {
                    this.collectProductLookup(
                        productLookups, productLookupKeys,
                        TableNameConstants.MED_MEDICATION_DEFINITION, med.getMedicationId());
                    this.collectInstanceIdForPriceWarmup(
                        instanceIdsByTable, TableNameConstants.MED_MEDICATION_DEFINITION, med.getMedicationId());
                    ChargeItem template = this.findChargeTemplate(
                        context, order.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, med.getId());
                    if (template != null && template.getDefinitionId() != null) {
                        preferredDefinitionIds.add(template.getDefinitionId());
                    }
                }
            } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                ServiceRequestListModel svc = orderCtx.svcDetail;
                Long activityId = order.getActivityId();
                if (svc != null && svc.getActivityId() != null) {
                    activityId = svc.getActivityId();
                }
                if (activityId != null) {
                    this.collectProductLookup(
                        productLookups, productLookupKeys,
                        TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
                    this.collectInstanceIdForPriceWarmup(
                        instanceIdsByTable, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
                }
                ChargeItem template = this.findChargeTemplate(
                    context, order.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, item.getOrderId());
                if (template != null && template.getDefinitionId() != null) {
                    preferredDefinitionIds.add(template.getDefinitionId());
                }
            }
            if (item.getBindItems() == null) {
                continue;
            }
            for (NurseStationOrderExecuteBindItemDto bindItem : item.getBindItems()) {
                if (bindItem == null) {
                    continue;
                }
                if (bindItem.getChargeItemDefinitionId() != null) {
                    preferredDefinitionIds.add(bindItem.getChargeItemDefinitionId());
                }
                if (bindItem.getActivityId() != null) {
                    this.collectProductLookup(
                        productLookups, productLookupKeys,
                        TableNameConstants.WKF_ACTIVITY_DEFINITION, bindItem.getActivityId());
                    this.collectInstanceIdForPriceWarmup(
                        instanceIdsByTable, TableNameConstants.WKF_ACTIVITY_DEFINITION, bindItem.getActivityId());
                }
                if (bindItem.getDeviceId() != null) {
                    this.collectProductLookup(
                        productLookups, productLookupKeys,
                        TableNameConstants.ADM_DEVICE_DEFINITION, bindItem.getDeviceId());
                    this.collectInstanceIdForPriceWarmup(
                        instanceIdsByTable, TableNameConstants.ADM_DEVICE_DEFINITION, bindItem.getDeviceId());
                }
            }
        }
        if (!productLookups.isEmpty()) {
            Map<String, ProductDefinitionDisplayDto> loadedProducts =
                productDefinitionBatchLoader.loadAsMap(productLookups);
            for (Map.Entry<String, ProductDefinitionDisplayDto> entry : loadedProducts.entrySet()) {
                if (entry.getValue() != null) {
                    context.putProductDefinition(entry.getKey(), entry.getValue());
                    if (entry.getValue().getChargeItemDefinitionId() != null) {
                        preferredDefinitionIds.add(entry.getValue().getChargeItemDefinitionId());
                    }
                }
            }
        }
        if (!preferredDefinitionIds.isEmpty()) {
            Map<Long, ChargeItemDefinition> definitions =
                chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(preferredDefinitionIds);
            for (ChargeItemDefinition definition : definitions.values()) {
                if (definition != null) {
                    context.putChargeDefinition(definition);
                }
            }
        }
        for (Map.Entry<String, Set<Long>> entry : instanceIdsByTable.entrySet()) {
            String instanceTable = entry.getKey();
            List<Long> instanceIds = new ArrayList<>(entry.getValue());
            if (instanceIds.isEmpty()) {
                continue;
            }
            ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
            priceQuery.setInstanceTable(instanceTable);
            priceQuery.setInstanceIds(instanceIds);
            List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
            context.indexPriceLinesByInstance(instanceTable, priceLines);
        }
    }

    /**
     * 收集产品定义批量查询项（去重）。
     */
    private void collectProductLookup(
        List<ProductDefinitionBatchLookup> productLookups,
        Set<String> productLookupKeys,
        String productTable,
        Long productDefinitionId) {
        if (productDefinitionId == null) {
            return;
        }
        String cacheKey = ProductDefinitionKeyUtil.buildCompositeKey(productTable, productDefinitionId);
        if (productLookupKeys.contains(cacheKey)) {
            return;
        }
        productLookupKeys.add(cacheKey);
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(productTable);
        lookup.setProductDefinitionId(productDefinitionId);
        productLookups.add(lookup);
    }

    /**
     * 收集定价实例主键（按实例表分组去重）。
     */
    private void collectInstanceIdForPriceWarmup(
        Map<String, Set<Long>> instanceIdsByTable,
        String instanceTable,
        Long instanceId) {
        if (instanceId == null) {
            return;
        }
        Set<Long> bucket = instanceIdsByTable.get(instanceTable);
        if (bucket == null) {
            bucket = new LinkedHashSet<>();
            instanceIdsByTable.put(instanceTable, bucket);
        }
        bucket.add(instanceId);
    }

    /**
     * 执行写路径单槽位 Gate（BR-IP-EXE-014/023）。
     *
     * @param item                写槽位
     * @param context             预加载上下文
     * @param performerId         当前医务人员
     * @throws BusinessException EX-IP-EXE-301 槽位已变更；EX-IP-EXE-303 可见性/状态不满足
     */
    private void assertWriteSlotExecutable(
        NurseStationOrderExecuteWriteSlotDto item,
        WriteContextBundle context,
        Long performerId) {
        InpatientOrderListSkeleton order = context.orderByKey.get(this.buildOrderKey(item));
        if (order == null) {
            // 预加载阶段应已写入 orderByKey，缺失说明数据异常
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "医嘱不存在");
        }
        if (!context.visibleEncounterIds.contains(order.getEncounterId())) {
            // 越权防护：患者已转科/不在管理病区时不允许执行
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "就诊不在护士站可见范围");
        }
        if (!RequestStatus.VERIFIED.getCode().equals(order.getStatusEnum())) {
            // 仅已校对医嘱可执行；草稿/停嘱等状态拒绝
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "仅已校对医嘱允许执行");
        }
        if (item.getActualTime() == null) {
            // IF-IP-EXE-02 必填：护士录入的实际执行时刻
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "实际执行时刻不能为空");
        }
        OrderExecutionContext orderCtx = context.resolveOrderContext(item);
        if (nurseStationOrderExecuteListAssembler.isPlanTimeAfterStopTime(
            order, orderCtx.medDetail, orderCtx.svcDetail, item.getPlanTime())) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "计划点晚于停嘱时间，不可执行");
        }
        // occurrence 净态 Gate：仅 pending / cancelled 可正向执行；已执行/不执行拒绝（EX-IP-EXE-301）
        List<ProcedureRecordQueryModel> procedures = context.proceduresByOrderKey.get(this.buildOrderKey(item));
        boolean executable = nurseStationOrderExecuteListAssembler.isPlanTimeExecutable(
            order,
            orderCtx.medDetail,
            orderCtx.svcDetail,
            item.getPlanTime(),
            procedures);
        if (!executable) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_301, "该计划点已执行或已登记不执行，请刷新后重试");
        }
    }

    /**
     * 不执行写路径单槽位 Gate（BR-IP-EXE-021 前置）。
     *
     * @param item    写槽位
     * @param context 预加载上下文
     * @throws BusinessException EX-IP-EXE-401 非 pending 槽位
     */
    private void assertWriteSlotSkippable(NurseStationOrderExecuteWriteSlotDto item, WriteContextBundle context) {
        InpatientOrderListSkeleton order = context.orderByKey.get(this.buildOrderKey(item));
        if (order == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_403, "医嘱不存在");
        }
        if (!context.visibleEncounterIds.contains(order.getEncounterId())) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_403, "就诊不在护士站可见范围");
        }
        OrderExecutionContext orderCtx = context.resolveOrderContext(item);
        if (nurseStationOrderExecuteListAssembler.isPlanTimeAfterStopTime(
            order, orderCtx.medDetail, orderCtx.svcDetail, item.getPlanTime())) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_403, "计划点晚于停嘱时间，不可登记不执行");
        }
        List<ProcedureRecordQueryModel> procedures = context.proceduresByOrderKey.get(this.buildOrderKey(item));
        boolean pending = nurseStationOrderExecuteListAssembler.isPlanTimePending(
            order,
            orderCtx.medDetail,
            orderCtx.svcDetail,
            item.getPlanTime(),
            procedures);
        if (!pending) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_401, "该计划点已执行或已登记不执行");
        }
    }

    /**
     * 列表查询入参 Gate（IF-IP-EXE-01 前置）。
     *
     * @param request 列表请求
     * @throws BusinessException 参数不合法
     */
    private void assertListExecuteRequestValid(ListNurseStationOrderExecuteRequest request) {
        if (request == null || request.getEncounterIds() == null || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请选择至少一名患者");
        }
        if (request.getExecutionDeadline() == null) {
            // 截止时间驱动频次展开窗口上界
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "截止时间不能为空");
        }
        String tab = trimToEmpty(request.getExecuteTab());
        if (StringUtils.isBlank(tab) || !this.isAllowedExecuteTab(tab)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行 Tab 不合法");
        }
        inpatientNurseStationTcmSupport.resolveMedicineCategoryTabOrDefault(request.getMedicineCategoryTab());
        String therapy = request.getTherapyType();
        if (StringUtils.isBlank(therapy)) {
            // 未传长临筛选视为 all，不做额外校验
            return;
        }
        if (!this.isAllowedTherapyFilter(trimToEmpty(therapy))) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "长临筛选不合法");
        }
    }

    /**
     * 写路径批量入参 Gate：条数上限、锚点完整性、执行/不执行路径差异校验。
     *
     * @param items        写槽位列表
     * @param executePath  true=执行（要求 actualTime）；false=不执行
     */
    private void assertWriteSlotsGate(List<NurseStationOrderExecuteWriteSlotDto> items, boolean executePath) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请勾选至少一条待执行槽位");
        }
        if (items.size() > InpatientNurseStationConstants.EXECUTE_WRITE_BATCH_MAX) {
            // BR-IP-EXE-022：单次批量上限，防止长事务
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "单次最多处理50条计划点");
        }
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            if (item == null || item.getOrderId() == null || StringUtils.isBlank(item.getOrderType())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "医嘱锚点不完整");
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())
                && !ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                // 本期仅支持药/诊两类医嘱执行
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "医嘱类型须为 MED_REQUEST 或 SERVICE_REQUEST");
            }
            if (item.getPlanTime() == null) {
                // planTime = Procedure occurrence_time，标识「哪一次计划」（如 22 日 8:00）
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "计划执行时刻不能为空");
            }
            if (executePath && item.getActualTime() == null) {
                // actualTime = recorded_time，护士实际操作时刻，与 planTime 无先后约束
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "实际执行时刻不能为空");
            }
            if (executePath && item.getAccountId() == null) {
                // 执行新建费用须锚定就诊账户，由 IF-IP-NUR-02/IF-IP-EXE-01 列表回传后原样提交
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_SELF_PAY_ACCOUNT_REQUIRED_FOR_DRAFT_CHARGE);
            }
        }
    }

    /**
     * 解析 Procedure 执行类目（长临 code），供 create/skip 命令 categoryEnum 字段。
     *
     * @param order    医嘱读模型
     * @param orderCtx 药/诊明细上下文
     * @return TherapyType code；缺省 TEMPORARY
     */
    private String resolveProcedureCategory(InpatientOrderListSkeleton order, OrderExecutionContext orderCtx) {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (orderCtx.medDetail != null && StringUtils.isNotBlank(orderCtx.medDetail.getTherapyEnum())) {
                // 药嘱长临以域明细为准（与发药/计费分轨一致）
                return orderCtx.medDetail.getTherapyEnum();
            }
            if (StringUtils.isNotBlank(order.getTherapyEnum())) {
                return order.getTherapyEnum();
            }
            // 缺省临时，避免 category 空导致 Procedure 校验失败
            return TherapyType.TEMPORARY.getCode();
        }
        // 诊嘱：优先 ServiceRequest 明细的长临
        if (orderCtx.svcDetail != null && StringUtils.isNotBlank(orderCtx.svcDetail.getTherapyEnum())) {
            return orderCtx.svcDetail.getTherapyEnum();
        }
        if (StringUtils.isNotBlank(order.getCategoryEnum())) {
            // OrderInfo.categoryEnum 在诊嘱场景通常承载长临
            return order.getCategoryEnum();
        }
        return TherapyType.TEMPORARY.getCode();
    }

    /**
     * orderType → 领域 Request 表名。
     *
     * @param orderType MED_REQUEST / SERVICE_REQUEST
     * @return 表名字面量
     */
    private String resolveRequestTable(String orderType) {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(orderType)) {
            return TableNameConstants.MED_MEDICATION_REQUEST;
        }
        return TableNameConstants.WKF_SERVICE_REQUEST;
    }

    /**
     * 写路径医嘱复合键：orderId + ":" + orderType。
     *
     * @param item 写槽位
     * @return 复合键字符串
     */
    private String buildOrderKey(NurseStationOrderExecuteWriteSlotDto item) {
        return item.getOrderId() + ":" + item.getOrderType();
    }

    /**
     * 就诊主键去重并保持前端勾选顺序。
     *
     * @param encounterIds 原始列表；可 null
     * @return 去重后的就诊主键列表
     */
    private List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long id : encounterIds) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 前端勾选就诊与护士可见范围求交，保持请求顺序（BR-IP-EXE-001）。
     *
     * @param requestedEncounterIds 请求就诊主键
     * @param visibleRows           可见性查询结果
     * @return 求交后的 encounterId 列表
     */
    private List<Long> retainRequestedVisibleEncounterIds(
        List<Long> requestedEncounterIds,
        List<InpatientEncounterContextQueryModel> visibleRows) {
        if (requestedEncounterIds == null || requestedEncounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> visibleIdSet = new HashSet<>();
        if (visibleRows != null) {
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row != null && row.getEncounterId() != null) {
                    visibleIdSet.add(row.getEncounterId());
                }
            }
        }
        List<Long> result = new ArrayList<>();
        for (Long encounterId : requestedEncounterIds) {
            if (encounterId != null && visibleIdSet.contains(encounterId)) {
                result.add(encounterId);
            }
        }
        return result;
    }

    /**
     * 从住院信息读模型行提取 encounterId 列表。
     *
     * @param rows 住院信息行；可 null
     * @return encounterId 列表
     */
    private List<Long> extractEncounterIds(List<InpatientEncounterContextQueryModel> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                ids.add(row.getEncounterId());
            }
        }
        return ids;
    }

    /**
     * 构建 encounterId → organizationId 索引，供 Charge 新建与 bindItems 模板查询。
     *
     * @param rows 住院信息行；可 null
     * @return 就诊到机构的映射
     */
    private Map<Long, Long> indexEncounterOrgIds(List<InpatientEncounterContextQueryModel> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                map.put(row.getEncounterId(), row.getOrganizationId());
            }
        }
        return map;
    }

    /**
     * 校验 executeTab 是否为 AS 约定的合法 Tab code。
     *
     * @param tab pending/executed/skipped/cancelled
     * @return true 表示合法
     */
    private boolean isAllowedExecuteTab(String tab) {
        if (InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab)) {
            return true;
        }
        return false;
    }

    /**
     * 校验 therapyType 筛选是否为 AS 约定的合法值。
     *
     * @param therapy all/long/temp
     * @return true 表示合法
     */
    private boolean isAllowedTherapyFilter(String therapy) {
        if (InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy)) {
            return true;
        }
        return false;
    }

    /**
     * 写路径预加载上下文容器。
     *
     * <p>Charge/产品/定价按 encounterId、产品键缓存，避免同一批次重复查库。</p>
     */
    private static class WriteContextBundle {
        /** orderId:orderType → OrderInfo 读模型。 */
        private final Map<String, InpatientOrderListSkeleton> orderByKey = new HashMap<>();
        /** medReqId → 药品域明细。 */
        private final Map<Long, MedicationRequestListQueryModel> medDetailById = new HashMap<>();
        /** serviceReqId → 诊疗域明细。 */
        private final Map<Long, ServiceRequestListModel> svcDetailById = new HashMap<>();
        /** orderKey → Procedure 历史列表。 */
        private final Map<String, List<ProcedureRecordQueryModel>> proceduresByOrderKey = new HashMap<>();
        /** encounterId → 费用行列表（执行写路径定价模板复用）。 */
        private final Map<Long, List<ChargeItem>> chargesByEncounterId = new HashMap<>();
        /** 产品复合键 → 产品读模型。 */
        private final Map<String, ProductDefinitionDisplayDto> productDefinitionByKey = new HashMap<>();
        /** definitionId → 定价行。 */
        private final Map<Long, ChargeItemDefinition> chargeDefinitionById = new HashMap<>();
        /** instanceTable → instanceId → 定价行列表。 */
        private final Map<String, Map<Long, List<ChargeItemDefinition>>> priceLinesByInstanceTable = new HashMap<>();
        /** 护士可见 encounterId 集合。 */
        private Set<Long> visibleEncounterIds = new HashSet<>();
        /** encounterId → organizationId。 */
        private Map<Long, Long> encounterOrgIds = new HashMap<>();
        /** 绑定耗材自动扣库时按 inventoryItemId 累计待扣数量（批次末合并扣减）。 */
        private final Map<Long, BigDecimal> deviceInventoryDecreaseAccumulator = new HashMap<>();

        /**
         * @return 绑定耗材自动扣库累计器（可空映射，调用方判空后合并扣减）
         */
        private Map<Long, BigDecimal> getDeviceInventoryDecreaseAccumulator() {
            return this.deviceInventoryDecreaseAccumulator;
        }

        /**
         * 写入产品读模型缓存。
         */
        private void putProductDefinition(String cacheKey, ProductDefinitionDisplayDto product) {
            if (cacheKey != null && product != null) {
                this.productDefinitionByKey.put(cacheKey, product);
            }
        }

        /**
         * 读取产品读模型缓存。
         */
        private ProductDefinitionDisplayDto getProductDefinition(String cacheKey) {
            return this.productDefinitionByKey.get(cacheKey);
        }

        /**
         * 写入定价行主键缓存。
         */
        private void putChargeDefinition(ChargeItemDefinition definition) {
            if (definition != null && definition.getId() != null) {
                this.chargeDefinitionById.put(definition.getId(), definition);
            }
        }

        /**
         * 按定价主键读取缓存。
         */
        private ChargeItemDefinition getChargeDefinitionById(Long definitionId) {
            if (definitionId == null) {
                return null;
            }
            return this.chargeDefinitionById.get(definitionId);
        }

        /**
         * 按实例维度索引批量查价结果。
         */
        private void indexPriceLinesByInstance(String instanceTable, List<ChargeItemDefinition> priceLines) {
            if (instanceTable == null || priceLines == null || priceLines.isEmpty()) {
                return;
            }
            Map<Long, List<ChargeItemDefinition>> byInstanceId =
                this.priceLinesByInstanceTable.computeIfAbsent(instanceTable, key -> new HashMap<>());
            for (ChargeItemDefinition priceLine : priceLines) {
                if (priceLine == null || priceLine.getInstanceId() == null) {
                    continue;
                }
                List<ChargeItemDefinition> bucket = byInstanceId.get(priceLine.getInstanceId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byInstanceId.put(priceLine.getInstanceId(), bucket);
                }
                bucket.add(priceLine);
            }
        }

        /**
         * 写入单实例定价行列表缓存。
         */
        private void putPriceLinesForInstance(
            String instanceTable,
            Long instanceId,
            List<ChargeItemDefinition> priceLines) {
            if (instanceTable == null || instanceId == null) {
                return;
            }
            Map<Long, List<ChargeItemDefinition>> byInstanceId =
                this.priceLinesByInstanceTable.computeIfAbsent(instanceTable, key -> new HashMap<>());
            List<ChargeItemDefinition> cachedPriceLines = List.of();
            if (priceLines != null) {
                cachedPriceLines = priceLines;
            }
            byInstanceId.put(instanceId, cachedPriceLines);
        }

        /**
         * 读取单实例定价行列表缓存。
         */
        private List<ChargeItemDefinition> getPriceLinesForInstance(String instanceTable, Long instanceId) {
            if (instanceTable == null || instanceId == null) {
                return null;
            }
            Map<Long, List<ChargeItemDefinition>> byInstanceId = this.priceLinesByInstanceTable.get(instanceTable);
            if (byInstanceId == null) {
                return null;
            }
            return byInstanceId.get(instanceId);
        }

        /**
         * 解析单槽位药/诊明细与长临 code。
         *
         * @param item 写槽位
         * @return 执行上下文；therapyEnum 优先域明细，回落 OrderInfo
         */
        private OrderExecutionContext resolveOrderContext(NurseStationOrderExecuteWriteSlotDto item) {
            OrderExecutionContext ctx = new OrderExecutionContext();
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                ctx.medDetail = medDetailById.get(item.getOrderId());
                if (ctx.medDetail != null) {
                    ctx.therapyEnum = ctx.medDetail.getTherapyEnum();
                }
            } else {
                ctx.svcDetail = svcDetailById.get(item.getOrderId());
                if (ctx.svcDetail != null) {
                    ctx.therapyEnum = ctx.svcDetail.getTherapyEnum();
                }
            }
            InpatientOrderListSkeleton order = orderByKey.get(item.getOrderId() + ":" + item.getOrderType());
            if (StringUtils.isBlank(ctx.therapyEnum) && order != null) {
                // 域明细缺长临时回落 OrderInfo 骨架
                ctx.therapyEnum = order.getTherapyEnum();
            }
            return ctx;
        }
    }

    /**
     * 单医嘱执行上下文：药/诊明细 + 长临 code。
     */
    private static class OrderExecutionContext {
        /** 药品明细；药嘱时有值。 */
        private MedicationRequestListQueryModel medDetail;
        /** 诊疗明细；诊嘱时有值。 */
        private ServiceRequestListModel svcDetail;
        /** 长临 code（TherapyType.getCode()）。 */
        private String therapyEnum;
    }
}
