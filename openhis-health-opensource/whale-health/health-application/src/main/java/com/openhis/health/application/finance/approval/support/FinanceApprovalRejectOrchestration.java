/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.support;

import com.openhis.health.application.finance.priceadjustment.request.RejectPriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.service.PriceAdjustmentAppService;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FinanceApprovalConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 财务审批驳回分发编排（IF-APR-04 / AS-FIN-APR-001-SDD §3.5）。
 *
 * <p>调价：复用 {@link PriceAdjustmentAppService#reject}（物流 unlock → {@code rejectPriceAdjustment}）；
 * 供应：{@link SupplyRequestDomainService#rejectRequest} 写驳回态，不动账。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalRejectOrchestration {

    /** 调价驳回（含 unlock）。 */
    private final PriceAdjustmentAppService priceAdjustmentAppService;

    /** 供应驳回写。 */
    private final SupplyRequestDomainService supplyRequestDomainService;

    /** 审批人医务人员主键（供应 rejectRequest.approverId）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 分发驳回。
     *
     * @param busNo        单号
     * @param documentKind SUPPLY / PRICE_ADJUSTMENT
     * @param rejectReason 驳回原因；调价必填（BR-APR-010）；供应本页统一必填便于留痕
     * @return 驳回后状态码（供应 {@code REJECT} / 调价 {@code rejected}）
     * @throws BusinessException 缺原因、种类非法或下游失败
     */
    public String reject(String busNo, String documentKind, String rejectReason) {
        String kind = documentKind.trim();
        String trimmedBusNo = busNo.trim();
        // 调价：复用 AS-FIN-PRC-001 IF-PRC-14（unlock → rejectPriceAdjustment）
        if (FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT.equals(kind)) {
            // 调价驳回原因必填（BR-APR-010）
            if (StringUtils.isEmpty(rejectReason)) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "驳回须填写原因");
            }
            RejectPriceAdjustmentRequest request = new RejectPriceAdjustmentRequest();
            request.setBusNo(trimmedBusNo);
            request.setApproveComment(rejectReason.trim());
            this.priceAdjustmentAppService.reject(request);
            return PriceAdjustmentStatus.REJECTED.getCode();
        }
        // 非供应种类：拒绝
        if (!FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY.equals(kind)) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "documentKind 非法");
        }
        // 供应驳回原因本页统一必填，便于财务留痕（对齐进货等业务 AS）
        if (StringUtils.isEmpty(rejectReason)) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "驳回原因不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT,
            "审批人不能为空");
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(trimmedBusNo);
        item.setApproverId(operatorId);
        item.setChangeReasonCode(rejectReason.trim());
        command.setItems(List.of(item));
        // Cmd：领域写驳回态，不触发发放/库存
        this.supplyRequestDomainService.rejectRequest(command);
        return SupplyStatus.REJECT.getCode();
    }
}
