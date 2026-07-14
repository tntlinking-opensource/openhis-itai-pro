/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.service;

import com.openhis.health.application.inpatient.nursestation.ATDManager.response.*;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardLocationBlockDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardOccupantDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.RecallReverseSettleOrchestrator;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignSaveContext;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignOrchestrator;
import com.openhis.health.application.inpatient.nursestation.ATDManager.mapper.NurseStationATDManagerMapper;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingExecutionItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ClearDischargedBedRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteBedOperationRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteInpatientDischargeRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListBedBoardRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListDischargePendingExecutionsRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ListDischargePendingMedicationsRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.RecallInpatientDischargeRequest;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.infrastructure.integration.booking.inpatient.InpatientBedBookingConsumeExtension;
import com.openhis.health.infrastructure.integration.booking.inpatient.InpatientBedBookingQueryExtension;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.InpatientBedBookingByBedExtensionIn;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.AssignInpatientBedCommand;
import com.openhis.health.domain.administration.encounter.command.ClearDischargedBedCommand;
import com.openhis.health.domain.administration.encounter.command.ConfirmBedTransferCommand;
import com.openhis.health.domain.administration.encounter.command.ExecuteInpatientNurseDischargeCommand;
import com.openhis.health.domain.administration.encounter.command.RecallInpatientDischargeCommand;
import com.openhis.health.domain.administration.encounter.command.SwapInpatientBedsCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.InpatientDischargeClinicalGate;
import com.openhis.health.domain.administration.location.command.OccupyBedCommand;
import com.openhis.health.domain.administration.location.command.ReleaseBedCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院护士站入出转管理应用服务（AS-IP-NUR-002，IF-IP-NUR-ATD-01～07）。
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
 * <p>写路径由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 统一管理 UoW（AS-SDD §5.2）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationATDManagerAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 当前登录医务人员主键（出院嘱退回校对人）。 */

    /** 当前登录者管辖病区解析（BR-IP-NUR-003）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 位置读侧：床位主数据与目标床预检。 */
    private final LocationQueryService locationQueryService;

    /** 位置写侧：占床/释床运营态。 */
    private final LocationDomainService locationDomainService;

    /** 就诊读侧：IMP 门禁、活动床/病房解析与住院上下文。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;

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

    /** 诊疗请求读侧（出院时刻解析）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 出院召回内嵌反结（ATD-07 步骤 6，护士站应用层独享）。 */
    private final RecallReverseSettleOrchestrator recallReverseSettleOrchestrator;

    /** 出院临床门禁（ATD-03/04/05 与 FIN-003 出院全结 Pre 共用）。 */
    private final InpatientDischargeClinicalGate inpatientDischargeClinicalGate;

    /** 药品定义读侧（待办展示名）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 诊疗定义读侧（待办展示名）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 术语 code → 展示名（剂量/数量单位）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 入科入院体征写编排（ASSIGN 成功后同事务写入）。 */
    private final AdmissionVitalSignOrchestrator admissionVitalSignOrchestrator;

    /** 床位预约读 Extension（GAP-ATD-007）。 */
    private final InpatientBedBookingQueryExtension inpatientBedBookingQueryExtension;

    /** 分床消费预约 Extension（GAP-ATD-007）。 */
    private final InpatientBedBookingConsumeExtension inpatientBedBookingConsumeExtension;

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
                // Pre：按占床 encounterId 主键批量装载上下文；禁止 listInpatientEncounterContexts（会套用登记列表
                // 默认 PLANNED/REGISTERED/IN_PROGRESS 过滤，漏掉 ON_HOLD 等待出院占床，致入科 Tab 仅显示「占床」空壳）。
                List<InpatientEncounterContextQueryModel> contextRows =
                    EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(
                        new ArrayList<>(occupiedEncounterIds));
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
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(occupiedEncounterIds);
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
                            BigDecimal pending = BigDecimal.ZERO;
                            BigDecimal pendingTotal = pendingByEncounterId.get(encounterId);
                            if (pendingTotal != null) {
                                pending = pendingTotal;
                            }
                            BigDecimal displayBalance =
                                InpatientAccountBalanceDisplayAssembler.computeDisplayAccountBalance(
                                    account, pending);
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
                        } else {
                            // BR-IP-ATD-009：活动占床轨迹存在但上下文缺失时告警，仍返回位置块且 occupant 为空。
                            log.warn(
                                "床位 IN_USE 且存在活动 adm_encounter_location 但住院上下文未装载，bedId={} encounterId={}",
                                bed.getId(),
                                encounterId);
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
    public ExecuteBedOperationResponse executeBedOperation(ExecuteBedOperationRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        // Gate：写路径须已登录操作人（B.6.1）。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
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
            // 占床并发冲突：领域 LOCATION_RULE_CONFLICT 映射为 EX-IP-ATD-201。
            if (ErrorCodeConstants.LOCATION_RULE_CONFLICT.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_201,
                    "请选择空床或互换");
            }
            // 其它业务失败统一映射为 EX-IP-ATD-202，与 AS 持久化失败口径一致。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_202,
                "分床失败，请联系管理员");
        } catch (OptimisticLockingFailureException ex) {
            // 床位行乐观锁冲突（与其它写路径并发）：与占床冲突同提示，引导刷新重选。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
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
        BedAssignGate bedGate = this.resolveTargetBedAssignGate(
            encounter.getPatientId(), request.getBedLocationId(), request.getHouseLocationId());
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toOccupyBedCommand(
            request.getBedLocationId(), request.getEncounterId(), operatorId, bedGate.occupyFromReservation());
        // Cmd：先占床成功再写轨迹，避免「有轨迹无床」；占床失败则整事务回滚。
        locationDomainService.occupyBed(occupyCommand);
        if (bedGate.reservationAppointmentId() != null
            && inpatientBedBookingConsumeExtension.isEnabled()) {
            inpatientBedBookingConsumeExtension.consumeReservationForBedAssign(
                bedGate.reservationAppointmentId(), operatorId);
        }
        AssignInpatientBedCommand assignCommand =
            nurseStationATDManagerMapper.toAssignInpatientBedCommand(request, operatorId);
        encounterDomainService.assignInpatientBed(assignCommand);
        // Cmd：分床成功后按 start_time 写入入院体征（BR-IP-ATD-020～024；与分床同事务）。
        EncounterDetailQueryModel assignedEncounter = encounterQueryService.mustGetEncounter(request.getEncounterId());
        LocalDateTime recordedAt = assignedEncounter.getStartTime();
        // 业务分支：分床后须已有 start_time 作锚点；缺失则跳过体征写（禁止 fallback 当前时刻）
        if (recordedAt != null) {
            AdmissionVitalSignSaveContext vitalContext = new AdmissionVitalSignSaveContext()
                .setEncounterId(request.getEncounterId())
                .setPatientId(assignedEncounter.getPatientId())
                .setRecordedAt(recordedAt)
                .setLocationId(request.getBedLocationId())
                .setOperatorId(operatorId)
                .setRecorderId(currentPractitionerResolver.findPractitionerIdOrNull())
                .setSubmitDto(request.getAdmissionVitalSigns());
            admissionVitalSignOrchestrator.saveIfPresent(vitalContext);
        }
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
        BedAssignGate bedGate = this.resolveTargetBedAssignGate(
            encounter.getPatientId(), request.getBedLocationId(), request.getHouseLocationId());
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toOccupyBedCommand(
            request.getBedLocationId(), request.getEncounterId(), operatorId, bedGate.occupyFromReservation());
        // Cmd：先占目标空床（行锁硬门禁），再迁移轨迹，最后释原床。
        locationDomainService.occupyBed(occupyCommand);
        if (bedGate.reservationAppointmentId() != null
            && inpatientBedBookingConsumeExtension.isEnabled()) {
            inpatientBedBookingConsumeExtension.consumeReservationForBedAssign(
                bedGate.reservationAppointmentId(), operatorId);
        }
        ConfirmBedTransferCommand transferCommand =
            nurseStationATDManagerMapper.toConfirmBedTransferCommand(request, fromBedId, operatorId);
        encounterDomainService.confirmBedTransfer(transferCommand);
        ReleaseBedCommand releaseCommand =
            nurseStationATDManagerMapper.toReleaseBedCommand(fromBedId, request.getEncounterId(), operatorId);
        locationDomainService.releaseBed(releaseCommand);
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
        ReleaseBedCommand releaseCommand = nurseStationATDManagerMapper.toSwapReleaseBedCommand(
            operatorId, bedIdA, request.getEncounterId(), bedIdB, request.getEncounterIdB());
        OccupyBedCommand occupyCommand = nurseStationATDManagerMapper.toSwapOccupyBedCommand(
            operatorId, bedIdB, request.getEncounterId(), bedIdA, request.getEncounterIdB());
        // Cmd：双床排序加锁后 release + 交叉 occupy 一次落库（GAP-ATD-003，消除空窗）。
        locationDomainService.swapBedsOperational(releaseCommand, occupyCommand);
    }

    /**
     * 目标床分床/换床门禁：空床直接占；预留床须患者一致且入院日已到。
     *
     * @return 是否自预留占床及待消费预约主键
     */
    private BedAssignGate resolveTargetBedAssignGate(
        Long patientId,
        Long bedLocationId,
        Long houseLocationId) {
        Location bed = locationQueryService.findOptionalById(bedLocationId).orElse(null);
        if (bed == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        if (houseLocationId != null && bed.getParentId() != null
            && !houseLocationId.equals(bed.getParentId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_201,
                "请选择空床或互换");
        }
        String operational = bed.getOperationalEnum();
        if (operational == null
            || LocationOperational.NOT_IN_USE.getCode().equals(operational)) {
            return new BedAssignGate(false, null);
        }
        if (LocationOperational.RESERVED.getCode().equals(operational)) {
            if (!inpatientBedBookingQueryExtension.isEnabled()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_201,
                    "目标床已预留，请另选空床");
            }
            InpatientBedReservationQueryExtensionOut reservation = inpatientBedBookingQueryExtension
                .findActiveByBed(new InpatientBedBookingByBedExtensionIn().setBedLocationId(bedLocationId))
                .orElseThrow(() -> new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_207,
                    "目标床预约信息不一致，请刷新后重试"));
            if (patientId == null || !patientId.equals(reservation.getPatientId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_207,
                    "该床已为其他患者预留");
            }
            if (reservation.getIntendedAdmissionDate() != null
                && reservation.getIntendedAdmissionDate().isAfter(LocalDate.now())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_207,
                    "预约入院日未到，暂不可分床");
            }
            return new BedAssignGate(true, reservation.getAppointmentId());
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_ATD_201,
            "请选择空床或互换");
    }

    /** 分床 Gate 结果：是否由预约占床及预约 id。 */
    private record BedAssignGate(boolean occupyFromReservation, Long reservationAppointmentId) {
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
            InpatientDischargeClinicalGate.DischargePendingExecutionScan scan =
                inpatientDischargeClinicalGate.loadDischargePendingExecutionScan(encounterId);
            Set<Long> medicationIds = new LinkedHashSet<>();
            Set<Long> activityIds = new LinkedHashSet<>();
            for (MedicationRequestListQueryModel row : scan.getMedicationRequests()) {
                if (row != null && row.getMedicationId() != null) {
                    medicationIds.add(row.getMedicationId());
                }
            }
            for (ServiceRequestListModel row : scan.getServiceRequests()) {
                if (row != null && row.getActivityId() != null) {
                    activityIds.add(row.getActivityId());
                }
            }
            Map<Long, String> medicationNameById = this.mapMedicationNamesByIds(medicationIds);
            Map<Long, String> activityNameById = this.mapActivityNamesByIds(activityIds);
            Map<String, String> doseUnitLabelByCode =
                terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
            Map<String, String> unitLabelByCode =
                terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
            List<DischargePendingExecutionItemDto> items = new ArrayList<>();
            for (MedicationRequestListQueryModel row : scan.getMedicationRequests()) {
                if (row == null || !inpatientDischargeClinicalGate.isDischargePendingListCandidateMedication(row)) {
                    continue;
                }
                String blockingReason = inpatientDischargeClinicalGate.resolveDischargeListBlockingReason(
                    row.getTherapyEnum(),
                    row.getStatusEnum(),
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    row.getId(),
                    scan.getProceduresByRequestKey(),
                    row,
                    null,
                    scan.getExecutionDeadline());
                if (blockingReason == null) {
                    continue;
                }
                String medicationName = medicationNameById.get(row.getMedicationId());
                String doseText = this.formatDoseText(
                    row.getDose(),
                    row.getDoseUnitCode(),
                    row.getQuantity(),
                    row.getUnitCode(),
                    doseUnitLabelByCode,
                    unitLabelByCode);
                DischargePendingExecutionItemDto item =
                    nurseStationATDManagerMapper.toDischargePendingExecutionItemFromMedication(
                        row, medicationName, doseText, blockingReason);
                if (item != null) {
                    items.add(item);
                }
            }
            for (ServiceRequestListModel row : scan.getServiceRequests()) {
                if (row == null || !inpatientDischargeClinicalGate.isDischargePendingListCandidateService(row)) {
                    continue;
                }
                String blockingReason = inpatientDischargeClinicalGate.resolveDischargeListBlockingReason(
                    row.getTherapyEnum(),
                    row.getStatusEnum(),
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    row.getId(),
                    scan.getProceduresByRequestKey(),
                    null,
                    row,
                    scan.getExecutionDeadline());
                if (blockingReason == null) {
                    continue;
                }
                String activityName = activityNameById.get(row.getActivityId());
                String doseText = this.formatDoseText(
                    null,
                    null,
                    row.getQuantity(),
                    row.getUnitCode(),
                    doseUnitLabelByCode,
                    unitLabelByCode);
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
            List<MedicationDispenseListQueryModel> pendingRows =
                inpatientDischargeClinicalGate.listDischargePendingMedicationRows(encounterId);
            Set<Long> medicationIds = new LinkedHashSet<>();
            Set<Long> locationIds = new LinkedHashSet<>();
            for (MedicationDispenseListQueryModel row : pendingRows) {
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
    public ExecuteInpatientDischargeResponse executeInpatientDischarge(ExecuteInpatientDischargeRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        // Gate：写路径须已登录操作人。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
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
                || InpatientNurseStationConstants.EX_IP_ATD_307.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_302.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_303.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_304.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_305.equals(ex.getErrorCode())) {
                throw ex;
            }
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_304,
                    "当前状态不允许办理出院，请刷新");
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_306,
                "出院失败，请刷新后重试");
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_304,
                "当前状态不允许办理出院，请刷新");
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
    public ClearDischargedBedResponse clearDischargedBed(ClearDischargedBedRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        Long operatorId = currentPractitionerResolver.requireOperatorId(
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
            BigDecimal pendingTotal =
                chargeItemQueryService.sumPendingSettlementTotalPriceByEncounterId(encounterId);
            BigDecimal displayBalance = InpatientAccountBalanceDisplayAssembler.computeDisplayAccountBalance(
                account,
                pendingTotal);
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
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_401,
                    "请等待出院结算完成后再清床");
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_402,
                "清床失败，请检查病床状态");
        } catch (OptimisticLockingFailureException ex) {
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
     * IF-IP-NUR-ATD-07：出院召回（001 列表仅 {@code DISCHARGED} 行操作）。
     *
     * <p>同事务：内嵌 FIN-004 反结 + 就诊 {@code DISCHARGED→REGISTERED} 与活动 {@code WARD} 恢复。
     * 未清床（{@code COMPLETED}）须收银台反结（AS-IP-FIN-004），禁止本接口。</p>
     *
     * @param request 就诊主键
     * @return 召回结果（{@code targetStatusEnum=REGISTERED}、{@code requiresBedAssignment=true}）
     * @throws BusinessException EX-IP-ATD-501～504
     */
    public RecallInpatientDischargeResponse recallInpatientDischarge(RecallInpatientDischargeRequest request) {
        // Gate：encounterId 必填，防止空参误调召回写路径。
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        // Gate：写路径须已登录操作人。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_ATD_504,
            "出院召回失败，请刷新后重试");
        try {
            // Pre：住院 IMP；仅 DISCHARGED 可召回，COMPLETED 须收银台反结（BR-IP-ATD-017）。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientNurseStationConstants.EX_IP_ATD_501,
                "当前状态不允许出院召回，请刷新");
            String statusCode = encounter.getStatusEnum();
            if (EncounterStatus.COMPLETED.getCode().equals(statusCode)) {
                // Pre：未清床须收银台反结，禁止护士站召回（BR-IP-ATD-017）。
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_501,
                    "仅已清床离院患者可召回；未清床请至收银台反结");
            }
            if (!EncounterStatus.DISCHARGED.getCode().equals(statusCode)) {
                // Pre：非 DISCHARGED 终止态不允许召回。
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_501,
                    "当前状态不允许出院召回，请刷新");
            }
            // Pre：解析末次已完成病区，供 INSERT 活动 WARD（BR-IP-ATD-019）。
            Optional<Long> lastWard = encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounterId);
            if (lastWard.isEmpty()) {
                // Pre：无末次 COMPLETED WARD 无法恢复活动病区轨迹。
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_504,
                    "出院召回失败，请刷新后重试");
            }
            Long wardLocationId = lastWard.get();
            // Cmd：内嵌反结（RecallReverseSettleOrchestrator；对称 FIN-004），失败 EX-IP-ATD-502/503。
            recallReverseSettleOrchestrator.execute(
                encounterId,
                encounter.getPatientId());
            // Cmd：DISCHARGED→REGISTERED + INSERT 活动 WARD（领域 §6.1.25）。
            RecallInpatientDischargeCommand recallCommand =
                nurseStationATDManagerMapper.toRecallInpatientDischargeCommand(
                    request, operatorId, wardLocationId);
            encounterDomainService.recallInpatientDischarge(recallCommand);
            // Asm：返回 REGISTERED 态与二次分床提示（须 ATD-02 重新分床）。
            return nurseStationATDManagerMapper.toRecallInpatientDischargeResponse(
                encounterId,
                EncounterStatus.REGISTERED.getCode(),
                true);
        } catch (BusinessException ex) {
            if (InpatientNurseStationConstants.EX_IP_ATD_501.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_502.equals(ex.getErrorCode())
                || InpatientNurseStationConstants.EX_IP_ATD_503.equals(ex.getErrorCode())) {
                throw ex;
            }
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_501,
                    "当前状态不允许出院召回，请刷新");
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_504,
                "出院召回失败，请刷新后重试");
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_504,
                "出院召回失败，请刷新后重试");
        } catch (RuntimeException ex) {
            // 运行时异常收口为 EX-IP-ATD-504，同事务整单回滚。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_504,
                "出院召回失败，请刷新后重试");
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
            if (StringUtils.isNotBlank(row.getDischargeTypeEnum())) {
                return true;
            }
            // Pre：校对出院后主档已写 out_way_code 仍占床，须在出 Tab 展示（BR-IP-ATD-010）。
            if (StringUtils.isNotEmpty(row.getOutWayCode())) {
                return true;
            }
            return false;
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
            row = EncounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
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
     * 出院门禁：与 ATD-03/04 待办列表同数据源——列表为空则医嘱/发药侧可通过。
     *
     * @param encounterId 就诊主键
     * @throws BusinessException EX-IP-ATD-301/302/303/307
     */
    private void assertDischargeGatePassed(Long encounterId) {
        inpatientDischargeClinicalGate.assertDischargeClinicalGatePassed(encounterId);
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
     * 剂量/数量展示文本组装（与 {@link com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler} 同源）。
     *
     * @param dose                 单次剂量
     * @param doseUnitCode         剂量单位 code
     * @param quantity             数量
     * @param unitCode             数量单位 code
     * @param doseUnitLabelByCode  剂量单位术语映射
     * @param unitLabelByCode      计量单位术语映射
     * @return 展示串；无内容时 null
     */
    private String formatDoseText(
        BigDecimal dose,
        String doseUnitCode,
        BigDecimal quantity,
        String unitCode,
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> unitLabelByCode) {
        if (dose != null) {
            String doseText = dose.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String unit = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                doseUnitLabelByCode, doseUnitCode, null);
            if (StringUtils.isNotBlank(unit)) {
                return doseText + unit.trim();
            }
            return doseText;
        }
        if (quantity != null) {
            String qtyText = quantity.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String unit = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, unitCode, null);
            if (StringUtils.isNotBlank(unit)) {
                return qtyText + unit.trim();
            }
            return qtyText;
        }
        return null;
    }

}
