/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.zxplat.plugin.chs.domain.inventory.access.InventoryInitializeFillAccess;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.share.enums.InvChgType;
import com.zxplat.plugin.chs.domain.inventory.command.InitializeInventoryCommand;
import com.zxplat.plugin.chs.domain.inventory.command.InitializeInventoryPairCommand;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.result.InitializeInventoryFillMeta;
import com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadQueryService;
import com.zxplat.plugin.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 批初始化配对填充 bridge 实现（P14）。
 *
 * <p><b>职责：</b>从 health-domain 库房库存与药品主数据组装 3501+3502 配对请求，支持分页与跳过已初始化行。</p>
 * <p><b>实现端口：</b>{@link com.zxplat.plugin.chs.domain.inventory.access.InventoryInitializeFillAccess}</p>
 * <p><b>调用方：</b>{@code InventoryInitializeWorkflow}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryInitializeUploadContextLoader implements InventoryInitializeFillAccess {

    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadQueryService invUploadQueryService;

    /**
     * 按库位分页扫描库存，填充 {@link InitializeInventoryCommand#getPairs()} 并返回分页元数据。
     */
    public InitializeInventoryFillMeta fillPairs(InitializeInventoryCommand request) {
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "locationId 不能为空");
        }
        int maxLines = ChsInventoryConstants.INITIALIZE_INVENTORY_DEFAULT_MAX_LINES;
        if (request.getMaxLines() != null && request.getMaxLines() > 0) {
            maxLines = request.getMaxLines();
        }
        int startPage = 0;
        if (request.getPageNo() != null && request.getPageNo() >= 0) {
            startPage = request.getPageNo();
        }
        boolean skipInitialized = true;
        if (request.getSkipInitialized() != null) {
            skipInitialized = request.getSkipInitialized();
        }

        String operatorName = resolveCurrentOperatorName();
        String invDate = GeneralSharedConstants.DATE_DISPLAY_FORMATTER.format(java.time.LocalDate.now());
        String invChgTime = GeneralSharedConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now());
        Long tenantId = TenantUtils.getCurrentTenantId();

        InventoryItemPageQuery query = new InventoryItemPageQuery();
        query.setLocationId(request.getLocationId());
        query.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        query.setInventoryStatusEnum(PublicationStatus.ACTIVE.getCode());

        List<InitializeInventoryPairCommand> pairs = new ArrayList<>();
        int skippedAlready = 0;
        int currentPage = startPage;
        long totalElements = 0;
        int totalPages = 0;
        boolean hasMore = false;

        while (pairs.size() < maxLines) {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(
                query, PageRequest.of(currentPage, maxLines, Sort.by(Sort.Direction.ASC, "id")));
            if (currentPage == startPage) {
                totalElements = page.getTotalElements();
                totalPages = page.getTotalPages();
            }
            if (page.isEmpty()) {
                break;
            }
            for (InventoryItemModel item : page.getContent()) {
                if (pairs.size() >= maxLines) {
                    break;
                }
                if (item == null || item.getId() == null) {
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (skipInitialized && invUploadQueryService.isInitializePairComplete(tenantId, item.getId())) {
                    skippedAlready++;
                    continue;
                }
                MedicationDefinition medication = medicationDefinitionQueryService
                    .findOptionalMedicationDefinitionEntityById(item.getItemId())
                    .orElse(null);
                if (medication == null) {
                    continue;
                }
                // 规范 manu_date/expy_end 必填：缺生产日期或有效期则本行跳过
                if (item.getProductionDate() == null || item.getExpirationDate() == null) {
                    continue;
                }
                InitializeInventoryPairCommand pair = buildPair(
                    item, medication, invDate, invChgTime, operatorName, request.getRemark());
                if (pair != null) {
                    pairs.add(pair);
                }
            }
            if (currentPage + 1 < page.getTotalPages()) {
                hasMore = true;
                currentPage++;
            } else {
                hasMore = false;
                break;
            }
        }

        request.setPairs(pairs);
        Integer nextPageNo = null;
        if (hasMore) {
            nextPageNo = currentPage + 1;
        }
        return new InitializeInventoryFillMeta()
            .setPageNo(startPage)
            .setNextPageNo(nextPageNo)
            .setHasMore(hasMore)
            .setTotalElements(totalElements)
            .setTotalPages(totalPages)
            .setSkippedAlreadyInitialized(skippedAlready);
    }

    private InitializeInventoryPairCommand buildPair(
        InventoryItemModel item,
        MedicationDefinition medication,
        String invDate,
        String invChgTime,
        String operatorName,
        String remark
    ) {
        // 批初始化无业务单据：以库存行主键作为机构批次流水号，保证同品种多批不撞键
        String fixmedinsBchno = InventoryUploadBatchNoAssembler.buildFixmedinsBchno(String.valueOf(item.getId()));
        if (isEmpty(fixmedinsBchno)) {
            return null;
        }
        // 库内 quantity 为拆零量 → 换成库存行操作单位数量（与 3502/3503 库房口径一致）
        BigDecimal invCnt = InventoryUploadQuantityAssembler.splitQuantityToOperationUnit(
            medication, item.getUnitCode(), item.getQuantity());
        if (invCnt == null || invCnt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // 配对 3502 平台 pric 必填：取品种销售定价（包装锚）→ 换到库存操作单位；无价则跳过
        BigDecimal packageSalePrice = chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
            TableNameConstants.MED_MEDICATION_DEFINITION, medication.getId());
        if (packageSalePrice == null) {
            return null;
        }
        BigDecimal opUnitPrice = InventoryUploadQuantityAssembler.convertUnitPricePackageAnchored(
            medication,
            medication.getPackageUnitCode(),
            packageSalePrice,
            item.getUnitCode());
        if (opUnitPrice == null) {
            opUnitPrice = MonetaryAmountSupport.scaleCalc(packageSalePrice);
        }
        String manuDate = item.getProductionDate().toLocalDate()
            .format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        String expyEnd = item.getExpirationDate().toLocalDate()
            .format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);

        InvUploadLineCommand line3501 = new InvUploadLineCommand();
        line3501.setSourceLineId(item.getId());
        line3501.setMedListCodg(trimToNull(medication.getYbNo()));
        line3501.setFixmedinsHilistId(medication.getBusNo());
        line3501.setFixmedinsHilistName(medication.getName());
        line3501.setFixmedinsBchno(fixmedinsBchno);
        line3501.setManuLotnum(item.getLotNumber());
        line3501.setInvCnt(invCnt);
        // invdate：上报当天
        line3501.setInvdate(invDate);
        // 药典未维护处方标志 → 非处方药 0
        line3501.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        // memo：initialize 请求 remark
        line3501.setMemo(remark);
        line3501.setManuDate(manuDate);
        line3501.setExpyEnd(expyEnd);
        // 无医保目录编码：后续编排按 NO_YB_NO 跳过
        if (isEmpty(line3501.getMedListCodg())) {
            line3501.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }

        // 配对 3502：cnt=inv_cnt、pric=操作单位售价、inv_chg_type=106；初始化无库存追溯码 → trdn=整包装
        InvUploadLineCommand line3502 = new InvUploadLineCommand();
        line3502.setSourceLineId(item.getId());
        line3502.setMedListCodg(line3501.getMedListCodg());
        line3502.setFixmedinsHilistId(line3501.getFixmedinsHilistId());
        line3502.setFixmedinsHilistName(line3501.getFixmedinsHilistName());
        line3502.setFixmedinsBchno(fixmedinsBchno);
        line3502.setManuLotnum(item.getLotNumber());
        // 初始化 3502：cnt 与 3501 inv_cnt 同值且为正（106 其他入库）
        line3502.setCnt(InvChgType.OTHER_IN.toSignedCnt(invCnt));
        line3502.setPric(opUnitPrice);
        line3502.setInvChgType(InvChgType.OTHER_IN.getCode());
        line3502.setInvChgTime(invChgTime);
        line3502.setInvChgOpterName(operatorName);
        line3502.setRxFlag(line3501.getRxFlag());
        line3502.setMemo(remark);
        line3502.setManuDate(manuDate);
        line3502.setExpyEnd(expyEnd);
        // DD §4.2：未扫默认 trdn_flag=1
        line3502.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
        if (isEmpty(line3502.getMedListCodg())) {
            line3502.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }

        return new InitializeInventoryPairCommand()
            .setStocktake3501(line3501)
            .setInvChange3502(line3502);
    }

    private String resolveCurrentOperatorName() {
        try {
            Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
            Practitioner practitioner = practitionerQueryService.findById(operatorId);
            if (practitioner != null) {
                return practitioner.getName();
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }
}
