/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.support;

import com.zxplat.plugin.peis.application.share.PeisOrderChargeDraftSupport;

import com.zxplat.plugin.peis.domain.port.HisProductCatalogPort;
import com.zxplat.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.zxplat.domain.administration.account.service.AccountDomainService;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.share.constants.ChargeItemDmConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PhysicalExamChargeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.AccountTypeCode;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检收费懒生成：为 PE 就诊补齐 PLANNED 收费明细（GAP-PE-CHG-02）。
 *
 * <p>个检与门诊不同：套餐挂一条 ChargeItem，{@code service_table/product_table}
 * 均为 {@code wkf_activity_definition}，{@code service_id/product_id} 为套餐 AD；
 * 套餐内子项 ServiceRequest 只用于执行，不单独挂费。加项仍锚 {@code wkf_service_request}。</p>
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamPlannedChargeEnsureSupport {

    private final EncounterQueryService encounterQueryService;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final HisProductCatalogPort hisProductCatalogPort;
    
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PhysicalExamGroupChargeEnsureSupport physicalExamGroupChargeEnsureSupport;

    /**
     * 单就诊幂等补齐 PLANNED 收费项。
     *
     * @param encounterId 就诊主键
     * @param operatorId  操作人
     * @return 缺价等非阻断警告文案；无警告时为空列表
     */
    public List<String> ensureForEncounter(Long encounterId, Long operatorId) {
        List<String> warnings = new ArrayList<>();
        if (encounterId == null || operatorId == null) {
            return warnings;
        }
        // Gate：须为 PE 就诊
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "非体检就诊不可收费");
        Long selfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (selfPayAccountId == null) {
            // Cmd：幂等开立个人现金账户，供后续收费项挂账
            OpenEncounterAccountsCommand openCmd = new OpenEncounterAccountsCommand();
            openCmd.setOperatorId(operatorId);
            OpenEncounterAccountsCommand.Item item = new OpenEncounterAccountsCommand.Item();
            item.setEncounterId(encounterId);
            item.setPatientId(encounter.getPatientId());
            item.setDefaultFlag(Boolean.TRUE);
            item.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
            openCmd.setItem(item);
            accountDomainService.openEncounterAccounts(openCmd);
            selfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        }
        if (selfPayAccountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING + ": 无法开立自费账户");
        }
        List<ServiceRequestListModel> serviceRequests = serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        this.signDraftServiceRequests(serviceRequests, operatorId);
        PeisEncounterExtModel peExt = peisEncounterExtQueryService.findOptionalByEncounterId(encounterId).orElse(null);
        // 团检走合同/套餐整单挂费，不按子项 SALE 展开
        if (peExt != null && Boolean.TRUE.equals(peExt.getGroupFlag())) {
            return physicalExamGroupChargeEnsureSupport.ensureForGroupEncounter(
                encounter, peExt, selfPayAccountId, operatorId, serviceRequests);
        }
        // Pre：套餐内子项不挂费；未结算的子项摊价行先作废，避免与套餐整单重复收
        Set<Long> baselineActivityIds = this.resolveBaselineActivityIds(peExt);
        boolean settledBaseline = this.hasSettledBaselineServiceRequestCharge(
            encounterId, serviceRequests, baselineActivityIds);
        // 已按旧模型结算的套餐内子项保持原账，不迁到套餐 AD
        if (!settledBaseline) {
            this.voidBaselineServiceRequestCharges(encounterId, serviceRequests, baselineActivityIds, operatorId);
        }
        Set<Long> existingServiceIds = this.loadExistingServiceRequestChargeAnchors(encounterId);
        // Cmd：仅加项按子项 SR 挂费
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            // 已作废申请不再挂费
            if (RequestStatus.CANCELLED.getCode().equals(serviceRequest.getStatusEnum())) {
                continue;
            }
            Long activityId = serviceRequest.getActivityId();
            // 套餐内子项由套餐 AD 整单覆盖，不单独挂费
            if (activityId != null && baselineActivityIds.contains(activityId)) {
                continue;
            }
            // 已挂过费的加项保持原明细
            if (existingServiceIds.contains(serviceRequest.getId())) {
                continue;
            }
            String warning = this.createPlannedChargeForServiceRequest(
                encounter, serviceRequest, selfPayAccountId, operatorId);
            // 缺价等非阻断警告收集后继续同就诊其它项
            if (StringUtils.isNotEmpty(warning)) {
                warnings.add(warning);
            } else {
                existingServiceIds.add(serviceRequest.getId());
            }
        }
        // Cmd：未按旧模型结过套餐内子项时，套餐整单锚 wkf_activity_definition
        if (!settledBaseline) {
            String packageWarning = this.ensurePersonalPackageChargeItem(
                encounter, peExt, selfPayAccountId, operatorId);
            if (StringUtils.isNotEmpty(packageWarning)) {
                warnings.add(packageWarning);
            }
        }
        return warnings;
    }

    /**
     * 批量幂等补齐 PLANNED 收费项。
     *
     * @param encounterIds 就诊主键集合
     * @param operatorId   操作人
     */
    public void ensureForEncounters(Collection<Long> encounterIds, Long operatorId) {
        if (encounterIds == null || encounterIds.isEmpty() || operatorId == null) {
            return;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinct.add(encounterId);
            }
        }
        for (Long encounterId : distinct) {
            this.ensureForEncounter(encounterId, operatorId);
        }
    }

    /**
     * 预结算/正式结算前：将收费项锚定的 DRAFT 服务申请补签为 ACTIVE。
     *
     * @param chargeItems 收费明细
     * @param operatorId  操作人
     */
    public void activateDraftServiceRequestsForChargeItems(List<ChargeItem> chargeItems, Long operatorId) {
        if (chargeItems == null || chargeItems.isEmpty() || operatorId == null) {
            return;
        }
        LinkedHashSet<Long> serviceReqIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getServiceId() == null) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                serviceReqIds.add(chargeItem.getServiceId());
            }
        }
        if (serviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, ServiceRequestListModel> byId = serviceRequestQueryService.mapListModelsByIds(serviceReqIds);
        List<ServiceRequestListModel> drafts = new ArrayList<>();
        for (Long serviceReqId : serviceReqIds) {
            ServiceRequestListModel row = byId.get(serviceReqId);
            if (row != null) {
                drafts.add(row);
            }
        }
        this.signDraftServiceRequests(drafts, operatorId);
    }

    /**
     * 补签仍为 DRAFT 的体检服务申请，满足门诊收费 Gate 对 ACTIVE 锚点的要求。
     *
     * @param serviceRequests 就诊下服务申请
     * @param operatorId      操作人
     */
    private void signDraftServiceRequests(List<ServiceRequestListModel> serviceRequests, Long operatorId) {
        if (serviceRequests == null || serviceRequests.isEmpty() || operatorId == null) {
            return;
        }
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (!RequestStatus.DRAFT.getCode().equals(serviceRequest.getStatusEnum())) {
                continue;
            }
            SignServiceRequestCommand signCmd = new SignServiceRequestCommand();
            signCmd.setOperatorId(operatorId);
            SignServiceRequestCommand.SignServiceRequestItemCommand signItem =
                new SignServiceRequestCommand.SignServiceRequestItemCommand();
            signItem.setId(serviceRequest.getId());
            signItem.setSignerId(operatorId);
            signItem.setSignTime(LocalDateTime.now());
            signCmd.setItem(signItem);
            serviceRequestDomainService.signServiceRequest(signCmd);
        }
    }

    /**
     * 加载就诊下已存在 wkf_service_request 锚点的 serviceId 集合。
     *
     * @param encounterId 就诊主键
     * @return 已有锚点 serviceId
     */
    private Set<Long> loadExistingServiceRequestChargeAnchors(Long encounterId) {
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        Set<Long> serviceIds = new HashSet<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getServiceId() == null) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())) {
                // 已退/退款中的加项不阻断重新挂待收
                if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                    continue;
                }
                if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                    continue;
                }
                serviceIds.add(row.getServiceId());
            }
        }
        return serviceIds;
    }

    /**
     * 为单个 ServiceRequest 创建 PLANNED 收费项。
     *
     * @param encounter           就诊详情
     * @param serviceRequest      服务申请
     * @param selfPayAccountId    自费账户
     * @param operatorId          操作人
     * @return 非阻断警告；成功或跳过不收费项时为 null
     */
    private String createPlannedChargeForServiceRequest(
        EncounterDetailQueryModel encounter,
        ServiceRequestListModel serviceRequest,
        Long selfPayAccountId,
        Long operatorId) {
        Long activityId = serviceRequest.getActivityId();
        // 无活动定义无法对目录挂费
        if (activityId == null) {
            return "体检服务申请缺少活动定义，已跳过挂费";
        }
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityId);
        ActivityDefinitionBaseQueryModel base = null;
        // 目录详情缺失时仅用占位名，不阻断挂费
        if (detail != null) {
            base = detail.getBaseInfo();
        }
        String activityName = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
        // 缺目录名时用收费项占位文案，避免警告空名称
        if (base != null && StringUtils.isNotEmpty(base.getName())) {
            activityName = base.getName();
        }
        // Gate：目录明确不收费（pricing_flag=false）的加项不挂费
        if (base != null && Boolean.FALSE.equals(base.getPricingFlag())) {
            return null;
        }
        // Pre：按活动定义主键取产品读模型与 SALE 定价行（与医生站诊疗挂费同口径；当前价）
        ProductDefinitionDisplayDto product;
        try {
            product = this.loadActivityProduct(activityId);
        } catch (BusinessException ex) {
            return "项目「" + activityName + "」未找到目录定义，已跳过挂费";
        }
        Long orgId = serviceRequest.getOrgId();
        // 申请未带机构时回落就诊机构，与医生站诊疗挂费一致
        if (orgId == null) {
            orgId = encounter.getOrganizationId();
        }
        ChargeItemDefinition priceLine = hisProductCatalogPort.resolveDraftChargePriceLine(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            activityId,
            product,
            orgId,
            encounter.getId());
        // 无目录 SALE → 跳过本加项，不阻断整单
        if (priceLine == null) {
            return "项目「" + activityName + "」缺少零售价定价，已跳过挂费";
        }
        BigDecimal quantity = serviceRequest.getQuantity();
        // 申请未写数量时按 1 计，避免行金额为空
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        String quantityUnit = serviceRequest.getUnitCode();
        CreateChargeItemCommand.Item draftItem = PeisOrderChargeDraftSupport.toCreateChargeItemDraftItemFromOrder(hisProductCatalogPort, 
            encounter.getId(),
            encounter.getPatientId(),
            selfPayAccountId,
            orgId,
            serviceRequest.getId(),
            TableNameConstants.WKF_SERVICE_REQUEST,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            activityId,
            priceLine,
            quantity,
            quantityUnit,
            product);
        draftItem.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(createCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounter.getId(),
            TableNameConstants.WKF_SERVICE_REQUEST,
            serviceRequest.getId());
        return null;
    }

    /**
     * 解析套餐内建单子项：优先登记快照，快照为空时回落当前套餐 AD 子项。
     *
     * @param peExt 体检就诊扩展；空则返回空集合
     * @return 套餐子项 activityId
     */
    private Set<Long> resolveBaselineActivityIds(PeisEncounterExtModel peExt) {
        Set<Long> baselineActivityIds = new HashSet<>();
        // 无体检扩展则无法区分套餐内项
        if (peExt == null) {
            return baselineActivityIds;
        }
        // 快照是建单当时的子项，加减项后仍能区分套餐内项与加项
        baselineActivityIds.addAll(this.extractSnapshotChildActivityIds(peExt.getPackageSnapshotJson()));
        // 快照已有子项，或未绑定套餐 AD，不再回落当前绑定
        if (!baselineActivityIds.isEmpty() || peExt.getPackageActivityId() == null) {
            return baselineActivityIds;
        }
        List<ActivityDefinitionChildQueryModel> children =
            activityDefinitionQueryService.listChildrenByActivityId(peExt.getPackageActivityId());
        // 无快照时用当前套餐绑定，避免套餐内子项被当加项挂费
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child != null && child.getChildrenId() != null) {
                baselineActivityIds.add(child.getChildrenId());
            }
        }
        return baselineActivityIds;
    }

    /**
     * 从套餐快照 JSON 解析建单时写入的子项 activityId（格式与登记编排快照一致）。
     *
     * @param packageSnapshotJson 快照 JSON；空则返回空列表
     * @return 子项 activityId
     */
    private List<Long> extractSnapshotChildActivityIds(String packageSnapshotJson) {
        // 未写快照则无法从登记瞬间还原子项
        if (StringUtils.isEmpty(packageSnapshotJson)) {
            return List.of();
        }
        int start = packageSnapshotJson.indexOf("\"childActivityIds\"");
        // 旧快照无该键：回落当前套餐绑定
        if (start < 0) {
            return List.of();
        }
        int bracketStart = packageSnapshotJson.indexOf('[', start);
        int bracketEnd = packageSnapshotJson.indexOf(']', bracketStart);
        // 数组残缺视为无快照子项
        if (bracketStart < 0 || bracketEnd < 0) {
            return List.of();
        }
        String body = packageSnapshotJson.substring(bracketStart + 1, bracketEnd).trim();
        if (StringUtils.isEmpty(body)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        // 快照数组为逗号分隔的 activityId（Snowflake 主键，非枚举 code）
        for (String token : body.split(",")) {
            if (StringUtils.isEmpty(token)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
                // 脏快照 token 跳过，不阻断挂费
            }
        }
        return ids;
    }

    /**
     * 作废套餐内子项上尚未结算的收费（PLANNED 回草稿后删 DRAFT），避免与套餐整单重复。
     *
     * @param encounterId         就诊主键
     * @param serviceRequests     就诊下服务申请
     * @param baselineActivityIds 套餐内 activityId
     * @param operatorId          操作人
     */
    private void voidBaselineServiceRequestCharges(
        Long encounterId,
        List<ServiceRequestListModel> serviceRequests,
        Set<Long> baselineActivityIds,
        Long operatorId) {
        if (encounterId == null || operatorId == null || serviceRequests == null
            || baselineActivityIds == null || baselineActivityIds.isEmpty()) {
            // 无套餐内子项则无需清理旧摊价行
            return;
        }
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            Long activityId = serviceRequest.getActivityId();
            // 加项收费保留
            if (activityId == null || !baselineActivityIds.contains(activityId)) {
                continue;
            }
            chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceRequest.getId());
            VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd =
                new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
            cmd.setOperatorId(operatorId);
            cmd.setEncounterId(encounterId);
            cmd.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
            cmd.setServiceId(serviceRequest.getId());
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(cmd);
        }
    }

    /**
     * 为个检套餐挂一条 ChargeItem：锚 {@code wkf_activity_definition} + 套餐 AD。
     *
     * @param encounter        就诊
     * @param peExt            体检扩展
     * @param selfPayAccountId 自费账户
     * @param operatorId       操作人
     * @return 非阻断警告；成功或已存在时为 null
     */
    private String ensurePersonalPackageChargeItem(
        EncounterDetailQueryModel encounter,
        PeisEncounterExtModel peExt,
        Long selfPayAccountId,
        Long operatorId) {
        // 未选套餐则不挂套餐整单
        if (peExt == null || peExt.getPackageActivityId() == null) {
            return null;
        }
        Long packageActivityId = peExt.getPackageActivityId();
        ChargeItem existing = this.findPersonalPackageChargeItem(encounter.getId(), packageActivityId);
        // 已有套餐整单（含已改价、已结算）不再重建
        if (existing != null) {
            // 残留草稿升为待收费，便于收费窗口直接预结算
            if (ChargeItemStatus.DRAFT.getCode().equals(existing.getStatusEnum())) {
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                    encounter.getId(),
                    TableNameConstants.WKF_ACTIVITY_DEFINITION,
                    packageActivityId);
            }
            return null;
        }
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(packageActivityId);
        ActivityDefinitionBaseQueryModel base = null;
        // 目录详情缺失时仅用占位名，不阻断挂费
        if (detail != null) {
            base = detail.getBaseInfo();
        }
        String packageName = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
        // 缺目录名时用收费项占位文案，避免警告空名称
        if (base != null && StringUtils.isNotEmpty(base.getName())) {
            packageName = base.getName();
        }
        ProductDefinitionDisplayDto product;
        try {
            product = this.loadActivityProduct(packageActivityId);
        } catch (BusinessException ex) {
            return "套餐「" + packageName + "」未找到目录定义，已跳过挂费";
        }
        Long orgId = encounter.getOrganizationId();
        ChargeItemDefinition priceLine = hisProductCatalogPort.resolveDraftChargePriceLine(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            packageActivityId,
            product,
            orgId,
            encounter.getId());
        // 套餐须有自身 SALE 定价行，不能用子项 definition 冒充
        if (priceLine == null || priceLine.getId() == null) {
            return "套餐「" + packageName + "」缺少零售价定价，已跳过挂费";
        }
        String quantityUnit = ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT;
        // 套餐有包装单位时用目录单位，否则回落「次」
        if (product != null && StringUtils.isNotEmpty(product.getPackageUnitCode())) {
            quantityUnit = product.getPackageUnitCode().trim();
        }
        CreateChargeItemCommand.Item draftItem = PeisOrderChargeDraftSupport.toCreateChargeItemDraftItemFromOrder(hisProductCatalogPort, 
            encounter.getId(),
            encounter.getPatientId(),
            selfPayAccountId,
            orgId,
            packageActivityId,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            packageActivityId,
            priceLine,
            BigDecimal.ONE,
            quantityUnit,
            product);
        draftItem.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(createCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounter.getId(),
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            packageActivityId);
        return null;
    }

    /**
     * 查找个检套餐整单收费（service/product 均锚套餐 AD）。
     *
     * @param encounterId       就诊主键
     * @param packageActivityId 套餐 AD
     * @return 命中行；无则 null
     */
    private ChargeItem findPersonalPackageChargeItem(Long encounterId, Long packageActivityId) {
        if (encounterId == null || packageActivityId == null) {
            // 缺就诊或套餐 AD 无法定位整单
            return null;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        for (ChargeItem row : rows) {
            if (row == null || row.getServiceId() == null) {
                continue;
            }
            // 已退款行不阻断重新挂套餐整单
            if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getServiceTable())
                && packageActivityId.equals(row.getServiceId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 套餐内子项是否已有已结算收费（旧模型按 SR 摊价入账）。
     *
     * @param encounterId         就诊主键
     * @param serviceRequests     就诊下服务申请
     * @param baselineActivityIds 套餐内 activityId
     * @return true 表示已按子项结过账，不可再挂套餐整单
     */
    private boolean hasSettledBaselineServiceRequestCharge(
        Long encounterId,
        List<ServiceRequestListModel> serviceRequests,
        Set<Long> baselineActivityIds) {
        if (encounterId == null || serviceRequests == null
            || baselineActivityIds == null || baselineActivityIds.isEmpty()) {
            // 无套餐内子项则不存在旧模型子项账
            return false;
        }
        Set<Long> baselineServiceRequestIds = new HashSet<>();
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            Long activityId = serviceRequest.getActivityId();
            // 加项结算不影响套餐整单迁移
            if (activityId == null || !baselineActivityIds.contains(activityId)) {
                continue;
            }
            baselineServiceRequestIds.add(serviceRequest.getId());
        }
        // 没有套餐内 SR 则不存在旧模型子项账
        if (baselineServiceRequestIds.isEmpty()) {
            return false;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        for (ChargeItem row : rows) {
            if (row == null || row.getServiceId() == null) {
                continue;
            }
            // 仅已结算子项账阻止迁移，待收费行仍可作废后改挂套餐 AD
            if (!ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())
                && baselineServiceRequestIds.contains(row.getServiceId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 加载诊疗活动产品读模型。
     *
     * @param activityId 活动定义主键
     * @return 产品读模型
     */
    private ProductDefinitionDisplayDto loadActivityProduct(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> map =
            hisProductCatalogPort.loadProductDisplayAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionDisplayDto product = map.get(key);
        if (product == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENSURE_PRICE_MISSING + ": 未找到体检项目定义");
        }
        return product;
    }
}
