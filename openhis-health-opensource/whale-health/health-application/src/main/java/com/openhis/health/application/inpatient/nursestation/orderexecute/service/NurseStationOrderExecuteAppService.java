package com.openhis.health.application.inpatient.nursestation.orderexecute.service;

import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.assembler.NurseStationExecutionInventoryAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.assembler.NurseStationOrderExecuteListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.mapper.NurseStationOrderExecuteMapper;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ExecuteNurseStationOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ListNurseStationOrderExecuteRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.SkipNurseStationOrderSlotsRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ListNurseStationOrderExecuteResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.SkipNurseStationOrderSlotsResponse;
import com.openhis.health.application.querysupport.order.InpatientOrderLookupLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
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
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.application.common.assembler.advice.InpatientMedicationRequestExecutionMeasureSupport;
import com.openhis.health.application.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateExecutedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   <li>BR-IP-EXE-019：绑定诊疗走 {@code createExecutedServiceRequest}；绑定耗材走 DeviceRequest → 草稿 Dispense → 执行直发</li>
 *   <li>BR-IP-EXE-022：批量写路径在同一 {@code @Transactional} 内提交，任一槽位失败整批回滚</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationOrderExecuteAppService {

    /** 写路径操作人门控。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    /** 当前登录用户。 */
    private final ICurrentUser currentUser;
    /** 管理病区范围。 */
    private final LocationQueryService locationQueryService;
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
    /** 诊疗写侧（绑定派生）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 耗材请求写侧（绑定耗材派生）。 */
    private final DeviceRequestDomainService deviceRequestDomainService;
    /** 耗材发放写侧（绑定耗材派生）。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    /** 映射器。 */
    private final NurseStationOrderExecuteMapper nurseStationOrderExecuteMapper;
    /** 列表装配。 */
    private final NurseStationOrderExecuteListAssembler nurseStationOrderExecuteListAssembler;
    /** 库存 Gate FIFO。 */
    private final NurseStationExecutionInventoryAssembler nurseStationExecutionInventoryAssembler;
    /** 药品目录批量读侧（执行新建 Charge 比例换算单价）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /**
     * IF-IP-EXE-01：医嘱执行列表查询。
     *
     * @param request 多选就诊、Tab、长临、截止时间
     * @return 按就诊分组的医嘱行（含槽位与 bindItems）
     */
    public ListNurseStationOrderExecuteResponse listNurseStationOrderExecute(ListNurseStationOrderExecuteRequest request) {
        try {
            // 入参 Gate：至少一名患者、截止时间、Tab/长临合法性（BR-IP-EXE-001 前置）
            this.assertListExecuteRequestValid(request);
            // 护士可见范围 = 当前医务人员管理病区下的在院就诊（BR-IP-NUR-003）
            Long practitionerId = currentUser.getCurrentPractitionerId();
            List<Long> managedWardIds = this.resolveManagedWardIds(practitionerId);
            List<Long> requestedEncounterIds = this.distinctEncounterIds(request.getEncounterIds());
            InpatientEncounterPageQuery visibilityQuery =
                nurseStationOrderExecuteMapper.toInpatientEncounterPageQuery(requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                encounterContextFacade.listInpatientEncounterContexts(visibilityQuery);
            // 前端勾选就诊与护士可见范围求交（BR-IP-EXE-001）
            List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(requestedEncounterIds, visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_102,
                    "所选患者不在护士站可见范围");
            }
            Set<Long> visibleEncounterIdSet = new HashSet<>(visibleEncounterIds);
            List<InpatientEncounterContextQueryModel> matchedVisibleRows = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row == null || row.getEncounterId() == null) {
                    continue;
                }
                if (visibleEncounterIdSet.contains(row.getEncounterId())) {
                    matchedVisibleRows.add(row);
                }
            }
            // 机构主键供 bindItems 模板查询按 org 收窄（仅保留求交后的就诊行）
            Map<Long, Long> encounterOrgIds = this.indexEncounterOrgIds(matchedVisibleRows);
            // 长临筛选：all 时不传 therapyEnum，读模型侧不过滤
            String therapyEnum = nurseStationOrderExecuteMapper.resolveTherapyEnum(request.getTherapyType());
            // OrderInfo 骨架：仅 VERIFIED 且符合护士站执行场景的医嘱行
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationExecuteOrders(
                visibleEncounterIds, therapyEnum);
            // 按就诊批量预热药/诊域明细（来源=医生开立），供槽位展开与展示字段回填
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            for (Long encounterId : visibleEncounterIds) {
                // 每个就诊各查一次药/诊明细列表，后续 Assembler 按 id 索引
                medByEncounter.put(encounterId,
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource));
                svcByEncounter.put(encounterId,
                    serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource));
            }
            // 装配：频次计划点 + Procedure 归并四类槽位 + bindItems + Tab 过滤
            List<NurseStationOrderExecutePatientGroupDto> groups =
                nurseStationOrderExecuteListAssembler.assemblePatientGroups(
                    skeleton,
                    medByEncounter,
                    svcByEncounter,
                    request.getExecutionDeadline(),
                    request.getExecuteTab(),
                    encounterOrgIds);
            return nurseStationOrderExecuteMapper.toListResponse(groups);
        } catch (BusinessException ex) {
            // 业务异常原样抛出（可见性、参数、频次等）
            throw ex;
        } catch (RuntimeException ex) {
            // 未预期异常统一包装为 EX-IP-EXE-101，避免堆栈透出
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_101,
                "医嘱执行列表加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-EXE-02：批量执行医嘱计划点。
     *
     * @param request 待执行槽位列表
     * @return 处理结果
     */
    @Transactional
    public ExecuteNurseStationOrdersResponse executeNurseStationOrders(ExecuteNurseStationOrdersRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求不能为空");
        }
        // 写槽位入参 Gate：批量上限 50、锚点完整、执行路径要求 actualTime（BR-IP-EXE-014 前置）
        this.assertWriteSlotsGate(request.getItems(), true);
        // operatorId = 登录账号；performerId = 当前医务人员（可能不同，如代执行场景）
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_EXE_303,
            "未登录或登录已失效，无法执行医嘱");
        // performerId 写入 Procedure/Charge 的执行人维度
        Long performerId = currentUser.getCurrentPractitionerId();
        if (performerId == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_303,
                "未解析到当前医务人员，无法执行医嘱");
        }
        List<NurseStationOrderExecuteWriteSlotDto> items = request.getItems();
        // 预加载：OrderInfo、可见性、药诊明细、Procedure 历史——避免写循环内单条查库
        WriteContextBundle context = this.prepareWriteContext(items);
        // 逐槽位业务 Gate：可见性 + VERIFIED + 该 planTime 未被 Procedure 终态占用（EX-IP-EXE-301）
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            this.assertWriteSlotExecutable(item, context, performerId);
        }
        // === 库存 Gate 阶段（整批 dry-run，任一药品不足则本批全部拒绝）===
        List<NurseStationExecutionInventoryAssembler.InventoryGateDemandLine> gateLines =
            this.buildInventoryGateDemands(items, context);
        // FIFO dry-run：任一药品不足整批 EX-IP-EXE-302，通过后 locationId+lotNumber 供长期发药写入
        Map<Long, NurseStationExecutionInventoryAssembler.MedicationFifoAllocation> fifoByMedication =
            nurseStationExecutionInventoryAssembler.runGateDryRun(gateLines);
        int processed = 0;
        // 逐槽位写 Procedure + 长临 Dispense/Charge + bindItems；同事务，失败即回滚整批
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            try {
                this.executeSingleSlot(item, context, operatorId, performerId, fifoByMedication);
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
        return nurseStationOrderExecuteMapper.toExecuteResponse(processed);
    }

    /**
     * IF-IP-EXE-04：批量登记计划点不执行。
     *
     * @param request 待不执行槽位列表
     * @return 处理结果
     */
    @Transactional
    public SkipNurseStationOrderSlotsResponse skipNurseStationOrderSlots(SkipNurseStationOrderSlotsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求不能为空");
        }
        // 不执行路径：不要求 actualTime，且 deliberately 不做库存 Gate（BR-IP-EXE-021）
        this.assertWriteSlotsGate(request.getItems(), false);
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_EXE_403,
            "未登录或登录已失效，无法登记不执行");
        List<NurseStationOrderExecuteWriteSlotDto> items = request.getItems();
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
     * @param fifoByMedication 库存 Gate 通过的 FIFO 分配结果（仅长期药嘱消费）
     */
    private void executeSingleSlot(
        NurseStationOrderExecuteWriteSlotDto item,
        WriteContextBundle context,
        Long operatorId,
        Long performerId,
        Map<Long, NurseStationExecutionInventoryAssembler.MedicationFifoAllocation> fifoByMedication) {
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
        String therapy = orderCtx.therapyEnum;
        Long orgId = context.encounterOrgIds.get(order.getEncounterId());
        // ④ 主医嘱联动：药嘱走发药+账单，诊嘱走账单（长临分轨见各自 private 方法）
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            this.executeMedicationPath(item, order, orderCtx, procedureId, operatorId, performerId, actualTime,
                therapy, orgId, fifoByMedication, context);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            this.executeServicePath(item, order, orderCtx, procedureId, operatorId, performerId, actualTime,
                therapy, orgId, context);
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
     * 药品执行分轨：长期新建 Dispense+Charge；临时挂接已有 Dispense 并激活 Charge。
     *
     * <p>BR-IP-EXE-016（长期）：{@code generateDispenseFromExecution} 使用 Gate 通过的库位/批号；
     * BR-IP-EXE-017（临时）：挂接签发侧草稿 Dispense + 激活 DRAFT Charge。</p>
     */
    private void executeMedicationPath(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        OrderExecutionContext orderCtx,
        Long procedureId,
        Long operatorId,
        Long performerId,
        LocalDateTime actualTime,
        String therapy,
        Long orgId,
        Map<Long, NurseStationExecutionInventoryAssembler.MedicationFifoAllocation> fifoByMedication,
        WriteContextBundle context) {
        MedicationRequestListQueryModel med = orderCtx.medDetail;
        if (med == null) {
            // 药嘱执行必须有域明细，否则无法解析数量/单位/发药锚点
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "药品明细不存在");
        }
        // 发药数量/单位与药品申请 quantity/unit_code 同套字段（长期=一次执行发药量，临时=总量；与 dose 无关）
        BigDecimal quantity = InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseQuantityFromRequest(
            med.getQuantity());
        String unitCode = InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseUnitCodeFromRequest(
            med.getUnitCode());
        if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
            // 长期：每次执行新建一条发放单 + 新建待结算费用行
            NurseStationExecutionInventoryAssembler.MedicationFifoAllocation fifo =
                fifoByMedication.get(med.getMedicationId());
            if (fifo == null) {
                // Gate 未分配该药品（不应发生，前置已 dry-run）→ 防御性拒绝
                throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_302, "库存 Gate 未通过");
            }
            // 写发药单：locationId/lotNumber 来自 FIFO Gate，护士不可手选
            medicationDispenseDomainService.generateDispenseFromExecution(
                nurseStationOrderExecuteMapper.toGenerateDispenseFromExecutionCommand(
                    med.getId(),
                    procedureId,
                    item.getPlanTime(),
                    fifo.getLocationId(),
                    fifo.getLotNumber(),
                    quantity,
                    unitCode,
                    med.getMedicationId(),
                    order.getEncounterId(),
                    order.getPatientId()));
            // 费用模板取自签发侧同 medReq 的 Charge 行，generate_source=ORDER_EXECUTE
            ChargeItem template = this.findChargeTemplate(context, order.getEncounterId(),
                TableNameConstants.MED_MEDICATION_REQUEST, med.getId());
            ChargeItemDefinition priceLine = this.resolveSalePriceLineForPlannedCharge(
                template,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                med.getMedicationId(),
                true);
            ProductDefinitionDisplayDto medicationProduct =
                this.loadMedicationProductDefinition(med.getMedicationId());
            // 复制模板 definitionId/科目，金额按执行 quantity/unit_code 与定价行比例换算（BR-IP-EXE-026）
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
                    medicationProduct,
                    null,
                    null,
                    operatorId,
                    orgId));
        } else {
            // 临时：签发时已生成 Dispense+Charge（PLANNED），执行时挂接 Procedure 并激活费用
            MedicationDispense dispense = this.findDraftMedicationDispense(med.getId(), context);
            // 将已有草稿发放单关联到本次 Procedure（不新建发放单）
            medicationDispenseDomainService.linkDispenseProcedure(
                nurseStationOrderExecuteMapper.toLinkDispenseProcedureCommand(
                    dispense.getId(), procedureId, item.getPlanTime(), operatorId));
            // 按 FIFO 分段 dispense 锚点定位待激活费用行（签发后 PLANNED；未升位时兼容 DRAFT）
            ChargeItem activatableCharge = this.findActivatableChargeForDispense(
                context, order.getEncounterId(), dispense.getId(), TableNameConstants.MED_MEDICATION_DISPENSE);
            // PLANNED/DRAFT → 待结算，performerId 记录实际执行护士
            chargeItemDomainService.activateChargeByExe(
                nurseStationOrderExecuteMapper.toActivateChargeByExeCommand(
                    activatableCharge.getId(), procedureId, actualTime, performerId));
        }
    }

    /**
     * 诊疗执行分轨：长期新建 Charge；临时激活签发侧 PLANNED Charge。
     *
     * <p>BR-IP-EXE-018：诊嘱无发药联动；长期每次执行新建待结算费用行，
     * 临时激活签发侧已计划（PLANNED）费用行。</p>
     */
    private void executeServicePath(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        OrderExecutionContext orderCtx,
        Long procedureId,
        Long operatorId,
        Long performerId,
        LocalDateTime actualTime,
        String therapy,
        Long orgId,
        WriteContextBundle context) {
        ServiceRequestListModel svc = orderCtx.svcDetail;
        if (svc == null || svc.getQuantity() == null || svc.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_303,
                DoctorStationAdviceConstants.MSG_CHARGE_QUANTITY_REQUIRED);
        }
        BigDecimal quantity = svc.getQuantity();
        String unitCode = org.apache.commons.lang3.StringUtils.trimToNull(svc.getUnitCode());
        if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
            // 长期诊疗：每次 planTime 新建待结算费用行（无发药联动）
            Long serviceId = item.getOrderId();
            ChargeItem template = this.findChargeTemplate(context, order.getEncounterId(),
                TableNameConstants.WKF_SERVICE_REQUEST, serviceId);
            Long activityId = order.getActivityId();
            if (svc.getActivityId() != null) {
                activityId = svc.getActivityId();
            }
            ChargeItemDefinition priceLine = this.requireSalePriceLineForExecution(
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                activityId);
            ProductDefinitionDisplayDto activityProduct =
                this.loadProductDefinition(TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
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
        } else {
            // 临时诊疗：激活签发侧 PLANNED Charge（兼容未升位的 DRAFT），关联本次 Procedure
            ChargeItem activatableCharge = this.findActivatableChargeForService(
                context, order.getEncounterId(), item.getOrderId(), TableNameConstants.WKF_SERVICE_REQUEST);
            chargeItemDomainService.activateChargeByExe(
                nurseStationOrderExecuteMapper.toActivateChargeByExeCommand(
                    activatableCharge.getId(), procedureId, actualTime, performerId));
        }
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
                    item, order, bindItem, operatorId, parentProcedureId, orgId, context);
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
        // ③ 新建待结算 Charge，service=派生 Request，product=ActivityDefinition
        BigDecimal quantity = this.resolveBindItemQuantity(bindItem);
        String unitCode = org.apache.commons.lang3.StringUtils.trimToNull(bindItem.getUnitCode());
        ChargeItemDefinition priceLine = this.requireSalePriceLineForExecution(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            bindItem.getActivityId());
        ProductDefinitionDisplayDto activityProduct =
            this.loadProductDefinition(TableNameConstants.WKF_ACTIVITY_DEFINITION, bindItem.getActivityId());
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
     * <p>DeviceRequest（已完成）→ 临时路径草稿 Dispense → 执行直发已发放 Dispense → 待结算 Charge。
     * 批号/追溯使用占位常量，<b>不</b>调用库存 QueryService 校验或扣减——库存由耗材发放页统一处理。</p>
     */
    private void executeBindDeviceItem(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        Long operatorId,
        Long parentProcedureId,
        Long orgId,
        WriteContextBundle context) {
        Long deviceDefId = bindItem.getDeviceId();
        BigDecimal quantity = this.resolveBindItemQuantity(bindItem);
        String unitCode = org.apache.commons.lang3.StringUtils.trimToNull(bindItem.getUnitCode());
        // ① 创建已完成态 DeviceRequest（performLocation 暂空，库房由后续耗材页处理）
        List<Long> deviceReqIds = deviceRequestDomainService.createExecutedDeviceOrder(
            nurseStationOrderExecuteMapper.toCreateExecutedDeviceOrderCommand(
                deviceDefId,
                quantity,
                null,
                order.getEncounterId(),
                order.getPatientId(),
                operatorId));
        Long deviceReqId = deviceReqIds.get(0);
        // ② 生成临时路径草稿 Dispense，作为直发模板
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
        // ③ 执行直发：批号/追溯占位，挂接主医嘱 Procedure
        Long dispenseId = deviceDispenseDomainService.generateIssuedDeviceDispenseFromExecution(
            nurseStationOrderExecuteMapper.toGenerateIssuedDeviceDispenseFromExecutionCommand(
                deviceReqId,
                parentProcedureId,
                InpatientNurseStationConstants.INPATIENT_NURSE_EXECUTE_DEVICE_LOT_PLACEHOLDER,
                InpatientNurseStationConstants.INPATIENT_NURSE_EXECUTE_DEVICE_TRACE_PLACEHOLDER,
                quantity,
                operatorId));
        // ④ 新建待结算 Charge，关联 DeviceRequest + DeviceDispense
        ChargeItemDefinition priceLine = this.requireSalePriceLineForExecution(
            TableNameConstants.ADM_DEVICE_DEFINITION,
            deviceDefId);
        ProductDefinitionDisplayDto deviceProduct =
            this.loadProductDefinition(TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId);
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
     * 查找临时药嘱可挂接的草稿发放单（DRAFT 态）。
     *
     * @param medReqId 药品申请主键
     * @param context  写上下文缓存
     * @return 草稿 Dispense
     * @throws BusinessException EX-IP-EXE-303 未找到
     */
    private MedicationDispense findDraftMedicationDispense(Long medReqId, WriteContextBundle context) {
        List<MedicationDispense> dispenses = context.dispensesByMedReqId.get(medReqId);
        if (dispenses == null) {
            // 首次访问该 medReq：批量查发放单并写入上下文缓存
            dispenses = medicationDispenseQueryService.listMedicationDispensesByMedReqIds(List.of(medReqId));
            context.dispensesByMedReqId.put(medReqId, dispenses);
        }
        for (MedicationDispense row : dispenses) {
            if (row != null && row.isDraftStatus()) {
                // 临时药嘱签发侧应已生成唯一草稿发放单
                return row;
            }
        }
        throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "未找到可挂接的草稿发放单");
    }

    /**
     * 查找临时医嘱执行可激活的费用行（签发后 PLANNED；未升位时兼容 DRAFT）。
     *
     * @param context      写上下文缓存
     * @param encounterId  就诊主键
     * @param serviceId    服务主键（medReqId 或 serviceReqId）
     * @param serviceTable 服务表名
     * @return 可激活 ChargeItem
     * @throws BusinessException EX-IP-EXE-303 未找到
     */
    private ChargeItem findActivatableChargeForService(
        WriteContextBundle context,
        Long encounterId,
        Long serviceId,
        String serviceTable) {
        List<ChargeItem> charges = this.loadEncounterCharges(context, encounterId);
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
            if (this.isActivatableByExecutionStatus(row.getStatusEnum())) {
                return row;
            }
        }
        throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "未找到可激活的待收费费用行");
    }

    /**
     * 查找临时药嘱 FIFO 分段执行可激活的费用行（按 dispense 锚点；签发后 PLANNED）。
     *
     * @param context       写上下文缓存
     * @param encounterId   就诊主键
     * @param dispenseId    发放单主键
     * @param dispenseTable 发放物理表名
     * @return 可激活 ChargeItem
     * @throws BusinessException EX-IP-EXE-303 未找到
     */
    private ChargeItem findActivatableChargeForDispense(
        WriteContextBundle context,
        Long encounterId,
        Long dispenseId,
        String dispenseTable) {
        List<ChargeItem> charges = this.loadEncounterCharges(context, encounterId);
        for (ChargeItem row : charges) {
            if (row == null) {
                continue;
            }
            if (!dispenseTable.equals(row.getDispenseTable())) {
                continue;
            }
            if (!dispenseId.equals(row.getDispenseId())) {
                continue;
            }
            if (this.isActivatableByExecutionStatus(row.getStatusEnum())) {
                return row;
            }
        }
        throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "未找到可激活的待收费费用行");
    }

    /**
     * 按就诊懒加载费用行列表并写入写上下文缓存。
     *
     * @param context     写上下文
     * @param encounterId 就诊主键
     * @return 该就诊下全部费用行
     */
    private List<ChargeItem> loadEncounterCharges(WriteContextBundle context, Long encounterId) {
        List<ChargeItem> charges = context.chargesByEncounterId.get(encounterId);
        if (charges == null) {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterId(encounterId);
            charges = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
            context.chargesByEncounterId.put(encounterId, charges);
        }
        return charges;
    }

    /**
     * 判断费用行是否可被临时医嘱执行激活（PLANNED 或 DRAFT）。
     *
     * @param statusEnum 收费状态持久化码
     * @return 可激活时 true
     */
    private boolean isActivatableByExecutionStatus(String statusEnum) {
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 长期执行新建费用：药嘱须始终解析定价行以做单位比例换算；诊疗等模板已带 definitionId 时可跳过查价。
     *
     * @param template         签发侧费用模板；可空
     * @param productTable     产品定义表
     * @param productId        产品定义主键
     * @param medicationPath   药嘱执行路径时为 true
     * @return 销售定价行；非药嘱且模板已含 definitionId 时返回 {@code null}
     * @throws BusinessException 缺少产品主键或未配置开立口径定价时抛出
     */
    private ChargeItemDefinition resolveSalePriceLineForPlannedCharge(
        ChargeItem template,
        String productTable,
        Long productId,
        boolean medicationPath) {
        if (medicationPath) {
            return this.requireSalePriceLineForExecution(productTable, productId);
        }
        if (template != null && template.getDefinitionId() != null) {
            return null;
        }
        return this.requireSalePriceLineForExecution(productTable, productId);
    }

    /**
     * 加载单条药品定义读模型，供护士执行新建 Charge 时按单位槽换算单价。
     *
     * @param medicationId 药品定义主键
     * @return 读模型；未命中时 {@code null}
     */
    private ProductDefinitionDisplayDto loadMedicationProductDefinition(Long medicationId) {
        return this.loadProductDefinition(TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
    }

    /**
     * 按产品定义表与主键加载读模型，供执行计费比例换算。
     */
    private ProductDefinitionDisplayDto loadProductDefinition(String productTable, Long productDefinitionId) {
        if (productDefinitionId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(productTable);
        lookup.setProductDefinitionId(productDefinitionId);
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(productTable, productDefinitionId);
        return loaded.get(key);
    }

    /**
     * 按产品定义加载开立口径销售定价行（与住院医生站 {@code firstPriceLine} 同构）。
     *
     * @param instanceTable 定价实例表（药品/诊疗/耗材定义表）
     * @param instanceId    定价实例主键
     * @return 销售定价行
     * @throws BusinessException 实例主键为空或未配置定价时抛出
     */
    private ChargeItemDefinition requireSalePriceLineForExecution(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_303,
                "产品定义主键缺失，无法生成执行费用");
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        ChargeItemDefinition priceLine =
            InpatientDoctorStationAdviceCatalogAssembler.selectFirstPriceLineForChargeDraft(instanceTable, priceLines);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_DEFINITION_MISSING_FOR_SAVE);
        }
        return priceLine;
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
        Long practitionerId = currentUser.getCurrentPractitionerId();
        List<Long> managedWardIds = this.resolveManagedWardIds(practitionerId);
        Set<Long> encounterIds = new LinkedHashSet<>();
        // 批量解析 OrderInfo 骨架，建立 orderId:orderType → OrderInfo 索引
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            InpatientOrderListSkeleton order = inpatientOrderLookupLoader.mustGetByOrderTypeAndId(
                item.getOrderType(), item.getOrderId());
            bundle.orderByKey.put(this.buildOrderKey(item), order);
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
        // 写 Gate 需比对「当前 pending 槽位」与 DB 中 Procedure 历史是否一致
        for (NurseStationOrderExecuteWriteSlotDto item : items) {
            String requestTable = this.resolveRequestTable(item.getOrderType());
            List<ProcedureRecordQueryModel> procedures =
                procedureQueryService.getProcedureRecords(item.getOrderId(), requestTable);
            bundle.proceduresByOrderKey.put(this.buildOrderKey(item), procedures);
        }
        return bundle;
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
        // occurrence 防重：同一 planTime 已有 COMPLETED/STOPPED/CANCEL 终态则拒绝（EX-IP-EXE-301）
        List<ProcedureRecordQueryModel> procedures = context.proceduresByOrderKey.get(this.buildOrderKey(item));
        boolean pending = nurseStationOrderExecuteListAssembler.isPlanTimePending(
            order,
            orderCtx.medDetail,
            orderCtx.svcDetail,
            item.getPlanTime(),
            procedures);
        if (!pending) {
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
        String tab = StringUtils.trimToEmpty(request.getExecuteTab());
        if (StringUtils.isBlank(tab) || !this.isAllowedExecuteTab(tab)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行 Tab 不合法");
        }
        String therapy = request.getTherapyType();
        if (StringUtils.isBlank(therapy)) {
            // 未传长临筛选视为 all，不做额外校验
            return;
        }
        if (!this.isAllowedTherapyFilter(StringUtils.trimToEmpty(therapy))) {
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
     * 解析当前医务人员管理病区库位主键列表（护士站可见范围基础）。
     *
     * @param practitionerId 医务人员主键；可 null
     * @return 病区 locationId 列表；无管理病区时为空列表
     */
    private List<Long> resolveManagedWardIds(Long practitionerId) {
        // 护士站可见性：当前用户所管理病区（非全机构）
        List<Location> wards = locationQueryService.listManagedWardsForPractitioner(practitionerId);
        List<Long> ids = new ArrayList<>();
        for (Location ward : wards) {
            if (ward != null && ward.getId() != null) {
                ids.add(ward.getId());
            }
        }
        return ids;
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
     * <p>Charge/Dispense 按 encounterId/medReqId 懒加载缓存，避免同一批次重复查库。</p>
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
        /** encounterId → 费用行列表（懒加载）。 */
        private final Map<Long, List<ChargeItem>> chargesByEncounterId = new HashMap<>();
        /** medReqId → 发放单列表（懒加载）。 */
        private final Map<Long, List<MedicationDispense>> dispensesByMedReqId = new HashMap<>();
        /** 护士可见 encounterId 集合。 */
        private Set<Long> visibleEncounterIds = new HashSet<>();
        /** encounterId → organizationId。 */
        private Map<Long, Long> encounterOrgIds = new HashMap<>();

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
