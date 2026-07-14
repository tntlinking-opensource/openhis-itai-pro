/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.service;

import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.assembler.OutpatientReturnMedicinePatientQueueAssembler;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.mapper.OutpatientPharmacyReturnMedicineMapper;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ConfirmMedicineReturnRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.GetReturnMedicineDetailRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.LoadReturnMedicineInitRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.PageReturnPatientRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ReturnMedicineItemRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ConfirmMedicineReturnResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.LoadReturnMedicineInitResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.PageReturnPatientResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ReturnMedicineDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberDispenseTraceOrchestrator;
import com.openhis.health.domain.medication.medicationdispense.command.DeclinePendingRefundMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.RefundDispensedMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.support.PharmacyReturnEncounterQueueLoader;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.support.ReturnMedicineDetailLoader;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterReturnExtensionIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊药房退药工作站应用服务（AS-OP-PHA-002）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：分页/登录、退药行状态 PENDING_REFUND、同就诊约束。</li>
 *   <li>Pre：患者队列、退药明细与应退金额回填。</li>
 *   <li>Cmd：确认/拒绝退药、库存回补、追溯码、3506 进销存上传。</li>
 *   <li>Asm：经 {@link OutpatientPharmacyReturnMedicineMapper} 装配响应。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyReturnMedicineAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final OrganizationQueryService organizationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ReturnMedicineDetailLoader returnMedicineDetailLoader;
    private final PharmacyReturnEncounterQueueLoader pharmacyReturnEncounterQueueLoader;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final TraceNumberDispenseTraceOrchestrator traceNumberDispenseTraceOrchestrator;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final OutpatientPharmacyReturnMedicineMapper returnMedicineMapper;
    private final OutpatientReturnMedicinePatientQueueAssembler outpatientReturnMedicinePatientQueueAssembler;

    /**
     * 加载退药初始化元数据（科室与退药状态筛选项）。
     *
     * @param request 请求体（可空壳）
     * @return 初始化数据
     * @throws BusinessException {@code EX-PHA-002-101}：加载失败
     */
    public LoadReturnMedicineInitResponse loadReturnMedicineInit(LoadReturnMedicineInitRequest request) {
        try {
            if (request == null) {
                request = new LoadReturnMedicineInitRequest();
            }
            // Pre：加载科室下拉与退药状态筛选项。
            List<Organization> departments = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
            LoadReturnMedicineInitResponse resp = new LoadReturnMedicineInitResponse();
            resp.setDepartmentOptions(this.returnMedicineMapper.mapToDepartmentOptions(departments));
            resp.setRefundStatusOptions(this.returnMedicineMapper.mapToRefundStatusOptions());
            // Asm：返回初始化元数据。
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药元数据加载失败：" + ex.getMessage());
        }
    }

    /**
     * 分页查询退药患者队列（仅含药品发放事实的就诊）。
     *
     * @param request 分页与过滤条件
     * @return 分页结果
     * @throws BusinessException {@code EX-PHA-002-101}：查询失败；分页参数非法
     */
    public PageReturnPatientResponse pageReturnPatientQueue(PageReturnPatientRequest request) {
        try {
            if (request == null) {
                request = new PageReturnPatientRequest();
            }
            // Gate：分页参数须合法。
            this.validatePageReturnPatientRequest(request);
            // Pre：按管辖药房与筛选条件分页就诊 ID。
            PageReturnPatientQuery query = this.returnMedicineMapper.toPageReturnPatientQuery(request);
            List<Long> pharmacyLocationIds = this.managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                query.setLocationIds(pharmacyLocationIds);
            }
            Page<Long> encounterIdPage =
                this.pharmacyReturnEncounterQueueLoader.pageEncounterIdsForOutpatientPharmacyReturn(query);
            List<Long> encounterIds = encounterIdPage.getContent();
            List<OutpatientEncounterContextQueryModel> infos =
                this.EncounterContextReader.listOutpatientContextsByEncounterIdsOrdered(encounterIds);
            Map<Long, String> refundCodeByEncounter =
                this.medicationDispenseQueryService.mapAggregatedRefundStatusCodeByEncounterIds(encounterIds);
            List<ReturnMedicinePatientQueueRow> rows =
                this.outpatientReturnMedicinePatientQueueAssembler.buildRows(infos, refundCodeByEncounter);
            PageReturnPatientResponse response = new PageReturnPatientResponse();
            return PagedResultBuilder.build(
                response,
                this.returnMedicineMapper.mapToReturnPatientRows(rows),
                encounterIdPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药队列查询失败：" + ex.getMessage());
        }
    }

    /**
     * 查询指定就诊的药品退药明细。
     *
     * @param request 就诊与过滤条件
     * @return 明细列表
     * @throws BusinessException {@code EX-PHA-002-101}：就诊不存在或查询失败
     */
    public ReturnMedicineDetailResponse getReturnMedicineDetail(GetReturnMedicineDetailRequest request) {
        try {
            // Gate：就诊主键须非空且就诊须存在。
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "encounterId 不能为空");
            }
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
            // Pre：加载退药明细读模型行。
            ReturnMedicineDetailQuery query = this.returnMedicineMapper.toReturnMedicineDetailQuery(request);
            List<Long> pharmacyLocationIds = this.managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                query.setLocationIds(pharmacyLocationIds);
            }
            List<ReturnMedicineDetailReadRow> rows = this.returnMedicineDetailLoader.listReturnMedicineDetails(query);
            // Pre：按原药嘱 service_id 汇总应退金额。
            this.applyChargeTotalPriceByOriginRequestId(request.getEncounterId(), rows);
            // Asm：映射明细列表并返回。
            ReturnMedicineDetailResponse resp = new ReturnMedicineDetailResponse();
            resp.setItems(this.returnMedicineMapper.mapToReturnMedicineDetailRows(rows));
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药明细查询失败：" + ex.getMessage());
        }
    }

    /**
     * 确认退药或拒绝退药（仅药品发放单）。
     *
     * @param request 操作类型、原因与退药项列表
     * @return 成功响应
     * @throws BusinessException 参数非法、状态不允许、库存定位失败等
     */
    public ConfirmMedicineReturnResponse confirmMedicineReturn(ConfirmMedicineReturnRequest request) {
        // Gate：请求体、confirmType 与退药行须合法。
        this.validateConfirmMedicineReturnRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED,
            "用户未登录");
        Long practitionerId = operatorId;
        boolean confirm =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_CONFIRM.equalsIgnoreCase(
                request.getConfirmType().trim());
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        // Gate：confirmType 只能是 CONFIRM 或 DECLINE。
        if (!confirm && !decline) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "confirmType 只能是 CONFIRM 或 DECLINE");
        }
        List<RefundDispensedMedicationCommand.Item> medRefundItems = new ArrayList<>();
        List<DeclinePendingRefundMedicationDispenseCommand.Item> medDeclineItems = new ArrayList<>();
        LinkedHashSet<Long> medRequestIds = new LinkedHashSet<>();
        List<MedicationDispense> confirmedDispenses = new ArrayList<>();
        Map<Long, BigDecimal> inventoryIncreaseMerged = new HashMap<>();
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        List<Long> confirmedDispenseIds = new ArrayList<>();
        Long encounterIdForUpload = null;

        for (ReturnMedicineItemRequest item : request.getItems()) {
            if (item == null) {
                continue;
            }
            String table = StringUtils.defaultString(item.getTableName()).trim();
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "本页仅支持药品退药");
            }
            MedicationDispense md = this.medicationDispenseQueryService.mustGetMedicationDispense(item.getDispenseId());
            this.assertPendingRefundDispense(md.getStatusEnum(), item.getDispenseId());
            if (encounterIdForUpload == null) {
                encounterIdForUpload = md.getEncounterId();
            } else if (!encounterIdForUpload.equals(md.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "一次退药确认仅支持同一就诊");
            }
            if (confirm) {
                RefundDispensedMedicationCommand.Item it = new RefundDispensedMedicationCommand.Item();
                it.setId(md.getId());
                it.setRefundQuantity(this.resolveRefundQuantity(md));
                it.setOperatorId(operatorId);
                it.setPractitionerId(practitionerId);
                it.setReasonText(request.getReason());
                medRefundItems.add(it);
                confirmedDispenseIds.add(md.getId());
                confirmedDispenses.add(md);
                this.collectMedicationInventory(md, inventoryIncreaseMerged, productByMedicationId);
            } else {
                DeclinePendingRefundMedicationDispenseCommand.Item it = new DeclinePendingRefundMedicationDispenseCommand.Item();
                it.setId(md.getId());
                it.setDeclineReasonText(request.getReason());
                it.setPractitionerId(practitionerId);
                medDeclineItems.add(it);
            }
            medRequestIds.add(item.getRequestId());
        }

        if (confirm) {
            if (!medRefundItems.isEmpty()) {
                RefundDispensedMedicationCommand cmd = new RefundDispensedMedicationCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medRefundItems);
                // Cmd：仅推进待退 REFUND 行；退药与原正向发药单无关联（BR-PHA-038）
                this.medicationDispenseDomainService.refundDispensedMedication(cmd);
            }
        } else {
            if (!medDeclineItems.isEmpty()) {
                DeclinePendingRefundMedicationDispenseCommand cmd = new DeclinePendingRefundMedicationDispenseCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medDeclineItems);
                this.medicationDispenseDomainService.declinePendingRefundMedicationDispense(cmd);
            }
        }

        for (Long requestId : medRequestIds) {
            if (requestId == null) {
                continue;
            }
            this.medicationRequestDomainService.completeMedicationRequestAfterRefund(requestId, operatorId);
        }

        if (confirm) {
            traceNumberDispenseTraceOrchestrator.recordReturnInLines(
                confirmedDispenses, operatorId, productByMedicationId);
            if (!inventoryIncreaseMerged.isEmpty()) {
                IncreaseQuantityCommand inc = new IncreaseQuantityCommand();
                inc.setOperatorId(operatorId);
                List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> incItems = new ArrayList<>();
                for (Map.Entry<Long, BigDecimal> e : inventoryIncreaseMerged.entrySet()) {
                    IncreaseQuantityCommand.IncreaseQuantityItemCommand it = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                    it.setId(e.getKey());
                    it.setIncreaseQty(e.getValue());
                    incItems.add(it);
                }
                inc.setItems(incItems);
                this.inventoryItemDomainService.increaseQuantities(inc);
            }
        }

        ConfirmMedicineReturnResponse resp = new ConfirmMedicineReturnResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setFailedItems(new ArrayList<>());
        if (confirm) {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE);
            String medicareUploadWarning =
                tryUploadMedicareInventoryAfterReturn(request, encounterIdForUpload, confirmedDispenseIds);
            if (StringUtils.isNotEmpty(medicareUploadWarning)) {
                resp.setMessage(medicareUploadWarning);
            }
        } else {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_DECLINE_SUCCESS_MESSAGE);
        }
        return resp;
    }

    /**
     * 退药本地事实提交后触发 3506；失败仅告警，不回滚退药。
     *
     * @return 需合并进响应的告警文案；无告警时 {@code null}
     */
    private String tryUploadMedicareInventoryAfterReturn(
        ConfirmMedicineReturnRequest request,
        Long encounterId,
        List<Long> dispenseIds
    ) {
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return null;
        }
        try {
            if (inventoryUploadExtension.isEnabled()) {
                InventoryUploadAfterReturnExtensionIn uploadCommand = new InventoryUploadAfterReturnExtensionIn()
                    .setEncounterId(encounterId)
                    .setDispenseIds(dispenseIds);
                inventoryUploadExtension.uploadAfterReturn(uploadCommand);
            }
        } catch (RuntimeException ex) {
            log.warn("医保进销存3506上传失败（本地退药已成功）", ex);
            return MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE
                + "；"
                + ErrorCodeConstants.EX_OPP_207_MEDICARE_PARTIAL_FAILURE
                + "："
                + ex.getMessage();
        }
        return null;
    }

    /**
     * IF-PHA-002-01 退药患者队列分页入参 Gate：校正 pageNo/pageSize 合法区间。
     *
     * @param request 分页请求；pageSize 缺省 10、上限 100
     * @throws BusinessException EX_PHA_002_101 pageNo/pageSize 非法
     */
    private void validatePageReturnPatientRequest(PageReturnPatientRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageNo 必须大于等于 1");
        }
        if (request.getPageSize() == null) {
            request.setPageSize(10);
        }
        if (request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageSize 非法");
        }
        if (request.getPageSize() > 100) {
            request.setPageSize(100);
        }
    }

    /**
     * IF-PHA-002-03 确认/拒绝退药写路径 Pre：校验明细非空、confirmType 及拒绝原因。
     *
     * @param request 退药确认请求
     * @throws BusinessException EX_PHA_002_301/302 请求或拒绝原因不合法
     */
    private void validateConfirmMedicineReturnRequest(ConfirmMedicineReturnRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请求不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请选择要退药的药品");
        }
        if (StringUtils.isEmpty(request.getConfirmType())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "confirmType 不能为空");
        }
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        if (decline && StringUtils.isEmpty(request.getReason())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_302_DECLINE_REASON_REQUIRED, "拒绝退药须填写原因");
        }
    }

    /**
     * 退药写路径 Gate：发放单须处于待退药态，已退/已拒绝/其它态均阻断。
     *
     * @param status     发放单 dispense_status 枚举
     * @param dispenseId 发放单主键（异常消息回显）
     * @throws BusinessException EX_PHA_002_201/202 状态不允许或已关闭
     */
    private void assertPendingRefundDispense(DispenseStatus status, Long dispenseId) {
        if (status == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "发放单状态缺失");
        }
        if (DispenseStatus.REFUNDED.equals(status) || DispenseStatus.DECLINED.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_202_DUPLICATE_OR_CLOSED, "该药品已退药或已拒绝，请勿重复操作");
        }
        if (!DispenseStatus.PENDING_REFUND.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED,
                "当前状态不允许退药，请刷新后重试：" + dispenseId);
        }
    }

    /**
     * 解析退药应退数量：优先 dispense_quantity，回落 quantity，取绝对值。
     *
     * @param md 发放单
     * @return 非负数量；两字段皆空时 0
     */
    private BigDecimal resolveRefundQuantity(MedicationDispense md) {
        BigDecimal q = md.getDispenseQuantity();
        if (q == null) {
            q = md.getQuantity();
        }
        if (q == null) {
            return BigDecimal.ZERO;
        }
        return q.abs();
    }

    /**
     * 按批号汇总退药回补库存：换算拆零量并合并同一 inventory_item 增量。
     *
     * @param md                         待退发放单（须含 lot_number）
     * @param inventoryIncreaseMerged    库存项 → 回补拆零量累加表（写路径就地变更）
     * @param productByMedicationId      药品定义缓存，避免重复批量加载
     * @throws BusinessException EX_PHA_002_203 定义缺失或退库数量非法
     */
    private void collectMedicationInventory(
        MedicationDispense md,
        Map<Long, BigDecimal> inventoryIncreaseMerged,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (StringUtils.isEmpty(md.getLotNumber())) {
            return;
        }
        ProductDefinitionDisplayDto product = this.loadProductForMedication(md.getMedicationId(), productByMedicationId);
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "药品定义缺失，无法换算退库数量");
        }
        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(product), md.getUnitCode(), this.resolveRefundQuantity(md));
        if (splitQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "退库数量非法，无法回补库存");
        }
        Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            md.getMedicationId(),
            md.getLocationId(),
            md.getLotNumber());
        BigDecimal existed = inventoryIncreaseMerged.get(inventoryItemId);
        if (existed == null) {
            inventoryIncreaseMerged.put(inventoryItemId, splitQty);
        } else {
            inventoryIncreaseMerged.put(inventoryItemId, existed.add(splitQty));
        }
    }

    /**
     * 按 medicationId 加载药品定义（带进程内 Map 缓存，供退库换算复用）。
     *
     * @param medicationId            药品定义主键
     * @param productByMedicationId   调用方维护的缓存
     * @return 目录展示 DTO；id 为空或未命中时 null
     */
    private ProductDefinitionDisplayDto loadProductForMedication(
        Long medicationId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (medicationId == null) {
            return null;
        }
        ProductDefinitionDisplayDto cached = productByMedicationId.get(medicationId);
        if (cached != null) {
            return cached;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationId);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        ProductDefinitionDisplayDto product = productByKey.get(key);
        if (product != null) {
            productByMedicationId.put(medicationId, product);
        }
        return product;
    }

    /**
     * 为退药明细行回填总价：按就诊 + 原药嘱 {@code service_id} 批量查收费明细并汇总。
     *
     * @param encounterId 就诊主键
     * @param rows        读侧明细行；无匹配收费时保持原值（默认 0）
     */
    private void applyChargeTotalPriceByOriginRequestId(Long encounterId, List<ReturnMedicineDetailReadRow> rows) {
        if (encounterId == null || rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> originRequestIds = new LinkedHashSet<>();
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getRefundOriginRequestId() == null) {
                continue;
            }
            originRequestIds.add(row.getRefundOriginRequestId());
        }
        if (originRequestIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setServiceIds(originRequestIds);
        List<ChargeItem> chargeRows = this.chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
        Map<Long, BigDecimal> totalPriceByServiceId = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getServiceId() == null) {
                continue;
            }
            if (!encounterId.equals(chargeRow.getEncounterId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeRow.getServiceTable())) {
                continue;
            }
            if (!this.isReturnMedicinePricingChargeStatus(chargeRow.getStatusEnum())) {
                continue;
            }
            Long serviceKey = chargeRow.getServiceId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existed = totalPriceByServiceId.get(serviceKey);
                if (existed == null) {
                    totalPriceByServiceId.put(serviceKey, chargeRow.getTotalPrice());
                } else {
                    totalPriceByServiceId.put(serviceKey, existed.add(chargeRow.getTotalPrice()));
                }
            }
        }
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getRefundOriginRequestId() == null) {
                continue;
            }
            BigDecimal total = totalPriceByServiceId.get(row.getRefundOriginRequestId());
            if (total != null) {
                // 退药页展示应退金额绝对值（与应退数量口径一致）
                row.setTotalPrice(total.abs());
            }
        }
    }

    /**
     * 退药明细应退金额所采纳的收费状态（与门诊发药定价 Query 子集一致）。
     *
     * @param statusEnum 收费明细 status_enum code
     * @return true 表示可纳入应退金额汇总
     */
    private boolean isReturnMedicinePricingChargeStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.SETTLED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }
}
