/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.service;

import com.openhis.health.application.medicaltech.feelist.dto.MedicalTechFeeItemRowDto;
import com.openhis.health.application.medicaltech.feelist.mapper.MedicalTechFeeListMapper;
import com.openhis.health.application.medicaltech.feelist.request.ConfirmMedicalTechFeesRequest;
import com.openhis.health.application.medicaltech.feelist.request.ListMedicalTechFeeItemsRequest;
import com.openhis.health.application.medicaltech.feelist.request.PrintMedicalTechFeeListRequest;
import com.openhis.health.application.medicaltech.feelist.response.ConfirmMedicalTechFeesResponse;
import com.openhis.health.application.medicaltech.feelist.response.ListMedicalTechFeeItemsResponse;
import com.openhis.health.application.medicaltech.feelist.response.PrintMedicalTechFeeListResponse;
import com.openhis.health.application.print.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.print.printjob.request.PreparePrintRequest;
import com.openhis.health.application.print.printjob.service.PrintJobAppService;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ChargeItemActivityOperatorResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.chargeitem.command.StampMedicalTechFeeConfirmationCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.MedicalTechExecutionConstants;
import com.openhis.health.domain.share.constants.MedicalTechFeeListConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医技工作站·费用清单应用服务（AS-MT-FEE-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ChargeItemQueryService}：按就诊装载费用后内存过滤本科相关行。</li>
 *   <li>{@link ChargeItemDomainService}：IF-03 行级确费盖章（fee_confirmer_*）。</li>
 *   <li>{@link ServiceRequestQueryService} / {@link DeviceRequestQueryService} /
 *       {@link DeviceDispenseQueryService} / {@link LocationQueryService}：相关判定三条件索引。</li>
 *   <li>{@link PrintJobAppService}：IF-02 触发 preparePrint（无 Charge 写）。</li>
 *   <li>{@link MedicalTechFeeListMapper}：行装配与打印入参。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/medicalTechFeeList/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicalTechFeeListAppService {

    /** 会话登录科室（须 TECH）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 当前操作人（确费人）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 组织只读：TECH 门禁与科室名展示。 */
    private final OrganizationQueryService organizationQueryService;

    /** 就诊只读：门闸与类别匹配。 */
    private final EncounterQueryService encounterQueryService;

    /** 收费只读：按就诊装载费用行。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 收费写：一行级确费盖章。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 诊疗请求只读：相关判定条件①。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 耗材请求只读：相关判定条件②。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 耗材发放只读：发放房→组织。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 位置只读：库房→组织。 */
    private final LocationQueryService locationQueryService;

    /** 产品定义展示：清单品名/编码 enrich。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 收费活动操作人解析：计费人跟 Procedure / DeviceDispense。 */
    private final ChargeItemActivityOperatorResolver chargeItemActivityOperatorResolver;

    /** 医务人员只读：确费人/计费人展示名批量解析。 */
    private final PractitionerQueryService practitionerQueryService;

    /** Request/收费 → DTO/打印入参装配。 */
    private final MedicalTechFeeListMapper medicalTechFeeListMapper;

    /** 打印运行时：preparePrint（AS-BAS-PRT）。 */
    private final PrintJobAppService printJobAppService;

    /**
     * IF-MT-FEE-01：查询登录 TECH 科相关费用清单与合计。
     *
     * @param request 就诊与 scope
     * @return 本科相关行与合计金额
     * @throws BusinessException 非 TECH / scope 非法 / 查询失败（EX-MT-FEE-101）
     */
    public ListMedicalTechFeeItemsResponse listMedicalTechFeeItems(ListMedicalTechFeeItemsRequest request) {
        try {
            // Gate：须登录 TECH 科
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechFeeListConstants.EX_MT_FEE_101);
            // Gate：入参与 scope
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_101, "encounterId 不能为空");
            }
            this.assertEncounterScope(request.getEncounterScope(), MedicalTechFeeListConstants.EX_MT_FEE_101);
            // Pre：装载过滤后的本科相关收费行
            List<ChargeItem> related = this.loadRelatedChargeItems(
                request.getEncounterId(), request.getEncounterScope(), loginTechOrgId);
            // Asm：产品名 + 确费人/计费人展示名 enrich + 合计
            return this.assembleListResponse(related);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_101, "无法加载费用清单");
        }
    }

    /**
     * IF-MT-FEE-02：打印本科相关费用清单（preparePrint）。
     *
     * @param request 就诊、scope、可选勾选 id、场景码
     * @return traceId 与成功标记
     * @throws BusinessException EX-MT-FEE-101 门禁；EX-MT-FEE-201 打印失败或勾选非法
     */
    public PrintMedicalTechFeeListResponse printMedicalTechFeeList(PrintMedicalTechFeeListRequest request) {
        try {
            // Gate：TECH + 入参
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechFeeListConstants.EX_MT_FEE_101);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_101, "encounterId 不能为空");
            }
            this.assertEncounterScope(request.getEncounterScope(), MedicalTechFeeListConstants.EX_MT_FEE_101);
            // Pre：复用 IF-01 相关过滤
            List<ChargeItem> related = this.loadRelatedChargeItems(
                request.getEncounterId(), request.getEncounterScope(), loginTechOrgId);
            // Pre：勾选子集须全部属于本科相关集（BR-MT-FEE-003）；非法 id 在交集内抛 EX-201
            this.intersectChargeItemsForPrint(related, request.getChargeItemIds());
            // Asm：构造打印 prepare 入参（PrintData 由客户端按过滤结果组装；服务端仅签发 prepare）
            PreparePrintRequest prepareRequest = this.medicalTechFeeListMapper.toPrintPrepareRequest(request);
            // Cmd：触发打印运行时（副作用在 Print 侧）
            PreparePrintResultDto prepareResult = this.printJobAppService.preparePrint(prepareRequest);
            // Asm：traceId / succeeded
            return this.medicalTechFeeListMapper.toPrintResponse(prepareResult);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_201, "打印失败请重试");
        }
    }

    /**
     * IF-MT-FEE-03：一键确费——将本科相关且尚未确费的收费行批量打上确费人/时刻。
     *
     * <p><b>仅住院</b>：门诊无确费业务（出院门禁也不读门诊确费戳）。已确费行跳过（幂等）。
     * 新产生的费用行确费人为空，须再次一键。无头表。</p>
     *
     * @param request 就诊与 scope（须为 {@code INPATIENT}）
     * @return 新确费行数与成功标记
     * @throws BusinessException EX-MT-FEE-301
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmMedicalTechFeesResponse confirmMedicalTechFees(ConfirmMedicalTechFeesRequest request) {
        try {
            // Gate：须登录 TECH 科
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechFeeListConstants.EX_MT_FEE_301);
            // Gate：入参与 scope
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_301, "encounterId 不能为空");
            }
            this.assertEncounterScope(request.getEncounterScope(), MedicalTechFeeListConstants.EX_MT_FEE_301);
            // Gate：确费仅住院；门诊无确费业务（与费用清单 UI / 出院门禁分轨一致）
            if (!MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(
                request.getEncounterScope().trim())) {
                throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_301, "门诊无确费业务");
            }
            // Pre：确费人 = 当前医务人员
            Long confirmerId = this.currentPractitionerResolver.requirePractitionerId(
                MedicalTechFeeListConstants.EX_MT_FEE_301, "未绑定医务人员，无法确费");
            // Pre：复用 IF-01 相关过滤，收集尚未确费行
            List<ChargeItem> related = this.loadRelatedChargeItems(
                request.getEncounterId(), request.getEncounterScope(), loginTechOrgId);
            List<Long> pendingIds = new ArrayList<>();
            if (related != null) {
                for (ChargeItem charge : related) {
                    // 业务：已确费跳过
                    if (charge == null || charge.getId() == null) {
                        continue;
                    }
                    if (charge.getFeeConfirmerId() != null) {
                        continue;
                    }
                    pendingIds.add(charge.getId());
                }
            }
            // Cmd：无待确费行则幂等成功
            if (pendingIds.isEmpty()) {
                ConfirmMedicalTechFeesResponse empty = new ConfirmMedicalTechFeesResponse();
                empty.setSucceeded(Boolean.TRUE);
                empty.setConfirmedCount(0);
                empty.setMessage("无待确费行");
                return empty;
            }
            // Cmd：批量写 fee_confirmer_id / fee_confirmed_at（不改收费状态）
            StampMedicalTechFeeConfirmationCommand stampCmd = new StampMedicalTechFeeConfirmationCommand();
            stampCmd.setFeeConfirmerId(confirmerId);
            stampCmd.setFeeConfirmedAt(LocalDateTime.now());
            stampCmd.setChargeItemIds(pendingIds);
            this.chargeItemDomainService.stampMedicalTechFeeConfirmation(stampCmd);
            // Asm：返回新确费行数
            ConfirmMedicalTechFeesResponse response = new ConfirmMedicalTechFeesResponse();
            response.setSucceeded(Boolean.TRUE);
            response.setConfirmedCount(pendingIds.size());
            response.setMessage("确费成功");
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechFeeListConstants.EX_MT_FEE_301, "确费失败请重试");
        }
    }

    /**
     * 装载就诊下本科相关收费行（BR-MT-FEE-002 三条件任一）。
     *
     * @param encounterId    就诊
     * @param encounterScope scope
     * @param loginTechOrgId 登录 TECH
     * @return 相关收费行
     */
    private List<ChargeItem> loadRelatedChargeItems(
        Long encounterId,
        String encounterScope,
        Long loginTechOrgId) {
        // Pre：就诊存在且类别匹配 scope
        EncounterDetailQueryModel encounter = this.encounterQueryService.mustGetEncounter(encounterId);
        this.assertEncounterMatchesScope(
            encounter.getClassEnum(), encounterScope, MedicalTechFeeListConstants.EX_MT_FEE_101);
        // Pre：装载本就诊全部费用
        ChargeItemQueryCommand chargeCmd =
            this.medicalTechFeeListMapper.toChargeItemQueryCommand(encounterId);
        List<ChargeItem> allCharges = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        if (allCharges == null || allCharges.isEmpty()) {
            return new ArrayList<>();
        }
        // Pre：批量装载 SR / DR / Dispense / Location，构建相关判定索引（禁止 N+1）
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Map<Long, ServiceRequestListModel> serviceById = new HashMap<>();
        if (serviceRequests != null) {
            for (ServiceRequestListModel row : serviceRequests) {
                if (row != null && row.getId() != null) {
                    serviceById.put(row.getId(), row);
                }
            }
        }
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(encounterId);
        Map<Long, DeviceRequestListModel> deviceById = new HashMap<>();
        if (deviceRequests != null) {
            for (DeviceRequestListModel row : deviceRequests) {
                if (row != null && row.getId() != null) {
                    deviceById.put(row.getId(), row);
                }
            }
        }
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(encounterId);
        Map<Long, Long> locationOrgByDispenseId =
            this.mapDispenseIdToLocationOrganizationId(deviceRequests, deviceDispenses);
        Map<Long, Long> locationOrgByRequestLocationId =
            this.mapRequestLocationToOrganizationId(deviceRequests);
        // Pre：内存过滤 BR-MT-FEE-002
        List<ChargeItem> related = new ArrayList<>();
        for (ChargeItem charge : allCharges) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            if (this.isRelatedToLoginTech(
                charge, loginTechOrgId, serviceById, deviceById,
                locationOrgByDispenseId, locationOrgByRequestLocationId)) {
                related.add(charge);
            }
        }
        return related;
    }

    /**
     * 判定收费行是否属登录 TECH（三条件任一）。
     *
     * @param charge                         收费行
     * @param loginTechOrgId                 登录 TECH
     * @param serviceById                    诊疗请求索引
     * @param deviceById                     耗材请求索引
     * @param locationOrgByDispenseId        发放主键 → 库房组织
     * @param locationOrgByRequestLocationId 请求库房 → 组织
     * @return 相关为 true
     */
    private boolean isRelatedToLoginTech(
        ChargeItem charge,
        Long loginTechOrgId,
        Map<Long, ServiceRequestListModel> serviceById,
        Map<Long, DeviceRequestListModel> deviceById,
        Map<Long, Long> locationOrgByDispenseId,
        Map<Long, Long> locationOrgByRequestLocationId) {
        // 条件④：医技划价来源且 org/requestingOrg 为本登录 TECH（医技划耗材主路径）
        if (GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(charge.getGenerateSourceEnum())) {
            if (loginTechOrgId.equals(charge.getRequestingOrgId())
                || loginTechOrgId.equals(charge.getOrgId())) {
                return true;
            }
        }
        // 条件①：诊疗锚点 org_id = loginTech
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(charge.getServiceTable())
            && charge.getServiceId() != null) {
            ServiceRequestListModel sr = serviceById.get(charge.getServiceId());
            if (sr != null && loginTechOrgId.equals(sr.getOrgId())) {
                return true;
            }
        }
        // 条件②：耗材发放房 / 请求库房组织 = loginTech
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(charge.getServiceTable())
            && charge.getServiceId() != null) {
            if (charge.getDispenseId() != null && locationOrgByDispenseId != null) {
                Long dispenseOrg = locationOrgByDispenseId.get(charge.getDispenseId());
                if (loginTechOrgId.equals(dispenseOrg)) {
                    return true;
                }
            }
            DeviceRequestListModel dr = deviceById.get(charge.getServiceId());
            if (dr != null && dr.getLocationId() != null && locationOrgByRequestLocationId != null) {
                Long requestLocOrg = locationOrgByRequestLocationId.get(dr.getLocationId());
                if (loginTechOrgId.equals(requestLocOrg)) {
                    return true;
                }
            }
            if (dr != null && dr.getPerformLocation() != null && locationOrgByRequestLocationId != null) {
                Long performOrg = locationOrgByRequestLocationId.get(dr.getPerformLocation());
                if (loginTechOrgId.equals(performOrg)) {
                    return true;
                }
            }
        }
        // 条件③：requesting_org_id = loginTech
        if (loginTechOrgId.equals(charge.getRequestingOrgId())) {
            return true;
        }
        return false;
    }

    /**
     * 构建发放主键 → 库房上级组织映射。
     *
     * @param deviceRequests  耗材请求
     * @param deviceDispenses 发放读模型
     * @return dispenseId → organizationId
     */
    private Map<Long, Long> mapDispenseIdToLocationOrganizationId(
        List<DeviceRequestListModel> deviceRequests,
        List<DeviceDispenseListModel> deviceDispenses) {
        Map<Long, Long> result = new HashMap<>();
        Set<Long> dispenseIds = new HashSet<>();
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (d != null && d.getId() != null) {
                    dispenseIds.add(d.getId());
                }
            }
        }
        List<DeviceDispense> entities = this.deviceDispenseQueryService.listDeviceDispensesByIds(dispenseIds);
        Set<Long> locationIds = new HashSet<>();
        Map<Long, Long> locationByDispenseId = new HashMap<>();
        if (entities != null) {
            for (DeviceDispense entity : entities) {
                if (entity == null || entity.getId() == null || entity.getLocationId() == null) {
                    continue;
                }
                locationByDispenseId.put(entity.getId(), entity.getLocationId());
                locationIds.add(entity.getLocationId());
            }
        }
        if (deviceRequests != null) {
            for (DeviceRequestListModel req : deviceRequests) {
                if (req == null) {
                    continue;
                }
                if (req.getLocationId() != null) {
                    locationIds.add(req.getLocationId());
                }
                if (req.getPerformLocation() != null) {
                    locationIds.add(req.getPerformLocation());
                }
            }
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Long> entry : locationByDispenseId.entrySet()) {
            Location location = locationById.get(entry.getValue());
            if (location != null && location.getOrganizationId() != null) {
                result.put(entry.getKey(), location.getOrganizationId());
            }
        }
        return result;
    }

    /**
     * 构建请求库房主键 → 组织映射（含 performLocation）。
     *
     * @param deviceRequests 耗材请求
     * @return locationId → organizationId
     */
    private Map<Long, Long> mapRequestLocationToOrganizationId(List<DeviceRequestListModel> deviceRequests) {
        Map<Long, Long> result = new HashMap<>();
        if (deviceRequests == null || deviceRequests.isEmpty()) {
            return result;
        }
        Set<Long> locationIds = new HashSet<>();
        for (DeviceRequestListModel req : deviceRequests) {
            if (req == null) {
                continue;
            }
            if (req.getLocationId() != null) {
                locationIds.add(req.getLocationId());
            }
            if (req.getPerformLocation() != null) {
                locationIds.add(req.getPerformLocation());
            }
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Location> entry : locationById.entrySet()) {
            Location location = entry.getValue();
            if (location != null && location.getOrganizationId() != null) {
                result.put(entry.getKey(), location.getOrganizationId());
            }
        }
        return result;
    }

    /**
     * 勾选 id 与相关集求交；空勾选返回全集。
     *
     * @param related        相关行
     * @param chargeItemIds  勾选主键；空=全集
     * @return 打印行
     */
    private List<ChargeItem> intersectChargeItemsForPrint(
        List<ChargeItem> related,
        List<Long> chargeItemIds) {
        if (related == null || related.isEmpty()) {
            return new ArrayList<>();
        }
        // 空勾选：打印全集
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return new ArrayList<>(related);
        }
        Set<Long> relatedIds = new HashSet<>();
        Map<Long, ChargeItem> byId = new LinkedHashMap<>();
        for (ChargeItem charge : related) {
            if (charge != null && charge.getId() != null) {
                relatedIds.add(charge.getId());
                byId.put(charge.getId(), charge);
            }
        }
        List<ChargeItem> selected = new ArrayList<>();
        for (Long id : chargeItemIds) {
            if (id == null) {
                continue;
            }
            // 勾选须全部属于相关集
            if (!relatedIds.contains(id)) {
                throw new BusinessException(
                    MedicalTechFeeListConstants.EX_MT_FEE_201, "勾选费用不属于本科相关清单");
            }
            ChargeItem row = byId.get(id);
            if (row != null) {
                selected.add(row);
            }
        }
        return selected;
    }

    /**
     * 装配列表响应：产品名 enrich + 确费人/计费人姓名 + MonetaryAmountSupport 合计。
     *
     * @param related 相关收费行
     * @return 列表响应
     */
    private ListMedicalTechFeeItemsResponse assembleListResponse(List<ChargeItem> related) {
        // Asm：批量产品读模型
        LinkedHashMap<String, ProductDefinitionBatchLookup> lookupByKey = new LinkedHashMap<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        Set<Long> practitionerIds = new LinkedHashSet<>();
        if (related != null) {
            for (ChargeItem charge : related) {
                if (charge == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(charge.getProductTable()) && charge.getProductId() != null) {
                    String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        charge.getProductTable().trim(), charge.getProductId());
                    if (!lookupByKey.containsKey(key)) {
                        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                        lookup.setProductTableName(charge.getProductTable().trim());
                        lookup.setProductDefinitionId(charge.getProductId());
                        lookupByKey.put(key, lookup);
                    }
                }
                if (charge.getRequestingOrgId() != null) {
                    orgIds.add(charge.getRequestingOrgId());
                }
                // 确费人主键纳入姓名批量解析
                if (charge.getFeeConfirmerId() != null) {
                    practitionerIds.add(charge.getFeeConfirmerId());
                }
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookupByKey.values()));
        Map<Long, String> orgNameById = new HashMap<>();
        Map<Long, Organization> orgById = this.organizationQueryService.mapByIds(orgIds);
        for (Map.Entry<Long, Organization> entry : orgById.entrySet()) {
            Organization org = entry.getValue();
            if (org != null && org.getName() != null) {
                orgNameById.put(entry.getKey(), org.getName());
            }
        }
        // Pre：计费人优先 Procedure / DeviceDispense；无则回落 Request.requester_id
        Map<Long, Long> billingByChargeItemId =
            this.resolveBillingPractitionerByChargeItemId(related);
        for (Long billingId : billingByChargeItemId.values()) {
            if (billingId != null) {
                practitionerIds.add(billingId);
            }
        }
        Map<Long, String> practitionerNameById =
            this.practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        List<MedicalTechFeeItemRowDto> rows =
            this.medicalTechFeeListMapper.toFeeItemRows(
                related, productByKey, orgNameById, practitionerNameById, billingByChargeItemId);
        List<BigDecimal> amounts = new ArrayList<>();
        if (rows != null) {
            for (MedicalTechFeeItemRowDto row : rows) {
                if (row == null || row.getTotalPrice() == null) {
                    continue;
                }
                amounts.add(row.getTotalPrice());
            }
        }
        BigDecimal sum = MonetaryAmountSupport.sumThenCalc(amounts);
        return this.medicalTechFeeListMapper.toListResponse(rows, sum);
    }

    /**
     * 解析费用行计费人：活动操作人优先，否则上游 Request {@code requester_id}。
     *
     * <p>{@code enterer_id} 为收费人（BR-OPC-016），禁止当作计费人。</p>
     *
     * @param related 本科相关收费行
     * @return chargeItemId → 计费人 practitionerId
     */
    private Map<Long, Long> resolveBillingPractitionerByChargeItemId(List<ChargeItem> related) {
        Map<Long, Long> billingByChargeItemId = new LinkedHashMap<>();
        if (related == null || related.isEmpty()) {
            return billingByChargeItemId;
        }
        // Pre：活动操作人（Procedure 主执行人 / DeviceDispense 发放·配药人）
        Map<Long, Long> activityOperatorByChargeItemId =
            this.chargeItemActivityOperatorResolver.mapOperatorPractitionerIdByChargeItemId(related);
        LinkedHashSet<Long> serviceRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceRequestIds = new LinkedHashSet<>();
        for (ChargeItem charge : related) {
            if (charge == null || charge.getId() == null || charge.getServiceId() == null) {
                continue;
            }
            Long fromActivity = activityOperatorByChargeItemId.get(charge.getId());
            // 已有活动操作人则直接采用
            if (fromActivity != null) {
                billingByChargeItemId.put(charge.getId(), fromActivity);
                continue;
            }
            // 无活动操作人：按锚点收集 Request 主键，稍后批量取 requester_id
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(charge.getServiceTable())) {
                serviceRequestIds.add(charge.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(charge.getServiceTable())) {
                deviceRequestIds.add(charge.getServiceId());
            }
        }
        Map<Long, Long> requesterByServiceRequestId =
            this.mapRequesterIdByServiceRequestIds(serviceRequestIds);
        Map<Long, Long> requesterByDeviceRequestId =
            this.mapRequesterIdByDeviceRequestIds(deviceRequestIds);
        for (ChargeItem charge : related) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            // 已解析过则跳过
            if (billingByChargeItemId.containsKey(charge.getId())) {
                continue;
            }
            if (charge.getServiceId() == null) {
                continue;
            }
            Long requesterId = null;
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(charge.getServiceTable())) {
                requesterId = requesterByServiceRequestId.get(charge.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(charge.getServiceTable())) {
                requesterId = requesterByDeviceRequestId.get(charge.getServiceId());
            }
            if (requesterId != null) {
                billingByChargeItemId.put(charge.getId(), requesterId);
            }
        }
        return billingByChargeItemId;
    }

    /**
     * 批量解析诊疗请求开立人（requester_id）作为计费人回落。
     *
     * @param serviceRequestIds 诊疗请求主键
     * @return serviceRequestId → requesterId
     */
    private Map<Long, Long> mapRequesterIdByServiceRequestIds(Set<Long> serviceRequestIds) {
        Map<Long, Long> result = new HashMap<>();
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return result;
        }
        Map<Long, ServiceRequestListModel> byId =
            this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        if (byId == null || byId.isEmpty()) {
            return result;
        }
        for (Map.Entry<Long, ServiceRequestListModel> entry : byId.entrySet()) {
            ServiceRequestListModel model = entry.getValue();
            if (model == null || model.getRequesterId() == null) {
                continue;
            }
            result.put(entry.getKey(), model.getRequesterId());
        }
        return result;
    }

    /**
     * 批量解析耗材请求开立人（requester_id）作为计费人回落。
     *
     * @param deviceRequestIds 耗材请求主键
     * @return deviceRequestId → requesterId
     */
    private Map<Long, Long> mapRequesterIdByDeviceRequestIds(Set<Long> deviceRequestIds) {
        Map<Long, Long> result = new HashMap<>();
        if (deviceRequestIds == null || deviceRequestIds.isEmpty()) {
            return result;
        }
        Map<Long, DeviceRequestListModel> byId =
            this.deviceRequestQueryService.mapListModelsByIds(deviceRequestIds);
        if (byId == null || byId.isEmpty()) {
            return result;
        }
        for (Map.Entry<Long, DeviceRequestListModel> entry : byId.entrySet()) {
            DeviceRequestListModel model = entry.getValue();
            if (model == null || model.getRequesterId() == null) {
                continue;
            }
            result.put(entry.getKey(), model.getRequesterId());
        }
        return result;
    }

    /**
     * 断言当前登录组织为单位且 class=TECH，返回组织主键。
     *
     * @param errorCode 失败码
     * @return 登录 TECH 科主键
     */
    private Long requireLoginTechOrgId(String errorCode) {
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            errorCode, "未登录或机构上下文缺失");
        Organization org = this.organizationQueryService.loadOrganizationByIdForDisplay(orgId);
        // 业务：非 TECH 科禁止进入医技费用清单
        if (org == null || org.getClassEnum() != OrganizationClassification.TECH) {
            throw new BusinessException(errorCode, "当前登录科室不是医技科");
        }
        return orgId;
    }

    /**
     * 断言 encounterScope 合法。
     *
     * @param encounterScope scope
     * @param errorCode      失败码
     */
    private void assertEncounterScope(String encounterScope, String errorCode) {
        // Gate：scope 必填
        if (StringUtils.isEmpty(encounterScope)) {
            throw new BusinessException(errorCode, "encounterScope 不能为空");
        }
        String scope = encounterScope.trim();
        // 门诊
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(scope)) {
            return;
        }
        // 住院
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(scope)) {
            return;
        }
        throw new BusinessException(errorCode, "encounterScope 非法");
    }

    /**
     * 断言就诊类别与 scope 一致。
     *
     * @param classEnum 就诊 class
     * @param scope     请求 scope
     * @param errorCode 失败码
     */
    private void assertEncounterMatchesScope(String classEnum, String scope, String errorCode) {
        // 门诊须 AMB
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(scope)
            && EncounterClass.AMB.getCode().equals(classEnum)) {
            return;
        }
        // 住院须 IMP
        if (MedicalTechExecutionConstants.ENCOUNTER_SCOPE_INPATIENT.equals(scope)
            && EncounterClass.IMP.getCode().equals(classEnum)) {
            return;
        }
        throw new BusinessException(errorCode, "就诊类别与 encounterScope 不一致");
    }
}
