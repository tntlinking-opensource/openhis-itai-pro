/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.notice;

import com.zxplat.plugin.chs.domain.notice.command.ComplianceGateCommand;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceGateResult;
import com.zxplat.plugin.chs.domain.notice.queryservice.NationalCatalogOverlayQueryService;
import com.zxplat.plugin.chs.domain.notice.service.CatalogNoticeComplianceGate;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结算链最小侵入门禁断言（门诊/住院 ContextLoader 挂接）。
 * <p>封装 {@link CatalogNoticeComplianceGate#evaluate}；阻断时抛出带门禁码的业务异常。
 * 支持本批 {@code yb_no} 预加载 overlay，消除按行 N+1（门禁规则不变）。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogNoticeComplianceAssertSupport {

    private final CatalogNoticeComplianceGate complianceGate;
    private final NationalCatalogOverlayQueryService overlayQueryService;

    /**
     * 为本批国家目录编码一次加载 overlay（key=hilistCode）；无通知叠加的编码不在 Map 中。
     *
     * <p>pick 规则与单条 {@link NationalCatalogOverlayQueryService#getOverlay} 一致。</p>
     *
     * @param tenantId    租户
     * @param contractId  契约
     * @param hilistCodes 本批 yb_no（可含重复）
     * @return 不可变语义的可写 HashMap（可能为空）；never null
     */
    public Map<String, YbNationalCatalogNoticeOverlay> preloadOverlays(
            Long tenantId,
            Long contractId,
            Collection<String> hilistCodes) {
        // 无编码：返回空 Map，后续 assert 视为无 overlay
        if (hilistCodes == null || hilistCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return overlayQueryService.mapOverlaysByHilistCodes(tenantId, contractId, null, hilistCodes);
    }

    /**
     * 断言当前行允许医保开单/上传；不通过则抛出带门禁码的 {@link BusinessException}。
     *
     * @param tenantId    租户
     * @param contractId  契约
     * @param hilistCode  国家目录编码（yb_no）
     * @param projectCode 院内产品 busNo / 项目码；可空
     * @param unitPrice   计价单价；可空则跳过限价比较
     */
    public void assertAllowed(
            Long tenantId,
            Long contractId,
            String hilistCode,
            String projectCode,
            BigDecimal unitPrice) {
        this.assertAllowed(tenantId, contractId, hilistCode, projectCode, unitPrice, null, null, null, null);
    }

    /**
     * 断言当前行允许医保开单/上传（带项目展示信息，便于前端跳转补码）。
     *
     * @param tenantId             租户
     * @param contractId           契约
     * @param hilistCode           国家目录编码（yb_no）
     * @param projectCode          院内产品 busNo / 项目码；可空
     * @param unitPrice            计价单价；可空则跳过限价比较
     * @param itemDisplayName      收费项目名称；可空
     * @param productDefinitionId  院内产品定义主键；可空
     * @param productTableName     产品表名；可空
     */
    public void assertAllowed(
            Long tenantId,
            Long contractId,
            String hilistCode,
            String projectCode,
            BigDecimal unitPrice,
            String itemDisplayName,
            Long productDefinitionId,
            String productTableName) {
        this.assertAllowed(
            tenantId,
            contractId,
            hilistCode,
            projectCode,
            unitPrice,
            itemDisplayName,
            productDefinitionId,
            productTableName,
            null);
    }

    /**
     * 断言当前行允许医保开单/上传；可传入本批预加载 overlay，避免按行查库。
     *
     * @param tenantId             租户
     * @param contractId           契约
     * @param hilistCode           国家目录编码（yb_no）
     * @param projectCode          院内产品 busNo / 项目码；可空
     * @param unitPrice            计价单价；可空则跳过限价比较
     * @param itemDisplayName      收费项目名称；可空
     * @param productDefinitionId  院内产品定义主键；可空
     * @param productTableName     产品表名；可空
     * @param overlayByHilist      本批预加载；null 时退回单条 getOverlay
     */
    public void assertAllowed(
            Long tenantId,
            Long contractId,
            String hilistCode,
            String projectCode,
            BigDecimal unitPrice,
            String itemDisplayName,
            Long productDefinitionId,
            String productTableName,
            Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist) {
        ComplianceGateCommand command = new ComplianceGateCommand();
        command.setTenantId(tenantId);
        command.setContractId(contractId);
        command.setHilistCode(hilistCode);
        command.setProjectCode(projectCode);
        command.setUnitPrice(unitPrice);
        command.setItemDisplayName(itemDisplayName);
        command.setProductDefinitionId(productDefinitionId);
        command.setProductTableName(productTableName);
        ComplianceGateResult result = complianceGate.evaluate(command, overlayByHilist);
        // 放行或无结果：结算链继续
        if (result == null || result.isAllowed()) {
            return;
        }
        this.throwBlocked(result, command);
    }

    /**
     * 将门禁阻断结果转为业务异常（含未映射补码详情）。
     *
     * @param result  门禁结果（allowed=false）
     * @param command 原入参（补详情字段）
     */
    private void throwBlocked(ComplianceGateResult result, ComplianceGateCommand command) {
        String code = result.getCode();
        // 门禁码缺失时用通用阻断码，避免空错误码上抛
        if (code == null || code.isBlank()) {
            code = "NOTICE_COMPLIANCE_BLOCKED";
        }
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            message = "医保文件门禁拒绝：" + code;
        }
        if (CatalogNoticeComplianceGate.C_PROJECT_UNMAPPED.equals(code)) {
            throw new BusinessException(code, message, this.buildUnmappedDetails(result, command));
        }
        throw new BusinessException(code, message);
    }

    /**
     * 组装「未映射国码」前端补码引导详情。
     *
     * @param result  门禁结果
     * @param command 原入参
     * @return 详情 Map
     */
    private Map<String, Object> buildUnmappedDetails(
        ComplianceGateResult result,
        ComplianceGateCommand command) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", "SUPPLEMENT_YB_NO");
        String itemName = result.getItemDisplayName();
        if (itemName == null || itemName.isBlank()) {
            itemName = command.getItemDisplayName();
        }
        if (itemName != null && !itemName.isBlank()) {
            details.put("itemName", itemName);
        }
        String projectCode = result.getProjectCode();
        if (projectCode == null || projectCode.isBlank()) {
            projectCode = result.getHilistCode();
        }
        if (projectCode == null || projectCode.isBlank()) {
            projectCode = command.getProjectCode();
        }
        if (projectCode == null || projectCode.isBlank()) {
            projectCode = command.getHilistCode();
        }
        if (projectCode != null && !projectCode.isBlank()) {
            details.put("projectCode", projectCode);
        }
        Long productId = result.getProductDefinitionId();
        if (productId == null) {
            productId = command.getProductDefinitionId();
        }
        if (productId != null) {
            details.put("productDefinitionId", String.valueOf(productId));
        }
        String productTable = result.getProductTableName();
        if (productTable == null || productTable.isBlank()) {
            productTable = command.getProductTableName();
        }
        if (productTable != null && !productTable.isBlank()) {
            details.put("productTable", productTable.trim());
        }
        return details;
    }
}
