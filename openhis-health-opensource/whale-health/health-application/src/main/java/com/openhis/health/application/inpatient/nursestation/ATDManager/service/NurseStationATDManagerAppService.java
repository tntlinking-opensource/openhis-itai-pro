package com.openhis.health.application.inpatient.nursestation.ATDManager.service;

import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardLocationBlockDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardOccupantDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.mapper.NurseStationATDManagerMapper;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingExecutionItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ClearDischargedBedRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteBedOperationRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteInpatientDischargeRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListBedBoardRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListDischargePendingExecutionsRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListDischargePendingMedicationsRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ClearDischargedBedResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ExecuteBedOperationResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ExecuteInpatientDischargeResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListBedBoardResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListDischargePendingExecutionsResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListDischargePendingMedicationsResponse;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.AssignInpatientBedCommand;
import com.openhis.health.domain.administration.encounter.command.ClearDischargedBedCommand;
import com.openhis.health.domain.administration.encounter.command.ConfirmBedTransferCommand;
import com.openhis.health.domain.administration.encounter.command.ExecuteInpatientNurseDischargeCommand;
import com.openhis.health.domain.administration.encounter.command.SwapInpatientBedsCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.command.OccupyBedCommand;
import com.openhis.health.domain.administration.location.command.ReleaseBedCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站入出转管理应用服务（AS-IP-NUR-002，IF-IP-NUR-ATD-01～06）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：床位列表范围解析、出 Tab 占床过滤、床态与就诊状态门禁。</li>
 *   <li>Cmd：分床/换床/互换、办理出院、清床领域写 + 床位资源 occupy/release 编排。</li>
 *   <li>Asm：床位图、出院前待办列表与占床患者展示，与 001 列表同源装配器。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationATDManager/{methodName}}；不另写 RestController。</p>
 *
 * <p>写路径在 {@link #executeBedOperation}、{@link #executeInpatientDischarge}、
 * {@link #clearDischargedBed} 上声明 {@code @Transactional}（AS-SDD §5.2）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationATDManagerAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 当前登录者管辖病区解析（BR-IP-NUR-003）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 位置读侧：床位主数据与目标床预检。 */
    private final LocationQueryService locationQueryService;

    /** 位置写侧：占床/释床运营态。 */
    private final LocationDomainService locationDomainService;

    /** 就诊读侧：IMP 门禁、活动床/病房解析与住院上下文。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊写侧：分床/转床/互换轨迹。 */
    private final EncounterDomainService encounterDomainService;

    /** 登记账户读侧（可选展示余额，BR-IP-ATD-007）。 */
    private final AccountQueryService accountQueryService;

    /** 收费明细读侧（待结算合计 → 展示余额）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** ATD Request/Command/DTO 映射。 */
    private final NurseStationATDManagerMapper nurseStationATDManagerMapper;

    /** 占床患者展示回填（与 001 列表同源）。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 药品请求读侧（出院前待办/门禁）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 诊疗请求读侧（出院前待办/门禁）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 药品发放读侧（待发退药/出院门禁）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 执行记录读侧（临时医嘱完结判定）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 药品定义读侧（待办展示名）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 诊疗定义读侧（待办展示名）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * IF-IP-NUR-ATD-01：床位列表查询（位置块 + 占床患者块）。
     *
     * @param request 病区/病房范围，与 001 树一致
     * @return 范围内全部启用床位
     * @throws BusinessException 位置或读模型批量失败时抛出 EX-IP-ATD-101
     */
    public ListBedBoardResponse listBedBoard(ListBedBoardRequest request) {
        try {
            String panel = null;
            if (request != null) {
                panel = request.getPanel();
            }
            // Gate：panel 须为 admission/discharge（BR-IP-ATD-010）。
            String resolvedPanel = this.requireValidBedBoardPanel(panel);
            Long wardId = null;
            Long houseId = null;
            if (request != null) {
                wardId = request.getWardId();
                houseId = request.getHouseId();
            }
            // Pre：按请求范围收集床位（AS-SDD §3.2；领域仅 listBedsUnderParent，病区/默认须经病房 parent 展开）。
            List<Location> beds = new ArrayList<>();
            if (houseId != null) {
                // Pre：仅选病房时，直接拉该病房下全部床。
                for (Location bed : locationQueryService.listBedsUnderParent(houseId)) {
                    if (bed != null) {
                        beds.add(bed);
                    }
                }
            } else if (wardId != null) {
                // Pre：选病区时，遍历该病区内各病房再汇总其下床位。
                for (Location room : locationQueryService.listRoomsUnderWard(wardId)) {
                    if (room == null || room.getId() == null) {
                        continue;
                    }
                    for (Location bed : locationQueryService.listBedsUnderParent(room.getId())) {
                        if (bed != null) {
                            beds.add(bed);
                        }
                    }
                }
            } else {
                // Pre：未选树节点时，管理病区并集（空则全院启用病区，BR-IP-NUR-003）。
                List<Location> wards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
                for (Location ward : wards) {
                    if (ward == null || ward.getId() == null) {
                        continue;
                    }
                    for (Location room : locationQueryService.listRoomsUnderWard(ward.getId())) {
                        if (room == null || room.getId() == null) {
                            continue;
                        }
                        for (Location bed : locationQueryService.listBedsUnderParent(room.getId())) {
                            if (bed != null) {
                                beds.add(bed);
                            }
                        }
                    }
                }
            }
            // Pre：固定段位 busNo 字典序排序，保证按病房分组且床序稳定。
            beds.sort(Comparator.comparing(Location::getBusNo, Comparator.nullsLast(Comparator.naturalOrder())));
            if (beds.isEmpty()) {
                return nurseStationATDManagerMapper.toListBedBoardResponse(Collections.emptyList());
            }
            Set<Long> houseIds = new LinkedHashSet<>();
            for (Location bed : beds) {
                if (bed != null && bed.getParentId() != null) {
                    houseIds.add(bed.getParentId());
                }
            }
            // Pre：批量解析病房展示名（houseId 来自 bed.parentId，非第二份列表查询）。
            Map<Long, String> houseNameById = locationQueryService.mapNamesByIds(houseIds);
            List<Long> bedIds = new ArrayList<>();
            for (Location bed : beds) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
            // Pre：批量反查活动床位级 adm_encounter_location（BR-IP-ATD-009；一床一患反查）。
            Map<Long, Long> encounterIdByBedId =
                encounterQueryService.mapActiveBedEncounterIdByLocationIds(bedIds);
            Map<Long, Location> bedById = new HashMap<>();
            for (Location bed : beds) {
                if (bed != null && bed.getId() != null) {
                    bedById.put(bed.getId(), bed);
                }
            }
            // Pre：仅 IN_USE 床位的占床就诊进入批量读模型，空床不得仅凭活动位置行加载患者块（BR-IP-ATD-001）。
            Set<Long> occupiedEncounterIds = new LinkedHashSet<>();
            for (Map.Entry<Long, Long> bedEncounterEntry : encounterIdByBedId.entrySet()) {
                Long bedId = bedEncounterEntry.getKey();
                Long encounterId = bedEncounterEntry.getValue();
                if (bedId == null || encounterId == null) {
                    continue;
                }
                Location mappedBed = bedById.get(bedId);
                if (mappedBed != null && this.isLocationBedInUse(mappedBed)) {
                    occupiedEncounterIds.add(encounterId);
                }
            }
            Map<Long, InpatientEncounterContextQueryModel> contextByEncounterId = new HashMap<>();
            if (!occupiedEncounterIds.isEmpty()) {
                InpatientEncounterPageQuery inpatientQuery = new InpatientEncounterPageQuery();
                inpatientQuery.setEncounterIdsIn(occupiedEncounterIds);
                List<InpatientEncounterContextQueryModel> contextRows =
                    encounterContextFacade.listInpatientEncounterContexts(inpatientQuery);
                for (InpatientEncounterContextQueryModel row : contextRows) {
                    if (row != null && row.getEncounterId() != null) {
                        contextByEncounterId.put(row.getEncounterId(), row);
                    }
                }
            }
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(occupiedEncounterIds);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> overlayByEncounterId =
                inpatientInfoQueueDisplayAssembler.buildOverlay(
                    new ArrayList<>(contextByEncounterId.values()));
            Map<Long, String> priorityByEncounterId =
                encounterQueryService.mapPriorityEnumByEncounterIds(occupiedEncounterIds);
            Map<Long, BigDecimal> chargedOccupancyByEncounterId =
                chargeItemQueryService.mapInpatientChargedOccupancyTotalPriceByEncounterIds(occupiedEncounterIds);
            // Asm：逐床合并位置块与占床块；空床/占床以 location.operational_enum 为准（BR-IP-ATD-001）。
            List<BedBoardItemDto> items = new ArrayList<>();
            for (Location bed : beds) {
                if (bed == null || bed.getId() == null) {
                    continue;
                }
                String houseName = null;
                if (bed.getParentId() != null) {
                    houseName = houseNameById.get(bed.getParentId());
                }
                BedBoardLocationBlockDto locationBlock =
                    nurseStationATDManagerMapper.toBedBoardLocationBlock(bed, houseName);
                BedBoardOccupantDto occupant = null;
                Long encounterId = encounterIdByBedId.get(bed.getId());
                if (this.isLocationBedInUse(bed)) {
                    // Asm：IN_USE 床才允许挂载占床患者块；反查 encounter 仅作一床一患组装输入。
                    if (encounterId == null) {
                        // BR-IP-ATD-009：床态占床但无活动位置时告警，仍返回位置块且 occupant 为空。
                        log.warn(
                            "床位 operational=IN_USE 但无活动 adm_encounter_location，bedId={}",
                            bed.getId());
                    } else {
                        InpatientEncounterContextQueryModel row = contextByEncounterId.get(encounterId);
                        if (row != null) {
                            Account account = accountByEncounterId.get(encounterId);
                            BigDecimal chargedOccupancy = BigDecimal.ZERO;
                            BigDecimal occupancyTotal = chargedOccupancyByEncounterId.get(encounterId);
                            if (occupancyTotal != null) {
                                chargedOccupancy = occupancyTotal;
                            }
                            BigDecimal displayBalance =
                                InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                                    account, chargedOccupancy);
                            boolean lowBalance = false;
                            if (displayBalance != null
                                && displayBalance.compareTo(
                                    InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD) < 0) {
                                lowBalance = true;
                            }
                            InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay =
                                overlayByEncounterId.get(encounterId);
                            String priorityEnum = priorityByEncounterId.get(encounterId);
                            occupant = nurseStationATDManagerMapper.toBedBoardOccupantDto(
                                row, overlay, displayBalance, lowBalance, priorityEnum);
                            // Asm：出 Tab 仅展示待出院/待结算/已结算占床患者（BR-IP-ATD-010）。
                            if (!this.isOccupantVisibleForPanel(resolvedPanel, row)) {
                                occupant = null;
                            }
                        }
                    }
                } else if (encounterId != null) {
                    // BR-IP-ATD-001/009：空床以 operational 为准；活动位置残留须告警且 occupant 强制为空。
                    log.warn(
                        "床位 operational=NOT_IN_USE 但仍有活动 adm_encounter_location，bedId={} encounterId={}",
                        bed.getId(),
                        encounterId);
                }
                BedBoardItemDto item = new BedBoardItemDto();
                item.setLocation(locationBlock);
                item.setOccupant(occupant);
                items.add(item);
            }
            // Asm：床位行顺序已与 beds 一致，直接封装响应。
            return nurseStationATDManagerMapper.toListBedBoardResponse(items);
        } catch (BusinessException ex) {
            // 业务异常原样上抛，保留 EX-IP-ATD-101 等契约码。
            throw ex;
        } catch (RuntimeException ex) {
            // 读侧批量失败统一包装，避免栈信息直出前端。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_101,
                "床位加载失败");
        }
    }

    /**
     * IF-IP-NUR-ATD-02：分床/换床/互换提交。
     *
     * @param request 写操作类型与目标床/对手就诊
     * @return 提交结果
     * @throws BusinessException 床态或状态不允许时 EX-IP-ATD-201；持久化失败时 EX-IP-ATD-202
     */
    @Transactional
    public ExecuteBedOperationResponse executeBedOperation(ExecuteBedOperationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        // Gate：写路径须已登录操作人（B.6.1）。
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_ATD_202,
            "分床失败，请联系管理员");
        String operationType = request.getOperationType();
        if (StringUtils.isBlank(operationType)) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "参数错误");
        }
        String trimmedType = operationType.trim();
        try {
            // Gate：写操作类型白名单（AS-SDD §3.3）。
            if (InpatientNurseStationConstants.BED_OPERATION_ASSIGN.equals(trimmedType)) {
                // Cmd 分支 A：首次分床（REGISTERED→IN_PROGRESS + occupyBed）。
                this.executeAssignBed(request, operatorId);
            } else if (InpatientNurseStationConstants.BED_OPERATION_TRANSFER.equals(trimmedType)) {
                // Cmd 分支 B：换空床（confirmBedTransfer + release + occupy）。
                this.executeTransferBed(request, operatorId);
            } else if (InpatientNurseStationConstants.BED_OPERATION_SWAP.equals(trimmedType)) {
                // Cmd 分支 C：互换床（swap + 双方 release/occupy 对调）。
                this.executeSwapBeds(request, operatorId);
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "参数错误");
            }
            // Asm：回显操作类型与成功标记。
            return nurseStationATDManagerMapper.toExecuteBedOperationResponse(request);
        } catch (BusinessException ex) {
            // 床态/状态类业务拒绝（EX-IP-ATD-201）原样上抛，便于前端展示可选床提示。
            if (InpatientNurseStationConstants.EX_IP_ATD_201.equals(ex.getErrorCode())) {
                throw ex;
            }
            // 其它业务失败统一映射为 EX-IP-ATD-202，与 AS 持久化失败口径一致。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_202,
                "分床失败，请联系管理员");
        } catch (RuntimeException ex) {
            // 非预期运行时异常同样收敛为 EX-IP-ATD-202。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_202,
                "分床失败，请联系管理员");
        }
    }

    /**
     * 首次分床：REGISTERED → IN_PROGRESS，并占用目标空床。
     *
     * @param request    写请求
     * @param operatorId 操作人
     * @throws BusinessException 预检失败时 EX-IP-ATD-201；领域失败时向上抛出
     */
    private void executeAssignBed(ExecuteBedOperationRequest request, Long operatorId) {
        if (request.getHouseLocationId() == null || request.getBedLocationId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        // Pre：住院类就诊门禁。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        // Pre：仅待入科允许首次分床（BR-IP-ATD-002）。
        if (!EncounterStatus.REGISTERED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        // Pre：目标床须为空（EX-IP-ATD-201）。
        this.assertTargetBedVacant(request.getBedLocationId(), request.getHouseLocationId());
        // Cmd：领域分床（HOUSE+BED 轨迹 + 参与者 + 状态迁移）。
        AssignInpatientBedCommand assignCommand =
            nurseStationATDManagerMapper.toAssignInpatientBedCommand(request, operatorId);
        encounterDomainService.assignInpatientBed(assignCommand);
        // Cmd：目标床标记 IN_USE（须在分床成功后）。
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toOccupyBedCommand(
            request.getBedLocationId(), request.getEncounterId(), operatorId);
        locationDomainService.occupyBed(occupyCommand);
    }

    /**
     * 换空床：保持 IN_PROGRESS，迁移 HOUSE+BED 并切换床态。
     *
     * @param request    写请求
     * @param operatorId 操作人
     * @throws BusinessException 预检失败时 EX-IP-ATD-201；领域失败时向上抛出
     */
    private void executeTransferBed(ExecuteBedOperationRequest request, Long operatorId) {
        if (request.getHouseLocationId() == null || request.getBedLocationId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        // Pre：仅已入科允许换床。
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        Long fromBedId = encounterQueryService.requireActiveLocationId(
            request.getEncounterId(),
            LocationForm.BED.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换",
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        this.assertTargetBedVacant(request.getBedLocationId(), request.getHouseLocationId());
        // Cmd：领域确认换床轨迹（释放旧 HOUSE+BED、写入新 HOUSE+BED）。
        ConfirmBedTransferCommand transferCommand =
            nurseStationATDManagerMapper.toConfirmBedTransferCommand(request, fromBedId, operatorId);
        encounterDomainService.confirmBedTransfer(transferCommand);
        // Cmd：释放原床运营态，避免双占。
        ReleaseBedCommand releaseCommand =
            nurseStationATDManagerMapper.toReleaseBedCommand(fromBedId, request.getEncounterId(), operatorId);
        locationDomainService.releaseBed(releaseCommand);
        // Cmd：占用目标空床，与分床 occupy 语义一致。
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toOccupyBedCommand(
            request.getBedLocationId(), request.getEncounterId(), operatorId);
        locationDomainService.occupyBed(occupyCommand);
    }

    /**
     * 互换床：双方 IN_PROGRESS，交叉迁移 HOUSE+BED 并对调床态。
     *
     * @param request    写请求
     * @param operatorId 操作人
     * @throws BusinessException 预检失败时 EX-IP-ATD-201；领域失败时向上抛出
     */
    private void executeSwapBeds(ExecuteBedOperationRequest request, Long operatorId) {
        if (request.getEncounterIdB() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        if (request.getEncounterId().equals(request.getEncounterIdB())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        EncounterDetailQueryModel encounterA = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        EncounterDetailQueryModel encounterB = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterIdB(),
            EncounterClass.IMP.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounterA.getStatusEnum())
            || !EncounterStatus.IN_PROGRESS.getCode().equals(encounterB.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        Long bedIdA = encounterQueryService.requireActiveLocationId(
            request.getEncounterId(),
            LocationForm.BED.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换",
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        Long bedIdB = encounterQueryService.requireActiveLocationId(
            request.getEncounterIdB(),
            LocationForm.BED.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换",
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        this.assertBedInUse(bedIdA);
        this.assertBedInUse(bedIdB);
        Long toHouseIdA = encounterQueryService.requireActiveLocationId(
            request.getEncounterIdB(),
            LocationForm.HOUSE.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换",
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        Long toHouseIdB = encounterQueryService.requireActiveLocationId(
            request.getEncounterId(),
            LocationForm.HOUSE.getCode(),
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换",
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
        SwapInpatientBedsCommand swapCommand = nurseStationATDManagerMapper.toSwapInpatientBedsCommand(
            request, toHouseIdA, toHouseIdB, operatorId);
        encounterDomainService.swapInpatientBeds(swapCommand);
        // Cmd：双方原床批量 releaseBed（AS-SDD §3.3.3 序号 9，A.9.1 单次落库）。
        ReleaseBedCommand releaseCommand = nurseStationATDManagerMapper.toSwapReleaseBedCommand(
            operatorId, bedIdA, request.getEncounterId(), bedIdB, request.getEncounterIdB());
        locationDomainService.releaseBed(releaseCommand);
        // Cmd：A 占 bedB、B 占 bedA（序号 10）。
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toSwapOccupyBedCommand(
            operatorId, bedIdB, request.getEncounterId(), bedIdA, request.getEncounterIdB());
        locationDomainService.occupyBed(occupyCommand);
    }

    /**
     * 目标床须为 NOT_IN_USE，且父级病房与请求一致。
     *
     * @param bedLocationId   目标床 id
     * @param houseLocationId 目标病房 id
     * @throws BusinessException 床不存在、已占或病房不匹配时 EX-IP-ATD-201
     */
    private void assertTargetBedVacant(Long bedLocationId, Long houseLocationId) {
        Location bed = locationQueryService.findOptionalById(bedLocationId).orElse(null);
        if (bed == null) {
            // 目标床不存在，拒绝分床/换床以免写入脏位置。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        if (!LocationOperational.NOT_IN_USE.getCode().equals(bed.getOperationalEnum())) {
            // 目标床已被占用，护士须另选空床或走互换流程。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        if (houseLocationId != null && bed.getParentId() != null
            && !houseLocationId.equals(bed.getParentId())) {
            // 请求病房与床位实际父级不一致，防止跨病房误占。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
    }

    /**
     * 床位须为 IN_USE（互换语义）。
     *
     * @param bedLocationId 床位 id
     * @throws BusinessException 床不存在或未占时 EX-IP-ATD-201
     */
    private void assertBedInUse(Long bedLocationId) {
        Location bed = locationQueryService.findOptionalById(bedLocationId).orElse(null);
        if (bed == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        if (!LocationOperational.IN_USE.getCode().equals(bed.getOperationalEnum())) {
            // 互换要求双方床均为 IN_USE，空床不能参与互换。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
    }

    /**
     * IF-IP-NUR-ATD-03：出院前待执行医嘱列表。
     *
     * @param request 就诊主键
     * @return 阻塞出院的医嘱行
     * @throws BusinessException 出 Tab 不可见或加载失败时 EX-IP-ATD-103
     */
    public ListDischargePendingExecutionsResponse listDischargePendingExecutions(
        ListDischargePendingExecutionsRequest request) {
        try {
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "就诊标识不能为空");
            }
            Long encounterId = request.getEncounterId();
            // Pre：上下文须存在；不存在或不在出 Tab 范围则拒绝（AS-SDD §3.4 步骤 2 → EX-IP-ATD-103）。
            this.mustGetInpatientContextForDischargeTab(
                encounterId,
                InpatientNurseStationConstants.EX_IP_ATD_103,
                "待执行医嘱加载失败");
            List<MedicationRequestListQueryModel> medicationRequests =
                medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
            List<ServiceRequestListModel> serviceRequests =
                serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
            List<Procedure> procedures =
                procedureQueryService.listByEncounterIds(Collections.singleton(encounterId));
            Map<String, List<Procedure>> proceduresByRequestKey =
                this.buildProceduresByRequestKey(procedures);
            Set<Long> medicationIds = new LinkedHashSet<>();
            Set<Long> activityIds = new LinkedHashSet<>();
            for (MedicationRequestListQueryModel row : medicationRequests) {
                if (row != null && row.getMedicationId() != null) {
                    medicationIds.add(row.getMedicationId());
                }
            }
            for (ServiceRequestListModel row : serviceRequests) {
                if (row != null && row.getActivityId() != null) {
                    activityIds.add(row.getActivityId());
                }
            }
            Map<Long, String> medicationNameById = this.mapMedicationNamesByIds(medicationIds);
            Map<Long, String> activityNameById = this.mapActivityNamesByIds(activityIds);
            List<DischargePendingExecutionItemDto> items = new ArrayList<>();
            for (MedicationRequestListQueryModel row : medicationRequests) {
                if (row == null || !this.isDischargePendingListCandidateMedication(row)) {
                    continue;
                }
                String blockingReason = this.resolveDischargeListBlockingReason(
                    row.getTherapyEnum(),
                    row.getStatusEnum(),
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    row.getId(),
                    proceduresByRequestKey);
                if (blockingReason == null) {
                    continue;
                }
                String medicationName = medicationNameById.get(row.getMedicationId());
                String doseText = this.formatDoseText(row.getDose(), row.getDoseUnitCode(), row.getQuantity(), row.getUnitCode());
                DischargePendingExecutionItemDto item =
                    nurseStationATDManagerMapper.toDischargePendingExecutionItemFromMedication(
                        row, medicationName, doseText, blockingReason);
                if (item != null) {
                    items.add(item);
                }
            }
            for (ServiceRequestListModel row : serviceRequests) {
                if (row == null || !this.isDischargePendingListCandidateService(row)) {
                    continue;
                }
                String blockingReason = this.resolveDischargeListBlockingReason(
                    row.getTherapyEnum(),
                    row.getStatusEnum(),
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    row.getId(),
                    proceduresByRequestKey);
                if (blockingReason == null) {
                    continue;
                }
                String activityName = activityNameById.get(row.getActivityId());
                String doseText = this.formatDoseText(null, null, row.getQuantity(), row.getUnitCode());
                DischargePendingExecutionItemDto item =
                    nurseStationATDManagerMapper.toDischargePendingExecutionItemFromService(
                        row, activityName, doseText, blockingReason);
                if (item != null) {
                    items.add(item);
                }
            }
            return nurseStationATDManagerMapper.toListDischargePendingExecutionsResponse(items);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_103,
                "待执行医嘱加载失败");
        }
    }

    /**
     * IF-IP-NUR-ATD-04：出院前待发/待退药列表。
     *
     * @param request 就诊主键
     * @return 待发/待退药行
     * @throws BusinessException 出 Tab 不可见或加载失败时 EX-IP-ATD-104
     */
    public ListDischargePendingMedicationsResponse listDischargePendingMedications(
        ListDischargePendingMedicationsRequest request) {
        try {
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "就诊标识不能为空");
            }
            Long encounterId = request.getEncounterId();
            // Pre：出 Tab 可见性门禁（AS-SDD §3.5 步骤 2 → EX-IP-ATD-104）。
            this.mustGetInpatientContextForDischargeTab(
                encounterId,
                InpatientNurseStationConstants.EX_IP_ATD_104,
                "待发药品加载失败");
            List<MedicationDispenseListQueryModel> dispenseRows =
                medicationDispenseQueryService.listMedicationDispensesByEncounter(encounterId);
            Set<Long> medicationIds = new LinkedHashSet<>();
            Set<Long> locationIds = new LinkedHashSet<>();
            List<MedicationDispenseListQueryModel> pendingRows = new ArrayList<>();
            for (MedicationDispenseListQueryModel row : dispenseRows) {
                if (row == null || row.getStatusEnum() == null) {
                    continue;
                }
                // Pre：仅保留 PREPARATION/SUMMARIZED/PENDING_REFUND（BR-IP-ATD-015）。
                if (!this.isPendingDischargeDispenseStatus(row.getStatusEnum().getCode())) {
                    continue;
                }
                pendingRows.add(row);
                if (row.getMedicationId() != null) {
                    medicationIds.add(row.getMedicationId());
                }
                if (row.getLocationId() != null) {
                    locationIds.add(row.getLocationId());
                }
            }
            Map<Long, String> medicationNameById = this.mapMedicationNamesByIds(medicationIds);
            Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);
            List<DischargePendingMedicationItemDto> items = new ArrayList<>();
            for (MedicationDispenseListQueryModel row : pendingRows) {
                String medicationName = medicationNameById.get(row.getMedicationId());
                String locationName = locationNameById.get(row.getLocationId());
                String statusText = this.resolveDispenseStatusDisplay(row.getStatusEnum());
                DischargePendingMedicationItemDto item = nurseStationATDManagerMapper.toDischargePendingMedicationItemDto(
                    row, medicationName, locationName, statusText);
                if (item != null) {
                    items.add(item);
                }
            }
            return nurseStationATDManagerMapper.toListDischargePendingMedicationsResponse(items);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_104,
                "待发药品加载失败");
        }
    }

    /**
     * IF-IP-NUR-ATD-05：办理出院（待出院 → 待结算）。
     *
     * @param request 就诊主键
     * @return 办理结果
     * @throws BusinessException 门禁失败 EX-IP-ATD-301～305；持久化失败 EX-IP-ATD-306
     */
    @Transactional
    public ExecuteInpatientDischargeResponse executeInpatientDischarge(ExecuteInpatientDischargeRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        // Gate：写路径须已登录操作人。
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_ATD_306,
            "出院失败，请刷新后重试");
        try {
            // Pre 步骤 3：住院上下文 + 出 Tab 可见（不可见映射 EX-IP-ATD-304）。
            InpatientEncounterContextQueryModel inpatientContext = this.mustGetInpatientContextForDischargeTab(
                encounterId,
                InpatientNurseStationConstants.EX_IP_ATD_304,
                "当前状态不允许办理出院，请刷新");
            // Pre 步骤 5：住院类就诊。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientNurseStationConstants.EX_IP_ATD_304,
                "当前状态不允许办理出院，请刷新");
            String statusCode = encounter.getStatusEnum();
            // Pre 步骤 8：已 PENDING 幂等成功，跳过后续写库（须在 hold ready 之前）。
            if (EncounterStatus.PENDING.getCode().equals(statusCode)) {
                return nurseStationATDManagerMapper.toExecuteInpatientDischargeResponse(encounterId);
            }
            // Pre 步骤 4：须待出院 ON_HOLD 分流（BR-IP-ATD-011④ → EX-IP-ATD-305）。
            this.assertDischargeHoldReady(inpatientContext);
            // Pre 步骤 6：出院门禁（长期/临时/待发退药，BR-IP-ATD-011①～③）。
            this.assertDischargeGatePassed(encounterId);
            // Pre 步骤 7：出院时刻优先取已校对出院医嘱计划时间。
            LocalDateTime dischargedAt = this.resolveDischargedAt(encounterId);
            // Pre 步骤 9：非 ON_HOLD 且非 PENDING 拒绝办理。
            if (!EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_304,
                    "当前状态不允许办理出院，请刷新");
            }
            // Cmd 步骤 10：领域办理出院 ON_HOLD→PENDING。
            ExecuteInpatientNurseDischargeCommand command =
                nurseStationATDManagerMapper.toExecuteInpatientNurseDischargeCommand(
                    request, operatorId, dischargedAt);
            encounterDomainService.executeInpatientNurseDischarge(command);
            return nurseStationATDManagerMapper.toExecuteInpatientDischargeResponse(encounterId);
        } catch (BusinessException ex) {
            if (InpatientNurseStationConstants.EX_IP_ATD_301.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_302.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_303.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_304.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_305.equals(ex.getErrorCode())) {
                throw ex;
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_306,
                "出院失败，请刷新后重试");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_306,
                "出院失败，请刷新后重试");
        }
    }

    /**
     * IF-IP-NUR-ATD-06：清床离院（已结算 → 离院并释床）。
     *
     * @param request 就诊主键
     * @return 清床结果
     * @throws BusinessException 非 COMPLETED 时 EX-IP-ATD-401；展示余额非零 EX-IP-ATD-403；床态异常 EX-IP-ATD-402
     */
    @Transactional
    public ClearDischargedBedResponse clearDischargedBed(ClearDischargedBedRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_ATD_402,
            "清床失败，请检查病床状态");
        try {
            // Pre 步骤 3：住院类就诊。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态");
            // Pre 步骤 4：仅 COMPLETED 可清床（BR-IP-ATD-013 → EX-IP-ATD-401）。
            if (!EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_401,
                    "请等待出院结算完成后再清床");
            }
            // Pre 步骤 4b：展示账户余额须为零（BR-IP-ATD-016 → EX-IP-ATD-403）。
            Account account = accountQueryService.getRegistrationAccount(
                encounter.getPatientId(),
                encounterId);
            BigDecimal chargedOccupancy =
                chargeItemQueryService.sumInpatientChargedOccupancyTotalPriceByEncounterId(encounterId);
            BigDecimal displayBalance = InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                account,
                chargedOccupancy);
            if (displayBalance.compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_403,
                    "患者账户余额须为零后才能清床");
            }
            // Pre 步骤 5：解析唯一活动床。
            Long bedId = encounterQueryService.requireActiveLocationId(
                encounterId,
                LocationForm.BED.getCode(),
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态",
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态");
            LocalDateTime clearedAt = LocalDateTime.now();
            // Cmd 步骤 6：收口轨迹并离院。
            ClearDischargedBedCommand clearCommand = nurseStationATDManagerMapper.toClearDischargedBedCommand(
                request, operatorId, bedId, clearedAt);
            encounterDomainService.clearDischargedBed(clearCommand);
            // Cmd 步骤 7：释放床位运营态。
            ReleaseBedCommand releaseCommand =
                nurseStationATDManagerMapper.toReleaseBedCommand(bedId, encounterId, operatorId);
            locationDomainService.releaseBed(releaseCommand);
            return nurseStationATDManagerMapper.toClearDischargedBedResponse(encounterId);
        } catch (BusinessException ex) {
            if (InpatientNurseStationConstants.EX_IP_ATD_401.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_403.equals(ex.getErrorCode())) {
                throw ex;
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态");
        }
    }

    /**
     * 校验床列表面板 code 合法并返回 trim 后值。
     *
     * @param panel 面板 code
     * @return 合法 panel
     * @throws BusinessException 非法 panel
     */
    private String requireValidBedBoardPanel(String panel) {
        if (StringUtils.isBlank(panel)) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "参数错误");
        }
        String trimmed = panel.trim();
        if (InpatientNurseStationConstants.ATD_PANEL_ADMISSION.equals(trimmed)) {
            return trimmed;
        }
        if (InpatientNurseStationConstants.ATD_PANEL_DISCHARGE.equals(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "参数错误");
    }

    /**
     * 判断床位 Location 是否处于占床运营态（BR-IP-ATD-001；空床判定以 {@code operational_enum} 为准）。
     *
     * @param bed 床位 Location；可 null
     * @return {@code true} 表示 {@code operational_enum=IN_USE}
     */
    private boolean isLocationBedInUse(Location bed) {
        if (bed == null) {
            return false;
        }
        return LocationOperational.IN_USE.getCode().equals(bed.getOperationalEnum());
    }

    /**
     * 按 panel 判断占床患者块是否展示（BR-IP-ATD-010）。
     *
     * @param panel 面板 code
     * @param row   住院读模型；可 null
     * @return true 表示可展示占床块
     */
    private boolean isOccupantVisibleForPanel(String panel, InpatientEncounterContextQueryModel row) {
        if (InpatientNurseStationConstants.ATD_PANEL_ADMISSION.equals(panel)) {
            return true;
        }
        if (InpatientNurseStationConstants.ATD_PANEL_DISCHARGE.equals(panel)) {
            return this.isDischargeTabVisible(row);
        }
        return false;
    }

    /**
     * 出 Tab 可见患者判定（与 AS §1.3、DM-ADM-002 §2.3.2 一致）。
     *
     * @param row 住院读模型
     * @return true 表示可在出 Tab 操作
     */
    private boolean isDischargeTabVisible(InpatientEncounterContextQueryModel row) {
        if (row == null || row.getStatusEnum() == null) {
            return false;
        }
        String statusCode = row.getStatusEnum();
        if (EncounterStatus.PENDING.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.COMPLETED.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            if (row.getTransferOrgId() != null) {
                return false;
            }
            if (StringUtils.isBlank(row.getDischargeTypeEnum())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 加载住院读模型并校验出 Tab 可见（BR-IP-ATD-010）。
     *
     * @param encounterId 就诊主键
     * @param errorCode   业务错误码（与 AS-SDD 各 IF 失败表一致）
     * @param userMessage 用户提示
     * @return 读模型行
     * @throws BusinessException 投影缺失或不在出 Tab 可见范围
     */
    private InpatientEncounterContextQueryModel mustGetInpatientContextForDischargeTab(
        Long encounterId,
        String errorCode,
        String userMessage) {
        InpatientEncounterContextQueryModel row;
        try {
            row = encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
        } catch (BusinessException ex) {
            // 上下文缺失视为本场景加载失败，统一映射为 ATD 读接口错误码。
            throw new BusinessException(errorCode, userMessage);
        }
        if (!this.isDischargeTabVisible(row)) {
            throw new BusinessException(errorCode, userMessage);
        }
        return row;
    }

    /**
     * 办理出院前置：须待出院 ON_HOLD 分流（BR-IP-ATD-011④）。
     *
     * @param row 住院就诊上下文
     * @throws BusinessException EX-IP-ATD-305
     */
    private void assertDischargeHoldReady(InpatientEncounterContextQueryModel row) {
        if (row == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_305,
                "请先开立并校对出院医嘱");
        }
        if (!EncounterStatus.ON_HOLD.getCode().equals(row.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_305,
                "请先开立并校对出院医嘱");
        }
        if (row.getTransferOrgId() != null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_305,
                "请先开立并校对出院医嘱");
        }
        if (StringUtils.isBlank(row.getDischargeTypeEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_305,
                "请先开立并校对出院医嘱");
        }
    }

    /**
     * 出院门禁：长期未停嘱、临时未完结、待发退药（BR-IP-ATD-011①～③）。
     *
     * @param encounterId 就诊主键
     * @throws BusinessException EX-IP-ATD-301/302/303
     */
    private void assertDischargeGatePassed(Long encounterId) {
        List<MedicationRequestListQueryModel> medicationRequests =
            medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<Procedure> procedures =
            procedureQueryService.listByEncounterIds(Collections.singleton(encounterId));
        Map<String, List<Procedure>> proceduresByRequestKey =
            this.buildProceduresByRequestKey(procedures);
        for (MedicationRequestListQueryModel row : medicationRequests) {
            if (row == null || !this.isDischargeGateRequestCandidate(row.getStatusEnum())) {
                continue;
            }
            // 退药关联行不参与出院门禁（与 ATD-03/004 同源）。
            if (this.isRefundDerivativeMedication(row)) {
                continue;
            }
            if (this.isLongTermNotStopped(row.getTherapyEnum(), row.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_301,
                    "长期医嘱需要先停嘱");
            }
            if (this.isTemporaryNotCompleted(
                row.getTherapyEnum(),
                TableNameConstants.MED_MEDICATION_REQUEST,
                row.getId(),
                proceduresByRequestKey)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_302,
                    "存在未执行或未完成的临时医嘱，请先执行");
            }
        }
        for (ServiceRequestListModel row : serviceRequests) {
            if (row == null || !this.isDischargeGateRequestCandidate(row.getStatusEnum())) {
                continue;
            }
            if (this.isExcludedDischargeServiceCategory(row.getCategoryEnum())) {
                continue;
            }
            if (row.getParentId() != null) {
                continue;
            }
            if (this.isRefundDerivativeService(row)) {
                continue;
            }
            if (this.isLongTermNotStopped(row.getTherapyEnum(), row.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_301,
                    "长期医嘱需要先停嘱");
            }
            if (this.isTemporaryNotCompleted(
                row.getTherapyEnum(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                row.getId(),
                proceduresByRequestKey)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_302,
                    "存在未执行或未完成的临时医嘱，请先执行");
            }
        }
        List<MedicationDispenseListQueryModel> dispenseRows =
            medicationDispenseQueryService.listMedicationDispensesByEncounter(encounterId);
        for (MedicationDispenseListQueryModel row : dispenseRows) {
            if (row == null || row.getStatusEnum() == null) {
                continue;
            }
            if (this.isPendingDischargeDispenseStatus(row.getStatusEnum().getCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_303,
                    "患者有待取的药品，请先取药");
            }
        }
    }

    /**
     * 解析出院时刻：优先已校对出院医嘱计划时间，否则当前时刻。
     *
     * @param encounterId 就诊主键
     * @return 出院时间
     */
    private LocalDateTime resolveDischargedAt(Long encounterId) {
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        LocalDateTime dischargedAt = null;
        for (ServiceRequestListModel row : serviceRequests) {
            if (row == null) {
                continue;
            }
            if (!InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(row.getCategoryEnum())) {
                continue;
            }
            if (row.getOccurrenceStartTime() != null) {
                dischargedAt = row.getOccurrenceStartTime();
                break;
            }
        }
        if (dischargedAt == null) {
            dischargedAt = LocalDateTime.now();
        }
        return dischargedAt;
    }

    /**
     * ATD-03 列表候选：已校对医生开立药品，排除退药关联。
     *
     * @param row 药品请求
     * @return true 表示可参与待执行筛选
     */
    private boolean isDischargePendingListCandidateMedication(MedicationRequestListQueryModel row) {
        if (!RequestStatus.VERIFIED.getCode().equals(row.getStatusEnum())) {
            return false;
        }
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (this.isRefundDerivativeMedication(row)) {
            return false;
        }
        return true;
    }

    /**
     * ATD-03 列表候选：已校对医生开立诊疗，排除转科/出院/护理及子项、退药关联。
     *
     * @param row 诊疗请求
     * @return true 表示可参与待执行筛选
     */
    private boolean isDischargePendingListCandidateService(ServiceRequestListModel row) {
        if (!RequestStatus.VERIFIED.getCode().equals(row.getStatusEnum())) {
            return false;
        }
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (this.isExcludedDischargeServiceCategory(row.getCategoryEnum())) {
            return false;
        }
        if (row.getParentId() != null) {
            return false;
        }
        if (this.isRefundDerivativeService(row)) {
            return false;
        }
        return true;
    }

    /**
     * 出院门禁请求候选：非 STOPPED 状态（不限于 VERIFIED）。
     *
     * @param statusEnum 请求状态 code
     * @return true 表示须参与门禁扫描
     */
    private boolean isDischargeGateRequestCandidate(String statusEnum) {
        if (StringUtils.isBlank(statusEnum)) {
            return false;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return false;
        }
        return true;
    }

    /**
     * 待执行列表阻塞原因：长期未停嘱或临时未完结。
     *
     * @param therapyEnum 长临 code
     * @param statusEnum  状态 code
     * @param requestTable 来源表
     * @param requestId   请求主键
     * @param proceduresByRequestKey 按 requestTable+requestId 分组的执行记录
     * @return 阻塞原因码；无须展示时 null
     */
    private String resolveDischargeListBlockingReason(
        String therapyEnum,
        String statusEnum,
        String requestTable,
        Long requestId,
        Map<String, List<Procedure>> proceduresByRequestKey) {
        if (this.isLongTermNotStopped(therapyEnum, statusEnum)) {
            return InpatientNurseStationConstants.DISCHARGE_BLOCKING_LONG_NOT_STOPPED;
        }
        if (this.isTemporaryNotCompleted(therapyEnum, requestTable, requestId, proceduresByRequestKey)) {
            return InpatientNurseStationConstants.DISCHARGE_BLOCKING_TEMP_NOT_COMPLETED;
        }
        return null;
    }

    /**
     * 长期医嘱且尚未停嘱。
     *
     * @param therapyEnum 长临 code
     * @param statusEnum  状态 code
     * @return true 表示长期未停
     */
    private boolean isLongTermNotStopped(String therapyEnum, String statusEnum) {
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return false;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return false;
        }
        return true;
    }

    /**
     * 临时医嘱净态未完结（与 AS-IP-NUR-004 BR-IP-EXE-006/008 同源）。
     *
     * <p>仅当整单净态仍为 pending（无 Procedure、或末态为 PREPARATION/IN_PROGRESS 等非终态）时返回 true；
     * 净 COMPLETED、STOPPED（不执行）、CANCEL 或 COMPLETED 已被 CANCEL 对冲均视为已完结，不阻塞出院。</p>
     *
     * @param therapyEnum 长临 code
     * @param requestTable 来源表
     * @param requestId   请求主键
     * @param proceduresByRequestKey 按 requestTable+requestId 分组的执行记录
     * @return true 表示临时未完结
     */
    private boolean isTemporaryNotCompleted(
        String therapyEnum,
        String requestTable,
        Long requestId,
        Map<String, List<Procedure>> proceduresByRequestKey) {
        if (!TherapyType.TEMPORARY.getCode().equals(therapyEnum)) {
            return false;
        }
        if (requestId == null || StringUtils.isBlank(requestTable)) {
            return true;
        }
        String key = this.buildRequestKey(requestTable, requestId);
        List<Procedure> procedures = proceduresByRequestKey.get(key);
        if (procedures == null || procedures.isEmpty()) {
            // 临时嘱尚无执行记录 → 待执行，阻塞出院
            return true;
        }
        return this.isTemporaryProcedureNetPending(procedures);
    }

    /**
     * 临时嘱整单净态是否为 pending（BR-IP-EXE-006/008，与医嘱执行列表读侧同口径）。
     *
     * @param procedures 同一 request 下全部 Procedure
     * @return true 表示净态仍为待执行
     */
    private boolean isTemporaryProcedureNetPending(List<Procedure> procedures) {
        if (this.pickLastNetCompletedProcedure(procedures) != null) {
            // 存在未被 CANCEL 对冲的净 COMPLETED → 已执行完毕
            return false;
        }
        Procedure netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            return true;
        }
        String status = netFinal.getStatusEnum();
        if (EventStatus.STOPPED.getCode().equals(status)) {
            // 不执行（STOPPED）视为终态
            return false;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            // 取消执行（CANCEL）为终态，执行-发药-取消-退药全链路结束后不阻塞出院
            return false;
        }
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(procedures, netFinal.getId())) {
                // COMPLETED 已被 CANCEL 对冲 → 取消执行终态
                return false;
            }
            return false;
        }
        // PREPARATION/IN_PROGRESS 等非终态 → 仍待执行
        return true;
    }

    /**
     * COMPLETED 是否已被 CANCEL 对冲（BR-IP-EXE-008，与 AS-IP-NUR-004 读侧同构）。
     *
     * @param procedures 同一 request 下全部 Procedure
     * @param completedProcedureId 待判定的 COMPLETED 主键
     * @return 若存在 CANCEL.refund_id 指向该 COMPLETED 则 true
     */
    private boolean isProcedureOffsetByCancel(List<Procedure> procedures, Long completedProcedureId) {
        if (procedures == null || completedProcedureId == null) {
            return false;
        }
        for (Procedure sibling : procedures) {
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
     * 临时嘱：取 id 最大的一条 Procedure 作为整单末态（BR-IP-EXE-007 临时口径）。
     *
     * @param procedures 该 request 下全部 Procedure
     * @return 末态记录；无记录时 null
     */
    private Procedure pickNetFinalProcedureRecord(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        Procedure netFinal = null;
        for (Procedure row : procedures) {
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
    private Procedure pickLastNetCompletedProcedure(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        Procedure lastNet = null;
        for (Procedure row : procedures) {
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
     * 是否为出院门禁关注的待发/待退药状态。
     *
     * @param statusCode 发放状态 code
     * @return true 表示阻塞出院
     */
    private boolean isPendingDischargeDispenseStatus(String statusCode) {
        if (DispenseStatus.PREPARATION.getCode().equals(statusCode)) {
            return true;
        }
        if (DispenseStatus.SUMMARIZED.getCode().equals(statusCode)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.getCode().equals(statusCode)) {
            return true;
        }
        return false;
    }

    /**
     * 排除转科/出院/护理类诊疗类目。
     *
     * @param categoryEnum 诊疗类目 code
     * @return true 表示应排除
     */
    private boolean isExcludedDischargeServiceCategory(String categoryEnum) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(categoryEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 药品退药关联行识别（与 004 同源）。
     *
     * @param row 药品请求
     * @return true 表示退药派生行
     */
    private boolean isRefundDerivativeMedication(MedicationRequestListQueryModel row) {
        if (row.getBasedOnId() == null || StringUtils.isBlank(row.getBasedOnTable())) {
            return false;
        }
        String table = row.getBasedOnTable().trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)) {
            return true;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(table)) {
            return true;
        }
        return false;
    }

    /**
     * 诊疗退药/退诊疗关联行识别。
     *
     * @param row 诊疗请求
     * @return true 表示应排除
     */
    private boolean isRefundDerivativeService(ServiceRequestListModel row) {
        if (row.getBasedOnId() != null && StringUtils.isNotBlank(row.getBasedOnTable())) {
            String table = row.getBasedOnTable().trim();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)
                || TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(table)) {
                return true;
            }
        }
        if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 按 requestTable+requestId 分组全部 Procedure（出院门禁净态判定用）。
     *
     * @param procedures 执行记录列表
     * @return 请求键 → 该请求下全部 Procedure
     */
    private Map<String, List<Procedure>> buildProceduresByRequestKey(List<Procedure> procedures) {
        Map<String, List<Procedure>> grouped = new HashMap<>();
        if (procedures == null) {
            return grouped;
        }
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getRequestId() == null
                || StringUtils.isBlank(procedure.getRequestTable())) {
                continue;
            }
            String key = this.buildRequestKey(procedure.getRequestTable(), procedure.getRequestId());
            List<Procedure> bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(key, bucket);
            }
            bucket.add(procedure);
        }
        return grouped;
    }

    /**
     * 请求自然键（requestTable + requestId）。
     *
     * @param requestTable 来源表
     * @param requestId    主键
     * @return 复合键
     */
    private String buildRequestKey(String requestTable, Long requestId) {
        return requestTable.trim() + "\u0001" + requestId;
    }

    /**
     * 批量解析药品展示名。
     *
     * @param medicationIds 药品定义 id 集合
     * @return id → 名称
     */
    private Map<Long, String> mapMedicationNamesByIds(Set<Long> medicationIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()) {
            return nameById;
        }
        List<MedicationDefinition> definitions = medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                nameById.put(definition.getId(), definition.getName());
            }
        }
        return nameById;
    }

    /**
     * 批量解析诊疗展示名。
     *
     * @param activityIds 活动定义 id 集合
     * @return id → 名称
     */
    private Map<Long, String> mapActivityNamesByIds(Set<Long> activityIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return nameById;
        }
        List<ActivityDefinition> definitions = activityDefinitionQueryService.findAllByIds(activityIds);
        for (ActivityDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                nameById.put(definition.getId(), definition.getName());
            }
        }
        return nameById;
    }

    /**
     * 发放状态展示文案（B.10.8.1：经 {@link EnumUtil} 解析）。
     *
     * @param status 发放状态枚举
     * @return 展示文案；无法解析时 null
     */
    private String resolveDispenseStatusDisplay(DispenseStatus status) {
        if (status == null) {
            return null;
        }
        DispenseStatus parsed = EnumUtil.fromCode(DispenseStatus.class, status.getCode());
        if (parsed != null) {
            return parsed.getDisplay();
        }
        return status.getDisplay();
    }

    /**
     * 剂量/数量展示文本组装。
     *
     * @param dose         单次剂量
     * @param doseUnitCode 剂量单位
     * @param quantity     数量
     * @param unitCode     单位
     * @return 展示串；无内容时 null
     */
    private String formatDoseText(
        BigDecimal dose,
        String doseUnitCode,
        BigDecimal quantity,
        String unitCode) {
        if (dose != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(dose.stripTrailingZeros().toPlainString());
            if (StringUtils.isNotBlank(doseUnitCode)) {
                builder.append(' ');
                builder.append(doseUnitCode.trim());
            }
            return builder.toString();
        }
        if (quantity != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(quantity.stripTrailingZeros().toPlainString());
            if (StringUtils.isNotBlank(unitCode)) {
                builder.append(' ');
                builder.append(unitCode.trim());
            }
            return builder.toString();
        }
        return null;
    }

}
