package com.openhis.health.application.outpatient.nursestation.service;

import com.openhis.health.domain.share.constants.OutpatientNurseStationConstants;
import com.openhis.health.application.common.assembler.advice.AdviceChargeAnchorRollupSupport;
import com.openhis.health.application.common.assembler.advice.AdviceChargeDisplayStatusSupport;
import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.assembler.advice.AdviceChargeDisplayStatusSupport.ListDisplayStatus;
import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.common.assembler.order.OrderRequestCatalogWarmAssembler;
import com.openhis.health.application.querysupport.order.dto.OrderRequestCatalogWarmContext;
import com.openhis.health.application.outpatient.nursestation.dto.NurseStationTreatmentLineDto;
import com.openhis.health.application.outpatient.nursestation.mapper.OutpatientNurseStationMapper;
import com.openhis.health.application.outpatient.nursestation.request.BatchCancelDispositionRequest;
import com.openhis.health.application.outpatient.nursestation.request.BatchPerformDispositionRequest;
import com.openhis.health.application.outpatient.nursestation.request.ListNetRemainingProcedureRecordsForNurseStationRequest;
import com.openhis.health.application.outpatient.nursestation.request.ListProcedureRecordsForNurseStationRequest;
import com.openhis.health.application.outpatient.nursestation.request.PageNurseStationPatientQueueRequest;
import com.openhis.health.application.outpatient.nursestation.request.QueryEncounterTreatmentSheetRequest;
import com.openhis.health.application.outpatient.nursestation.response.BatchCancelDispositionResponse;
import com.openhis.health.application.outpatient.nursestation.response.BatchPerformDispositionResponse;
import com.openhis.health.application.outpatient.nursestation.response.ListNetRemainingProcedureRecordsForNurseStationResponse;
import com.openhis.health.application.outpatient.nursestation.response.ListProcedureRecordsForNurseStationResponse;
import com.openhis.health.application.outpatient.nursestation.response.PageNurseStationPatientQueueResponse;
import com.openhis.health.application.outpatient.nursestation.response.QueryEncounterTreatmentSheetResponse;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.application.querysupport.procedure.OrderExecutionSheetLoader;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.OrderExecutionSheetQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseValidationModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊护士站应用编排：患者队列、处置单三列表、执行记录抽屉、净剩可取消列表、批量处置执行与批量取消执行（IF-NUR-01～05、IF-NUR-03B）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EncounterQueryService}：IF-NUR-01 门诊护士站队列分页与 IF-NUR-02～04 就诊存在性门闸。</li>
 *   <li>{@link ServiceRequestQueryService} / {@link MedicationRequestQueryService} / {@link DeviceRequestQueryService}：IF-NUR-02 列表装载与 IF-NUR-04 次数门控只读输入。</li>
 *   <li>{@link ChargeItemQueryService}：IF-NUR-02 计费聚合与 IF-NUR-04 已计费门控只读输入。</li>
 *   <li>{@link ProcedureQueryService}：IF-NUR-03 医嘱执行单分页与 IF-NUR-02 次数统计、IF-NUR-04/05 门控输入。</li>
 *   <li>{@link ProcedureDomainService}：IF-NUR-04/05 执行登记/取消写路径。</li>
 *   <li>{@link DeviceDispenseQueryService} / {@link DeviceDispenseDomainService} / {@link DeviceRequestDomainService} / {@link InventoryItemDomainService}：IF-NUR-02 发放态装载与 IF-NUR-04/05 耗材写路径。</li>
 *   <li>{@link OutpatientNurseStationMapper}：Request→Query/Command 与查询模型/DTO→Response 字段搬运。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为 {@code POST /api/app/outpatientNurseStation/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientNurseStationAppService {

    /** 就诊只读查询：IF-NUR-01 护士站队列分页与 IF-NUR-02～04 就诊存在性门闸。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /**
     * 诊疗服务申请只读查询：IF-NUR-02 诊疗列表与 IF-NUR-04 计划次数门控。
     */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 药品申请只读查询：IF-NUR-02 药品列表与 IF-NUR-04 计划次数门控。
     */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 药品发放只读查询：IF-NUR-02 收费行发药锚点 → 药品申请 id 映射。
     */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 耗材请求只读查询：IF-NUR-02 耗材列表装载。
     */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 耗材发放只读查询：IF-NUR-02 发放态/主发放行定位与 IF-NUR-04 待发门控。
     */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 收费明细只读查询：IF-NUR-02 计费聚合与 IF-NUR-04 已计费门控。
     */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 执行记录只读查询：IF-NUR-02 诊疗次数统计与 IF-NUR-04 净次数门控。
     */
    private final ProcedureQueryService procedureQueryService;

    /** 医嘱执行单跨聚合读组装（Procedure + 请求 content_json + 执行人）。 */
    private final OrderExecutionSheetLoader orderExecutionSheetLoader;

    /**
     * 执行记录写服务：IF-NUR-04 诊疗/药品执行登记（受 AS-SDD DOC-CONFLICT 约束）。
     */
    private final ProcedureDomainService procedureDomainService;

    /**
     * 耗材请求写服务：IF-NUR-04 耗材执行确认。
     */
    private final DeviceRequestDomainService deviceRequestDomainService;

    /**
     * 耗材发放写服务：IF-NUR-04 拣配/批号门控/发放推进。
     */
    private final DeviceDispenseDomainService deviceDispenseDomainService;

    /**
     * 库存项写服务：IF-NUR-04 合并扣减（允许空映射短路）。
     */
    private final InventoryItemDomainService inventoryItemDomainService;

    /**
     * 库存项只读查询：IF-NUR-04 按批号定位耗材账面行以合并扣减。
     */
    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 护士站装配器：DTO/命令字段搬运。
     */
    private final OutpatientNurseStationMapper outpatientNurseStationMapper;

    /**
     * IF-NUR-02 处置单目录与类目展示批量预热（application.common 复用）。
     */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;

    /**
     * 当前登录用户：IF-NUR-01 Gate 与工作站 {@code organizationId} 对齐。
     */
    private final ICurrentUser currentUser;

    /**
     * IF-NUR-01：分页查询门诊护士站患者队列。
     *
     * @param request 队列筛选与分页条件
     * @return 当前页队列与总数
     * @throws BusinessException 读路径异常时抛出（EX-NUR-101 语义）
     */
    public PageNurseStationPatientQueueResponse pageNurseStationPatientQueue(PageNurseStationPatientQueueRequest request) {
        // 请求体空值门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        // Gate：机构仅以当前登录用户所属组织单元为准（B.10.11；请求体禁止传 organizationId）。
        Long sessionOrgId = this.currentUser.getCurrentOrganizationUnitId();
        if (sessionOrgId == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "当前用户未绑定组织单元，无法查询护士站队列");
        }
        // Pre：将 HTTP 请求映射为分页入参（pageNo 1-based 与关键字口径由 AbstractPageQuery 承载）。
        com.openhis.health.domain.share.query.AbstractPageQuery queueQuery =
            this.outpatientNurseStationMapper.toNurseStationOutpatientQueueQuery(request);
        // Pre：调用就诊上下文护士站队列分页；准入 EXISTS 已在领域查询内实现（BR-NUR-001）。
        Page<OutpatientEncounterContextQueryModel> page =
            this.encounterContextFacade.pageNurseStationOutpatientQueue(queueQuery);
        // Asm：装配分页响应，供护士站左侧患者列表直接渲染。
        return this.outpatientNurseStationMapper.toPageNurseStationPatientQueueResponse(page);
    }

    /**
     * IF-NUR-02：按就诊查询处置单（三列表同构）。
     *
     * @param request 就诊主键与诊疗类目过滤条件
     * @return 三列表处置行
     * @throws BusinessException 就诊不存在或查询失败（EX-NUR-102 语义）
     */
    public QueryEncounterTreatmentSheetResponse queryEncounterTreatmentSheet(QueryEncounterTreatmentSheetRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        // Gate：就诊主键必须显式传入，避免护士在未知就诊上下文加载处置单。
        // Pre：强制加载就诊详情，作为后续患者锚点一致性校验输入（不作为写前唯一性真源）。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：装载收费明细全集，用于三列表行级计费聚合（Q-ORD-006）。
        ChargeItemQueryCommand chargeCmd = this.outpatientNurseStationMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor =
            AdviceChargeAnchorRollupSupport.buildRequestAnchorChargeIndex(
                chargeItems, this.medicationDispenseQueryService, this.deviceDispenseQueryService);
        // Pre：装载诊疗/药品/耗材请求列表，并在应用层过滤已完成态（护士站处置视图口径）。
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId());
        List<MedicationRequestListQueryModel> medicationRequests =
            this.medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId());
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(request.getEncounterId());
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(request.getEncounterId());
        List<Long> deviceReqIds = new ArrayList<>();
        for (DeviceRequestListModel dr : deviceRequests) {
            if (dr.getId() != null) {
                deviceReqIds.add(dr.getId());
            }
        }
        // Step：耗材医嘱→发放单 id 列表一次 IN 查询后内存分组（对齐 A.9.1，禁止按医嘱循环查库）
        Map<Long, List<Long>> deviceDispenseIdsByDeviceRequest =
            this.deviceDispenseQueryService.mapDeviceDispenseIdsByDeviceRequestIds(deviceReqIds);
        // Pre：批量预热诊疗/药品/耗材定义与类目术语，供三列表 itemName 与 medCategoryCodeText 等展示列装配（Q-ORD-006 §3.4.3）。
        OrderRequestCatalogWarmContext catalogContext =
            this.orderRequestCatalogWarmAssembler.prepareFromTreatmentSheetRows(
                serviceRequests, medicationRequests, deviceRequests);
        List<NurseStationTreatmentLineDto> serviceLines = new ArrayList<>();
        for (ServiceRequestListModel row : serviceRequests) {
            if (AdviceChargeDisplayStatusSupport.isRefundOrderCopyRequest(row.getRefundServiceId())) {
                continue;
            }
            String serviceChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), chargeByAnchor);
            if (!AdviceChargeDisplayStatusSupport.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), serviceChargeStatus)) {
                continue;
            }
            if (StringUtils.isNotBlank(request.getServiceCategoryCode())) {
                if (row.getTherapyEnum() == null) {
                    continue;
                }
                if (!request.getServiceCategoryCode().trim().equals(row.getTherapyEnum().trim())) {
                    continue;
                }
            }
            NurseStationTreatmentLineDto line =
                this.buildServiceLine(row, request.getEncounterId(), chargeByAnchor, catalogContext);
            List<ProcedureRecordQueryModel> procedures = this.procedureQueryService.getProcedureRecords(
                row.getId(), TableNameConstants.WKF_SERVICE_REQUEST);
            int completed = 0;
            int aborted = 0;
            for (ProcedureRecordQueryModel p : procedures) {
                if (EventStatus.COMPLETED.getCode().equals(p.getStatusEnum())) {
                    completed++;
                }
                if (EventStatus.isCancelExecutionStatusCode(p.getStatusEnum())) {
                    aborted++;
                }
            }
            line.setPerformCount(Integer.valueOf(completed));
            line.setCancelCount(Integer.valueOf(aborted));
            this.finalizeNurseTreatmentLineDisplay(line);
            serviceLines.add(line);
        }
        List<NurseStationTreatmentLineDto> medicationLines = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRequests) {
            if (AdviceChargeDisplayStatusSupport.isRefundOrderCopyRequest(row.getRefundMedicineId())) {
                continue;
            }
            String medicationChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), chargeByAnchor);
            if (!AdviceChargeDisplayStatusSupport.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), medicationChargeStatus)) {
                continue;
            }
            NurseStationTreatmentLineDto medicationLine =
                this.buildMedicationLine(row, request.getEncounterId(), chargeByAnchor, catalogContext);
            this.finalizeNurseTreatmentLineDisplay(medicationLine);
            medicationLines.add(medicationLine);
        }
        List<NurseStationTreatmentLineDto> deviceLines = new ArrayList<>();
        for (DeviceRequestListModel row : deviceRequests) {
            if (AdviceChargeDisplayStatusSupport.isRefundOrderCopyRequest(row.getRefundDeviceId())) {
                continue;
            }
            String deviceChargeStatus = this.resolveAggregatedChargeStatus(
                TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), chargeByAnchor);
            if (!AdviceChargeDisplayStatusSupport.shouldIncludeNurseStationTreatmentSheetRow(
                row.getStatusEnum(), TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), deviceChargeStatus)) {
                continue;
            }
            NurseStationTreatmentLineDto deviceLine = this.buildDeviceLine(
                row, request.getEncounterId(), chargeByAnchor, deviceDispenses, deviceDispenseIdsByDeviceRequest, catalogContext);
            this.finalizeNurseTreatmentLineDisplay(deviceLine);
            deviceLines.add(deviceLine);
        }
        // Asm：将三列表与就诊头信息包装为统一响应（encounter 头信息字段由前端自行使用 encounterId 二次查询时可省略）。
        return this.outpatientNurseStationMapper.toQueryEncounterTreatmentSheetResponse(
            request, encounter, serviceLines, medicationLines, deviceLines);
    }

    /**
     * IF-NUR-03：分页查询诊疗执行记录（执行记录抽屉）。
     *
     * @param request 就诊收敛与分页筛选条件
     * @return 执行记录分页结果
     * @throws BusinessException 就诊不存在或查询失败（EX-NUR-103 语义）
     */
    public ListProcedureRecordsForNurseStationResponse listProcedureRecordsForNurseStation(
        ListProcedureRecordsForNurseStationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        // Gate：就诊主键必须显式传入，避免跨就诊误查执行记录抽屉数据。
        // Pre：强制加载就诊，保证护士当前上下文与执行单筛选 encounterId 同源。
        this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：将抽屉请求映射为读模型分页查询（pageNo 1-based 与 sort 口径由 OrderExecutionSheetPageQuery 继承 AbstractPageQuery）。
        OrderExecutionSheetPageQuery pageQuery =
            this.outpatientNurseStationMapper.toOrderExecutionSheetPageQuery(request);
        Page<OrderExecutionSheetQueryModel> page =
            this.orderExecutionSheetLoader.pageOrderExecutionSheet(pageQuery);
        // Asm：装配抽屉响应。
        return this.outpatientNurseStationMapper.toListProcedureRecordsForNurseStationResponse(page);
    }

    /**
     * IF-NUR-03B：查询净剩可取消的已完成执行记录（对冲后仍有效的 COMPLETED 行）。
     *
     * <p>已存在 {@code CANCEL.refund_id} 指向的 COMPLETED 与对应 CANCEL 均不返回，供取消抽屉勾选。</p>
     *
     * @param request 就诊锚点与可选来源请求缩窄条件
     * @return 净剩执行记录列表
     * @throws BusinessException 就诊不存在或缩窄参数不合法
     */
    public ListNetRemainingProcedureRecordsForNurseStationResponse listNetRemainingProcedureRecordsForNurseStation(
        ListNetRemainingProcedureRecordsForNurseStationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        if (request.getRequestId() != null && StringUtils.isBlank(request.getRequestTable())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "缩窄来源请求时 requestTable 不能为空");
        }
        // Pre：就诊存在，保证列表与取消操作同属一次就诊上下文。
        this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        List<ProcedureRecordQueryModel> netRows = this.procedureQueryService.listNetRemainingCompletedProcedureRecords(
            request.getEncounterId(),
            request.getRequestId(),
            request.getRequestTable());
        // Asm：按记录时间、主键倒序，便于护士优先看到最近执行。
        netRows.sort(this.netRemainingProcedureRecordComparator());
        return this.outpatientNurseStationMapper.toListNetRemainingProcedureRecordsForNurseStationResponse(netRows);
    }

    /**
     * IF-NUR-04：批量处置执行（诊疗/药品登记与耗材发放）。
     *
     * @param request 批量勾选与操作人上下文
     * @return 成功标记与各路径实际完成主键集合
     * @throws BusinessException 门控失败、未计费、次数已满、耗材房缺失或领域写失败（EX-NUR-201～208 语义）
     */
    public BatchPerformDispositionResponse batchPerformDisposition(BatchPerformDispositionRequest request) {
        // 门禁校验：请求参数与勾选项目组合合法性
        this.validateBatchPerformDispositionGate(request);
        // Pre：强制加载就诊，保证批量执行锚点就诊一致且存在。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // 加载收费明细用于后续已计费门控
        ChargeItemQueryCommand chargeCmd = this.outpatientNurseStationMapper.toChargeItemQueryCommand(request.getEncounterId());
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        // 过滤已计费诊疗与药品申请：仅保留存在已结算收费项的请求
        List<Long> retainedServiceIds = this.retainBillableRequestIds(request.getServiceRequestIds(), TableNameConstants.WKF_SERVICE_REQUEST, chargeItems);
        List<Long> retainedMedicationIds = this.retainBillableRequestIds(request.getMedicationRequestIds(), TableNameConstants.MED_MEDICATION_REQUEST, chargeItems);
        // 判断是否存在耗材执行项
        boolean hasDevice = request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty();
        // 耗材执行必须指定库房位置
        if (hasDevice && request.getInventoryLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "请选择耗材房后再执行发放");
        }
        // 无任何可执行项目时阻断流程
        if (retainedServiceIds.isEmpty() && retainedMedicationIds.isEmpty() && !hasDevice) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "所选项目无已计费收费项");
        }
        List<Long> performedServiceIds = new ArrayList<>();
        List<Long> performedMedicationIds = new ArrayList<>();
        List<Long> completedDispenseIds = new ArrayList<>();
        Map<Long, ServiceRequestListModel> serviceRowById = new HashMap<>();
        for (ServiceRequestListModel row : this.serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId())) {
            if (row.getId() != null) {
                serviceRowById.put(row.getId(), row);
            }
        }
        Map<Long, MedicationRequestListQueryModel> medicationRowById = new HashMap<>();
        for (MedicationRequestListQueryModel row : this.medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId())) {
            if (row.getId() != null) {
                medicationRowById.put(row.getId(), row);
            }
        }
        // Cmd：诊疗路径——逐条校验净次数门控后执行 create→start→complete（护士站通过 CreateProcedureRecordCommand#skipDuplicateRequestAnchorCheck 允许多次登记）。
        for (Long serviceRequestId : retainedServiceIds) {
            ServiceRequestQueryModel sr = this.serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
            ServiceRequestListModel listRow = serviceRowById.get(serviceRequestId);
            int planned = this.resolvePlannedServicePerformCount(listRow);
            int net = this.netProcedureCount(serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
            if (net >= planned) {
                // 计划次数已满，本诊疗请求不再登记完成态。
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
                this.outpatientNurseStationMapper.toCreateProcedureRecordCommand(
                    serviceRequestId,
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    request.getEncounterId(),
                    encounter.getPatientId(),
                    sr.getCategoryEnum(),
                    now,
                    request.getOperatorId());
            Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.outpatientNurseStationMapper.toStartProcedureExecutionCommand(procedureId));
            this.procedureDomainService.completeProcedureRecord(
                this.outpatientNurseStationMapper.toCompleteProcedureRecordCommand(procedureId, request.getOperatorId()));
            performedServiceIds.add(serviceRequestId);
        }
        // Cmd：药品路径——净次数门控计划次数取自 execute_num（BR-NUR-005，见 MedicationRequestDetailQueryModel）。
        for (Long medicationRequestId : retainedMedicationIds) {
            MedicationRequestDetailQueryModel med = this.medicationRequestQueryService.loadMedicationRequestById(medicationRequestId);
            MedicationRequestListQueryModel listRow = medicationRowById.get(medicationRequestId);
            int planned = this.resolvePlannedMedicationPerformCount(listRow, med);
            int net = this.netProcedureCount(medicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST);
            if (net >= planned) {
                // 计划次数已满，本药品请求不再登记完成态。
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            String category = med.getTherapyEnum();
            if (category == null) {
                category = OutpatientNurseStationConstants.MEDICATION_PROCEDURE_CATEGORY_FALLBACK;
            }
            com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
                this.outpatientNurseStationMapper.toCreateProcedureRecordCommand(
                    medicationRequestId,
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    request.getEncounterId(),
                    encounter.getPatientId(),
                    category,
                    now,
                    request.getOperatorId());
            Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
            this.procedureDomainService.startProcedureExecution(
                this.outpatientNurseStationMapper.toStartProcedureExecutionCommand(procedureId));
            this.procedureDomainService.completeProcedureRecord(
                this.outpatientNurseStationMapper.toCompleteProcedureRecordCommand(procedureId, request.getOperatorId()));
            performedMedicationIds.add(medicationRequestId);
        }
        // Cmd：耗材路径——逐条拣配/批号门控/发放，并回写耗材请求完成态；库存按真实批号+库位解析后合并扣减（占位批号则跳过扣减）。
        if (hasDevice) {
            List<Long> sortedDispenseIds = new ArrayList<>(request.getDeviceDispenseIds());
            Collections.sort(sortedDispenseIds);
            Long performerId = this.resolvePerformerId(request.getOperatorId(), request.getPerformerPractitionerId());
            for (Long dispenseId : sortedDispenseIds) {
                this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
                DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
                DeviceDispenseValidationModel preparable = this.deviceDispenseQueryService.validateDeviceDispensePreparable(dispenseId);
                if (preparable == null || Boolean.FALSE.equals(preparable.getAllow())) {
                    // 并发或非准备中态，跳过本发放行（BR-NUR-008）。
                    continue;
                }
                String matchKey = String.valueOf(dispenseId);
                this.deviceDispenseDomainService.matchDispenseLotNumber(
                    this.outpatientNurseStationMapper.toMatchDispenseLotNumberCommand(matchKey, request.getOperatorId()));
                this.deviceDispenseDomainService.prepareDeviceDispense(
                    this.outpatientNurseStationMapper.toPrepareDeviceDispenseCommand(
                        dispenseId,
                        request.getOperatorId(),
                        OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER,
                        OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_TRACE_PLACEHOLDER,
                        dispense.getQuantity(),
                        request.getOperatorId()));
                this.deviceDispenseDomainService.dispenseDevice(
                    this.outpatientNurseStationMapper.toDispenseDeviceCommand(
                        dispenseId, performerId, dispense.getQuantity(), request.getOperatorId()));
                this.deviceRequestDomainService.executeDeviceOrder(
                    this.outpatientNurseStationMapper.toExecuteDeviceOrderCommand(
                        dispense.getDeviceReqId(), performerId, null, request.getOperatorId()));
                completedDispenseIds.add(dispenseId);
            }
        }
        Map<Long, BigDecimal> inventoryIdToDecreaseQty = new HashMap<>();
        for (Long dispenseId : completedDispenseIds) {
            DeviceDispenseModel after = this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
            Long locationId = after.getLocationId();
            if (locationId == null) {
                locationId = request.getInventoryLocationId();
            }
            Long deviceDefId = after.getDeviceDefId();
            String lot = after.getLotNumber();
            if (locationId == null || deviceDefId == null || StringUtils.isBlank(lot)) {
                continue;
            }
            String lotTrim = lot.trim();
            if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lotTrim)) {
                continue;
            }
            Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForDeviceDispenseBatch(
                TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId, locationId, lotTrim);
            BigDecimal decQty = this.resolveDeviceDispenseRestoreQuantity(after);
            if (decQty == null || decQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            this.mergeInventoryQuantityByItemId(inventoryIdToDecreaseQty, inventoryItemId, decQty);
        }
        this.inventoryItemDomainService.decreaseQuantitiesMerged(request.getOperatorId(), inventoryIdToDecreaseQty);
        // 审计：记录护士、就诊与批量勾选摘要，满足 AS-SDD §6.5 可追溯要求。
        log.info(
            "OutpatientNurseStation batchPerformDisposition operatorId={} encounterId={} services={} medications={} dispenses={} inventoryLocationId={}",
            request.getOperatorId(),
            request.getEncounterId(),
            request.getServiceRequestIds(),
            request.getMedicationRequestIds(),
            request.getDeviceDispenseIds(),
            request.getInventoryLocationId());
        BatchPerformDispositionResponse response = new BatchPerformDispositionResponse();
        response.setSuccess(Boolean.TRUE);
        response.setPerformedServiceIds(performedServiceIds);
        response.setPerformedMedicationIds(performedMedicationIds);
        response.setCompletedDeviceDispenseIds(completedDispenseIds);
        return response;
    }

    /**
     * IF-NUR-05：批量取消执行（诊疗/药品追加 CANCEL 与耗材发放回退）。
     *
     * <p>不含 {@link ChargeItemQueryService} / 收费回滚；与 IF-NUR-04 对称编排。</p>
     *
     * @param request 批量勾选、取消原因与操作人上下文
     * @return 成功标记与各路径实际取消主键集合
     * @throws BusinessException 门控失败、净次数为 0、发放态不可取消或领域写失败（EX-NUR-209～213 语义）
     */
    public BatchCancelDispositionResponse batchCancelDisposition(BatchCancelDispositionRequest request) {
        // Gate：入参、取消原因与至少一类勾选（EX-NUR-209）；不触碰 ChargeItem。
        this.validateBatchCancelDispositionGate(request);
        // Pre：就诊锚点存在，后续 Procedure 创建需患者主键。
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        List<Long> cancelledProcedureIds = new ArrayList<>();
        List<Long> revertedDeviceDispenseIds = new ArrayList<>();
        // Cmd：诊疗/药品——按前端勾选的已完成 procedureId 逐条追加 CANCEL（refund_id 指向该 COMPLETED，BR-NUR-011 / BR-PRO-005）。
        List<Long> procedureIds = this.normalizeDistinctProcedureIdList(request.getProcedureIds());
        for (Long completedProcedureId : procedureIds) {
            ProcedureRecordQueryModel completedRow =
                this.mustResolveCancellableCompletedProcedure(completedProcedureId, request.getEncounterId());
            String categoryEnum = completedRow.getCategoryEnum();
            if (categoryEnum == null) {
                if (TableNameConstants.MED_MEDICATION_REQUEST.equals(completedRow.getRequestTable())) {
                    categoryEnum = OutpatientNurseStationConstants.MEDICATION_PROCEDURE_CATEGORY_FALLBACK;
                } else {
                    throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行记录类目不能为空");
                }
            }
            this.appendProcedureCancelOffset(
                completedRow.getRequestId(),
                completedRow.getRequestTable(),
                request.getEncounterId(),
                encounter.getPatientId(),
                categoryEnum,
                request.getStatusReasonText(),
                request.getOperatorId(),
                completedProcedureId);
            cancelledProcedureIds.add(completedProcedureId);
        }
        boolean hasDevice = request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty();
        List<DeviceCancelRestoreSnapshot> deviceRestoreSnapshots = new ArrayList<>();
        if (hasDevice) {
            // Pre：按发放行 id 排序，回退前缓存批号/库位/数量（BR-NUR-012）。
            List<Long> sortedDispenseIds = new ArrayList<>(request.getDeviceDispenseIds());
            Collections.sort(sortedDispenseIds);
            for (Long dispenseId : sortedDispenseIds) {
                if (dispenseId == null) {
                    continue;
                }
                this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
                DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
                if (!this.isDeviceDispenseCancellable(dispense.getStatusEnum())) {
                    // 非已发放完成态，本行不可纳入本批回退。
                    continue;
                }
                BigDecimal restoreQty = this.resolveDeviceDispenseRestoreQuantity(dispense);
                deviceRestoreSnapshots.add(new DeviceCancelRestoreSnapshot(
                    dispense.getDeviceReqId(),
                    dispense.getLocationId(),
                    dispense.getDeviceDefId(),
                    dispense.getLotNumber(),
                    restoreQty));
                revertedDeviceDispenseIds.add(dispenseId);
            }
            if (!revertedDeviceDispenseIds.isEmpty()) {
                // Cmd 7：批量回退发放行至准备中（清空批号/追溯/已发量）。
                this.deviceDispenseDomainService.revertCompletedBatchToPreparation(
                    this.outpatientNurseStationMapper.toRevertCompletedBatchToPreparationCommand(
                        revertedDeviceDispenseIds, request.getOperatorId()));
                // Cmd 8：按回退前快照合并回补库存（真实批号；占位批号跳过，与 IF-NUR-04 扣减对称）。
                Map<Long, BigDecimal> inventoryIdToIncreaseQty = new HashMap<>();
                for (DeviceCancelRestoreSnapshot snapshot : deviceRestoreSnapshots) {
                    Long locationId = snapshot.getLocationId();
                    Long deviceDefId = snapshot.getDeviceDefId();
                    String lot = snapshot.getLotNumber();
                    if (locationId == null || deviceDefId == null || StringUtils.isBlank(lot)) {
                        continue;
                    }
                    String lotTrim = lot.trim();
                    if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lotTrim)) {
                        continue;
                    }
                    BigDecimal incQty = snapshot.getRestoreQty();
                    if (incQty == null || incQty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForDeviceDispenseBatch(
                        TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId, locationId, lotTrim);
                    this.mergeInventoryQuantityByItemId(inventoryIdToIncreaseQty, inventoryItemId, incQty);
                }
                if (!inventoryIdToIncreaseQty.isEmpty()) {
                    this.inventoryItemDomainService.increaseQuantities(
                        this.outpatientNurseStationMapper.toIncreaseQuantityCommand(
                            request.getOperatorId(), inventoryIdToIncreaseQty));
                }
                // Cmd 9：按耗材请求去重后撤销执行确认（与发放回退顺序见 SDD §3.6）。
                Set<Long> deviceRequestIdsToRevoke = new LinkedHashSet<>();
                for (DeviceCancelRestoreSnapshot snapshot : deviceRestoreSnapshots) {
                    if (snapshot.getDeviceReqId() != null) {
                        deviceRequestIdsToRevoke.add(snapshot.getDeviceReqId());
                    }
                }
                for (Long deviceRequestId : deviceRequestIdsToRevoke) {
                    this.deviceRequestDomainService.revokeDeviceOrderExecution(
                        this.outpatientNurseStationMapper.toRevokeDeviceOrderExecutionCommand(
                            deviceRequestId, request.getOperatorId()));
                }
            }
        }
        if (cancelledProcedureIds.isEmpty() && revertedDeviceDispenseIds.isEmpty()) {
            if (hasDevice) {
                // 勾选了耗材但无一行处于可回退态（EX-NUR-211）。
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "所选耗材发放状态不允许取消");
            }
            // 未抵消任何执行记录且未回退任何发放行（EX-NUR-210）。
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "无可取消的执行记录");
        }
        log.info(
            "OutpatientNurseStation batchCancelDisposition operatorId={} encounterId={} procedureIds={} dispenses={} statusReasonText={}",
            request.getOperatorId(),
            request.getEncounterId(),
            request.getProcedureIds(),
            request.getDeviceDispenseIds(),
            request.getStatusReasonText());
        BatchCancelDispositionResponse response = new BatchCancelDispositionResponse();
        response.setSuccess(Boolean.TRUE);
        response.setCancelledProcedureIds(cancelledProcedureIds);
        response.setRevertedDeviceDispenseIds(revertedDeviceDispenseIds);
        return response;
    }

    /**
     * IF-NUR-05 Gate：批量取消请求入参组合校验。
     *
     * @param request 入参
     * @throws BusinessException 未勾选任何可取消项或缺少操作人/就诊锚点/取消原因
     */
    private void validateBatchCancelDispositionGate(BatchCancelDispositionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        if (StringUtils.isBlank(request.getStatusReasonText())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "取消原因不能为空");
        }
        boolean any = false;
        if (request.getProcedureIds() != null && !request.getProcedureIds().isEmpty()) {
            any = true;
        }
        if (request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty()) {
            any = true;
        }
        if (!any) {
            // 诊疗/药品与耗材勾选均为空，无取消对象（EX-NUR-209）。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "请先勾选可取消的执行记录或已发放耗材");
        }
    }

    /**
     * 诊疗/药品路径：追加一条 CANCEL 执行记录（create→start→cancel，BR-NUR-011）。
     *
     * @param requestId        请求主键
     * @param requestTable     请求表持久化名
     * @param encounterId      就诊主键
     * @param patientId        患者主键
     * @param categoryEnum     执行类目码
     * @param statusReasonText              取消原因
     * @param operatorId                    操作人
     * @param offsetCompletedProcedureId    被抵消的已完成执行记录主键（写入新 CANCEL 行的 {@code refund_id}）
     */
    private void appendProcedureCancelOffset(
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        String statusReasonText,
        Long operatorId,
        Long offsetCompletedProcedureId) {
        LocalDateTime now = LocalDateTime.now();
        // 新建执行记录并跳过同请求锚点幂等，以支撑多次登记/取消（skipDuplicateRequestAnchorCheck）。
        com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand createCmd =
            this.outpatientNurseStationMapper.toCreateProcedureRecordCommand(
                requestId,
                requestTable,
                encounterId,
                patientId,
                categoryEnum,
                now,
                operatorId);
        Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
        // 进入进行中态后写入 CANCEL 终态（非对历史 COMPLETED 行直接 cancel）。
        this.procedureDomainService.startProcedureExecution(
            this.outpatientNurseStationMapper.toStartProcedureExecutionCommand(procedureId));
        if (offsetCompletedProcedureId == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "被取消的已完成执行记录主键不能为空");
        }
        // BR-PRO-005：新 CANCEL 行 refund_id 指向被抵消的已完成 cln_procedure.id。
        this.procedureDomainService.cancelProcedureRecord(
            this.outpatientNurseStationMapper.toCancelProcedureRecordCommand(
                procedureId, statusReasonText, offsetCompletedProcedureId, operatorId));
    }

    /**
     * 校验并加载可被取消的已完成执行记录（前端传入的 {@code procedureId} 须为净剩 COMPLETED 行）。
     *
     * @param completedProcedureId 待抵消的已完成执行记录主键
     * @param encounterId          本批就诊锚点
     * @return 可取消的已完成行模型
     * @throws BusinessException 记录不存在、状态非法、就诊不一致或已被对冲
     */
    private ProcedureRecordQueryModel mustResolveCancellableCompletedProcedure(
        Long completedProcedureId,
        Long encounterId) {
        if (completedProcedureId == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "procedureId 不能为空");
        }
        ProcedureDetailQueryModel detail = this.procedureQueryService.loadProcedureById(completedProcedureId);
        if (detail == null || detail.getProcedure() == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_NOT_FOUND, "执行记录不存在");
        }
        ProcedureRecordQueryModel row = detail.getProcedure();
        if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "仅已完成的执行记录可取消");
        }
        if (row.getEncounterId() == null || !row.getEncounterId().equals(encounterId)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行记录不属于当前就诊");
        }
        if (!this.isNurseStationProcedureRequestTable(row.getRequestTable())) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "该执行记录不属于诊疗/药品登记链路");
        }
        if (this.isProcedureAlreadyOffsetByCancel(row.getRequestId(), row.getRequestTable(), completedProcedureId)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "该执行记录已被取消对冲");
        }
        return row;
    }

    /**
     * 判断某条已完成执行记录是否已被同请求下的 CANCEL 行对冲。
     *
     * @param requestId            来源请求主键
     * @param requestTable         来源表持久化名
     * @param completedProcedureId 已完成执行记录主键
     * @return 已被对冲时返回 true
     */
    private boolean isProcedureAlreadyOffsetByCancel(Long requestId, String requestTable, Long completedProcedureId) {
        List<ProcedureRecordQueryModel> siblings = this.procedureQueryService.getProcedureRecords(requestId, requestTable);
        for (ProcedureRecordQueryModel sibling : siblings) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断来源表是否属于护士站诊疗/药品执行登记。
     *
     * @param requestTable 来源表持久化名
     * @return 是否为诊疗或药品请求表
     */
    private boolean isNurseStationProcedureRequestTable(String requestTable) {
        if (StringUtils.isBlank(requestTable)) {
            return false;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            return true;
        }
        return false;
    }

    /**
     * 净剩执行记录列表排序：记录时间倒序，同时间按主键倒序。
     *
     * @return 比较器
     */
    private Comparator<ProcedureRecordQueryModel> netRemainingProcedureRecordComparator() {
        return (left, right) -> {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            LocalDateTime leftTime = left.getRecordedTime();
            LocalDateTime rightTime = right.getRecordedTime();
            if (leftTime != null && rightTime != null) {
                int timeCmp = rightTime.compareTo(leftTime);
                if (timeCmp != 0) {
                    return timeCmp;
                }
            }
            if (leftTime == null && rightTime != null) {
                return 1;
            }
            if (leftTime != null && rightTime == null) {
                return -1;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return 1;
            }
            if (rightId == null) {
                return -1;
            }
            return rightId.compareTo(leftId);
        };
    }

    /**
     * 去重并保持勾选顺序的 procedureId 列表（同批禁止重复抵消同一条 COMPLETED）。
     *
     * @param procedureIds 原始勾选列表
     * @return 去重后的主键列表
     * @throws BusinessException 同批重复勾选同一 procedureId
     */
    private List<Long> normalizeDistinctProcedureIdList(List<Long> procedureIds) {
        if (procedureIds == null || procedureIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long procedureId : procedureIds) {
            if (procedureId == null) {
                continue;
            }
            if (distinct.contains(procedureId)) {
                throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "同批不能重复勾选同一执行记录");
            }
            distinct.add(procedureId);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 按库存项主键合并增减数量（同一库存项多发放行汇总）。
     *
     * @param inventoryIdToQty 库存项主键 → 累计数量
     * @param inventoryItemId  库存项主键
     * @param delta              本次增加或扣减量
     */
    private void mergeInventoryQuantityByItemId(
        Map<Long, BigDecimal> inventoryIdToQty,
        Long inventoryItemId,
        BigDecimal delta) {
        if (inventoryItemId == null || delta == null || delta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal merged = inventoryIdToQty.get(inventoryItemId);
        if (merged == null) {
            inventoryIdToQty.put(inventoryItemId, delta);
        } else {
            inventoryIdToQty.put(inventoryItemId, merged.add(delta));
        }
    }

    /**
     * 解析耗材发放执行人：入口可覆盖为指定护士，否则回落操作人。
     *
     * @param operatorId              操作人主键
     * @param performerPractitionerId 覆盖执行人；允许 null
     * @return 实际用于发放/执行确认的执行人主键
     */
    private Long resolvePerformerId(Long operatorId, Long performerPractitionerId) {
        if (performerPractitionerId != null) {
            return performerPractitionerId;
        }
        return operatorId;
    }

    /**
     * 将可空的主键列表规范为非 null 引用；未勾选时按空列表迭代。
     *
     * @param ids 请求体中的 id 列表；允许 null
     * @return 非 null 列表引用
     */
    private List<Long> normalizeOptionalIdList(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    /**
     * 解析耗材发放行用于库存变动的数量：优先实发量，否则回落计划量。
     *
     * @param dispense 发放行；允许 null
     * @return 回补/扣减用量；可能为 null
     */
    private BigDecimal resolveDeviceDispenseRestoreQuantity(DeviceDispenseModel dispense) {
        if (dispense == null) {
            return null;
        }
        if (dispense.getDispenseQuantity() != null) {
            return dispense.getDispenseQuantity();
        }
        return dispense.getQuantity();
    }

    /**
     * 判断耗材发放行是否允许取消（已发放或部分发放）。
     *
     * @param statusEnum 发放状态持久化码
     * @return 是否可取消
     */
    private boolean isDeviceDispenseCancellable(String statusEnum) {
        if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)) {
            return true;
        }
        if (DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * IF-NUR-04 Gate：批量处置请求入参组合校验。
     *
     * @param request 入参
     * @throws BusinessException 未勾选任何执行项或缺少操作人/就诊锚点
     */
    private void validateBatchPerformDispositionGate(BatchPerformDispositionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        boolean any = false;
        if (request.getServiceRequestIds() != null && !request.getServiceRequestIds().isEmpty()) {
            any = true;
        }
        if (request.getMedicationRequestIds() != null && !request.getMedicationRequestIds().isEmpty()) {
            any = true;
        }
        if (request.getDeviceDispenseIds() != null && !request.getDeviceDispenseIds().isEmpty()) {
            any = true;
        }
        if (!any) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "请先勾选待执行项目或耗材");
        }
    }

    /**
     * 基于已计费门控（PLANNED∪SETTLED）过滤请求主键列表。
     *
     * @param requestIds  勾选主键
     * @param serviceTable 请求表持久化名
     * @param chargeItems 就诊收费明细
     * @return 仍保留的请求主键列表
     */
    private List<Long> retainBillableRequestIds(List<Long> requestIds, String serviceTable, List<ChargeItem> chargeItems) {
        List<Long> retained = new ArrayList<>();
        if (requestIds == null || requestIds.isEmpty()) {
            return retained;
        }
        Set<Long> billable = new HashSet<>();
        if (chargeItems != null) {
            for (ChargeItem item : chargeItems) {
                if (item.getServiceId() == null || StringUtils.isBlank(item.getServiceTable())) {
                    continue;
                }
                if (!serviceTable.equals(item.getServiceTable())) {
                    continue;
                }
                if (ChargeItemStatus.PLANNED.getCode().equals(item.getStatusEnum())
                    || ChargeItemStatus.SETTLED.getCode().equals(item.getStatusEnum())) {
                    billable.add(item.getServiceId());
                }
            }
        }
        for (Long id : requestIds) {
            if (id == null) {
                continue;
            }
            if (billable.contains(id)) {
                retained.add(id);
            }
        }
        return retained;
    }

    /**
     * 计算某请求下执行记录净次数（完成 − 取消）。
     *
     * @param requestId    请求主键
     * @param requestTable 请求表持久化名
     * @return 净次数
     */
    private int netProcedureCount(Long requestId, String requestTable) {
        List<ProcedureRecordQueryModel> records = this.procedureQueryService.getProcedureRecords(requestId, requestTable);
        int completed = 0;
        int cancelCount = 0;
        for (ProcedureRecordQueryModel row : records) {
            if (EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                completed++;
            }
            if (EventStatus.isCancelExecutionStatusCode(row.getStatusEnum())) {
                cancelCount++;
            }
        }
        int net = completed - cancelCount;
        return net;
    }

    /**
     * 从诊疗列表行解析计划执行次数（仅 {@code execution_count}）。
     *
     * @param listRow 列表行；允许 null
     * @return 计划次数
     */
    private int resolvePlannedServicePerformCount(ServiceRequestListModel listRow) {
        return ServiceAdviceDraftCommandSupport.resolvePlannedServicePerformCount(listRow);
    }

    /**
     * 从药品列表行与详情解析计划执行次数：优先使用列表行数量取整（execute_num 未在详情模型暴露时的回落口径）。
     *
     * @param listRow 列表行；允许 null
     * @param detail  详情；允许 null
     * @return 计划次数
     */
    private int resolvePlannedMedicationPerformCount(MedicationRequestListQueryModel listRow, MedicationRequestDetailQueryModel detail) {
        if (detail != null && detail.getExecuteNum() != null) {
            return detail.getExecuteNum();
        }
        if (listRow != null && listRow.getQuantity() != null) {
            return listRow.getQuantity().intValue();
        }
        if (detail != null && detail.getQuantity() != null) {
            return detail.getQuantity().intValue();
        }
        return 1;
    }

    /**
     * 构建诊疗处置行（锚点、数量与计费聚合字段）。
     *
     * @param row            诊疗申请列表行
     * @param encounterId    就诊主键
     * @param chargeByAnchor 计费聚合索引
     * @param catalogContext 目录与类目展示预热上下文
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildServiceLine(
        ServiceRequestListModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        line.setDispenseId(null);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE);
        ActivityDefinition activityDef = null;
        if (row.getActivityId() != null && catalogContext != null) {
            activityDef = catalogContext.getActivityDefinitionById().get(row.getActivityId());
        }
        String activityBusNo = null;
        String activityName = null;
        if (activityDef != null) {
            activityBusNo = activityDef.getBusNo();
            activityName = activityDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(activityBusNo, row.getActivityId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            activityName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK));
        line.setSpecText(null);
        if (activityDef != null) {
            line.setPyStr(activityDef.getPyStr());
            line.setWbStr(activityDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        String serviceCategoryCode = null;
        if (activityDef != null && StringUtils.isNotBlank(activityDef.getCategoryCode())) {
            serviceCategoryCode = activityDef.getCategoryCode().trim();
        }
        line.setServiceCategoryCode(serviceCategoryCode);
        if (catalogContext != null) {
            line.setServiceCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getActivityCategoryLabelByCode(), serviceCategoryCode));
        }
        line.setDeviceCategoryCode(null);
        line.setDeviceCategoryCodeText(null);
        line.setMedCategoryCode(null);
        line.setMedCategoryCodeText(null);
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        line.setPlannedPerformCount(this.resolvePlannedServicePerformCount(row));
        line.setMedicationExecuteNum(null);
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(null);
        line.setSortNumber(row.getSortNumber());
        this.applyChargeAggregation(line, TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), chargeByAnchor);
        return line;
    }

    /**
     * 构建药品处置行（锚点、数量与计费聚合字段）。
     *
     * @param row            药品申请列表行
     * @param encounterId    就诊主键
     * @param chargeByAnchor 计费聚合索引
     * @param catalogContext 目录与类目展示预热上下文
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildMedicationLine(
        MedicationRequestListQueryModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor,
        OrderRequestCatalogWarmContext catalogContext) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        line.setDispenseId(null);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION);
        MedicationDefinition medicationDef = null;
        if (row.getMedicationId() != null && catalogContext != null) {
            medicationDef = catalogContext.getMedicationDefinitionById().get(row.getMedicationId());
        }
        String medicationBusNo = null;
        String medicationName = null;
        if (medicationDef != null) {
            medicationBusNo = medicationDef.getBusNo();
            medicationName = medicationDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(medicationBusNo, row.getMedicationId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            medicationName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_MEDICATION_LINE_NAME_FALLBACK));
        line.setSpecText(OrderCatalogDisplayAssembler.resolveMedicationSpecText(medicationDef));
        if (medicationDef != null) {
            line.setPyStr(medicationDef.getPyStr());
            line.setWbStr(medicationDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        line.setServiceCategoryCode(null);
        line.setServiceCategoryCodeText(null);
        line.setDeviceCategoryCode(null);
        line.setDeviceCategoryCodeText(null);
        String medCategoryCode = null;
        if (medicationDef != null && StringUtils.isNotBlank(medicationDef.getCategoryCode())) {
            medCategoryCode = medicationDef.getCategoryCode().trim();
        }
        line.setMedCategoryCode(medCategoryCode);
        if (catalogContext != null) {
            line.setMedCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getMedicationCategoryLabelByCode(), medCategoryCode));
        }
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        line.setPlannedPerformCount(null);
        MedicationRequestDetailQueryModel medDetail = null;
        if (row.getId() != null) {
            medDetail = this.medicationRequestQueryService.loadMedicationRequestById(row.getId());
        }
        line.setMedicationExecuteNum(this.resolvePlannedMedicationPerformCount(row, medDetail));
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(row.getGroupId());
        line.setSortNumber(row.getSortNumber());
        this.applyChargeAggregation(line, TableNameConstants.MED_MEDICATION_REQUEST, row.getId(), chargeByAnchor);
        return line;
    }

    /**
     * 构建耗材处置行（锚点、发放态与计费聚合字段）。
     *
     * @param row                     耗材申请列表行
     * @param encounterId             就诊主键
     * @param chargeByAnchor          计费聚合索引
     * @param deviceDispenses         就诊下发放单行集合
     * @param deviceDispenseIdsByRequest AS-SDD IF-NUR-02 步骤 8：{@code listDeviceDispenseIdsByDeviceRequest} 结果（主发放行取 id 最小）
     * @param catalogContext             目录与类目展示预热上下文
     * @return 处置行 DTO
     */
    private NurseStationTreatmentLineDto buildDeviceLine(
        DeviceRequestListModel row,
        Long encounterId,
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor,
        List<DeviceDispenseListModel> deviceDispenses,
        Map<Long, List<Long>> deviceDispenseIdsByRequest,
        OrderRequestCatalogWarmContext catalogContext) {
        NurseStationTreatmentLineDto line = new NurseStationTreatmentLineDto();
        line.setEncounterId(encounterId);
        line.setRequestId(row.getId());
        Long primaryDispenseId = this.resolvePrimaryDispenseId(row.getId(), deviceDispenses, deviceDispenseIdsByRequest);
        line.setDispenseId(primaryDispenseId);
        line.setRequestTable(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_DEVICE);
        line.setOrderType(OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_DEVICE);
        DeviceDefinition deviceDef = null;
        if (row.getDeviceDefId() != null && catalogContext != null) {
            deviceDef = catalogContext.getDeviceDefinitionById().get(row.getDeviceDefId());
        }
        String deviceBusNo = null;
        String deviceName = null;
        if (deviceDef != null) {
            deviceBusNo = deviceDef.getBusNo();
            deviceName = deviceDef.getName();
        }
        line.setItemCode(OrderCatalogDisplayAssembler.resolveItemCatalogCode(deviceBusNo, row.getDeviceDefId()));
        line.setItemName(OrderCatalogDisplayAssembler.resolveItemDisplayName(
            deviceName,
            OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LINE_NAME_FALLBACK));
        if (StringUtils.isNotBlank(row.getDeviceSpecifications())) {
            line.setSpecText(row.getDeviceSpecifications().trim());
        } else if (deviceDef != null && StringUtils.isNotBlank(deviceDef.getTotalVolume())) {
            line.setSpecText(deviceDef.getTotalVolume().trim());
        } else {
            line.setSpecText(null);
        }
        if (deviceDef != null) {
            line.setPyStr(deviceDef.getPyStr());
            line.setWbStr(deviceDef.getWbStr());
        } else {
            line.setPyStr(null);
            line.setWbStr(null);
        }
        line.setServiceCategoryCode(null);
        line.setServiceCategoryCodeText(null);
        String deviceCategoryCode = null;
        if (deviceDef != null && StringUtils.isNotBlank(deviceDef.getCategoryCode())) {
            deviceCategoryCode = deviceDef.getCategoryCode().trim();
        }
        line.setDeviceCategoryCode(deviceCategoryCode);
        if (catalogContext != null) {
            line.setDeviceCategoryCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    catalogContext.getDeviceCategoryLabelByCode(), deviceCategoryCode));
        }
        line.setMedCategoryCode(null);
        line.setMedCategoryCodeText(null);
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        line.setPlannedPerformCount(null);
        line.setMedicationExecuteNum(null);
        line.setPerformCount(null);
        line.setCancelCount(null);
        line.setRefundPairRequestId(null);
        line.setGroupId(null);
        line.setSortNumber(row.getSortNumber());
        this.applyChargeAggregation(line, TableNameConstants.WKF_DEVICE_REQUEST, row.getId(), chargeByAnchor);
        line.setDispenseStatusEnum(this.resolveDispenseStatusForRequest(row.getId(), deviceDispenses, primaryDispenseId));
        DispenseStatus dispenseStatus = EnumUtil.fromCode(DispenseStatus.class, line.getDispenseStatusEnum());
        if (dispenseStatus != null) {
            line.setDispenseStatusEnumText(dispenseStatus.getDisplay());
        }
        return line;
    }

    /**
     * 解析某耗材医嘱对应的主发放行主键：优先 map；否则回落为同就诊同请求下 id 最小发放行。
     *
     * @param deviceRequestId            耗材请求主键
     * @param deviceDispenseIdsByRequest 医嘱→发放单 id 列表（IF-NUR-02 步骤 8）；允许 null
     * @param deviceDispenses            发放单行集合
     * @return 发放行主键；可能为 null
     */
    private Long resolvePrimaryDispenseId(
        Long deviceRequestId,
        List<DeviceDispenseListModel> deviceDispenses,
        Map<Long, List<Long>> deviceDispenseIdsByRequest) {
        if (deviceRequestId == null) {
            return null;
        }
        if (deviceDispenseIdsByRequest != null) {
            List<Long> byReq = deviceDispenseIdsByRequest.get(deviceRequestId);
            if (byReq != null && !byReq.isEmpty()) {
                return Collections.min(byReq);
            }
        }
        Long minId = null;
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (!deviceRequestId.equals(d.getDeviceReqId())) {
                    continue;
                }
                if (d.getId() == null) {
                    continue;
                }
                if (minId == null || d.getId() < minId) {
                    minId = d.getId();
                }
            }
        }
        return minId;
    }

    /**
     * 解析耗材发放状态展示用持久化码：命中主发放行则取其状态，否则为空。
     *
     * @param deviceRequestId 耗材请求主键
     * @param deviceDispenses 发放单行集合
     * @param primaryDispenseId 主发放行主键
     * @return 发放状态码；可能为 null
     */
    private String resolveDispenseStatusForRequest(
        Long deviceRequestId,
        List<DeviceDispenseListModel> deviceDispenses,
        Long primaryDispenseId) {
        if (primaryDispenseId != null && deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (primaryDispenseId.equals(d.getId())) {
                    return d.getStatusEnum();
                }
            }
        }
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (deviceRequestId != null && deviceRequestId.equals(d.getDeviceReqId())) {
                    return d.getStatusEnum();
                }
            }
        }
        return null;
    }

    /**
     * 回填处置行的计费聚合字段（首条状态与金额和）。
     *
     * @param line           处置行
     * @param serviceTable   请求表持久化名
     * @param requestId      请求主键
     * @param chargeByAnchor 聚合索引
     */
    private void applyChargeAggregation(
        NurseStationTreatmentLineDto line,
        String serviceTable,
        Long requestId,
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor) {
        if (requestId == null || chargeByAnchor == null) {
            return;
        }
        String key = AdviceChargeAnchorRollupSupport.buildChargeAnchorKey(serviceTable, requestId);
        AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation agg = chargeByAnchor.get(key);
        if (agg == null) {
            return;
        }
        String chargeStatusEnum = agg.getChargeStatusEnum();
        line.setChargeStatusEnum(chargeStatusEnum);
        line.setChargeStatusEnumText(
            AdviceChargeDisplayStatusSupport.resolveChargeStatusDisplayTextForAdviceList(chargeStatusEnum));
        line.setDisplayTotalPrice(agg.getSumTotalPrice());
    }

    /**
     * 从计费聚合索引读取请求锚点上的收费汇总态码。
     */
    private String resolveAggregatedChargeStatus(
        String serviceTable,
        Long requestId,
        Map<String, AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation> chargeByAnchor) {
        if (requestId == null || chargeByAnchor == null) {
            return null;
        }
        String key = AdviceChargeAnchorRollupSupport.buildChargeAnchorKey(serviceTable, requestId);
        if (key == null) {
            return null;
        }
        AdviceChargeAnchorRollupSupport.RequestAnchorChargeAggregation agg = chargeByAnchor.get(key);
        if (agg == null) {
            return null;
        }
        return agg.getChargeStatusEnum();
    }

    /**
     * 回填列表「状态」列：收费退费态优先，否则执行进度（待执行/已完成）。
     */
    private void finalizeNurseTreatmentLineDisplay(NurseStationTreatmentLineDto line) {
        String executionText = this.resolveNurseTreatmentExecutionDisplayText(line);
        ListDisplayStatus display = AdviceChargeDisplayStatusSupport.resolveNurseTreatmentLineDisplayStatus(
            line.getChargeStatusEnum(), executionText);
        line.setDisplayStatusEnum(display.displayStatusEnum());
        line.setDisplayStatusEnumText(display.displayStatusEnumText());
    }

    /**
     * 按请求类型与执行次数解析执行态展示文案（不含收费态）。
     */
    private String resolveNurseTreatmentExecutionDisplayText(NurseStationTreatmentLineDto line) {
        if (line == null) {
            return "待执行";
        }
        String requestTable = line.getRequestTable();
        if (OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_SERVICE.equals(requestTable)) {
            int planned = 1;
            if (line.getPlannedPerformCount() != null) {
                planned = line.getPlannedPerformCount().intValue();
            }
            int performed = 0;
            if (line.getPerformCount() != null) {
                performed = line.getPerformCount().intValue();
            }
            int cancelled = 0;
            if (line.getCancelCount() != null) {
                cancelled = line.getCancelCount().intValue();
            }
            int net = performed - cancelled;
            if (net >= planned) {
                return "已完成";
            }
            return "待执行";
        }
        if (OutpatientNurseStationConstants.REQUEST_TABLE_VIEW_MEDICATION.equals(requestTable)) {
            int planned = 1;
            if (line.getMedicationExecuteNum() != null) {
                planned = line.getMedicationExecuteNum().intValue();
            }
            int performed = 0;
            if (line.getPerformCount() != null) {
                performed = line.getPerformCount().intValue();
            }
            int cancelled = 0;
            if (line.getCancelCount() != null) {
                cancelled = line.getCancelCount().intValue();
            }
            int net = performed - cancelled;
            if (planned > 0 && net >= planned) {
                return "已完成";
            }
            return "待执行";
        }
        return "待执行";
    }

    /**
     * 耗材取消前库存回补快照（须在发放回退前采集批号/库位/数量）。
     * <p>仅用于 IF-NUR-05 耗材回退链路，不对外暴露。</p>
     */
    private static final class DeviceCancelRestoreSnapshot {
        private final Long deviceReqId;
        private final Long locationId;
        private final Long deviceDefId;
        private final String lotNumber;
        private final BigDecimal restoreQty;

        private DeviceCancelRestoreSnapshot(
            Long deviceReqId,
            Long locationId,
            Long deviceDefId,
            String lotNumber,
            BigDecimal restoreQty) {
            this.deviceReqId = deviceReqId;
            this.locationId = locationId;
            this.deviceDefId = deviceDefId;
            this.lotNumber = lotNumber;
            this.restoreQty = restoreQty;
        }

        public Long getDeviceReqId() {
            return deviceReqId;
        }

        public Long getLocationId() {
            return locationId;
        }

        public Long getDeviceDefId() {
            return deviceDefId;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public BigDecimal getRestoreQty() {
            return restoreQty;
        }
    }

}
