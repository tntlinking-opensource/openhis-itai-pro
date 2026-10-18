/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.assembler;

import com.zxplat.domain.share.constants.InpatientSettleConstants;
import com.zxplat.domain.share.constants.OutpatientChargeConstants;
import com.zxplat.plugin.einvoice.boss.domain.share.BossServiceIds;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.domain.enums.IssueKind;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 {@code issue_kind} 路由博思开具报文组装器（gateway scheme 出站入口）。
 *
 * <p>调用链：application {@code BossIssueOutboundSupport} → bridge {@code EinvIssueInvoker}
 * → 本类 {@link #build} → 各线 {@code *PayloadAssembler} → HTTP 信封。</p>
 *
 * <p>{@link #resolveServiceId} / {@link #resolveBusType} 在单据初始化与 Worker 出站前使用，
 * 保证 health 票据类型与博思 serviceId、busType 一致。</p>
 */
@Component
@RequiredArgsConstructor
public class BossPayloadBuilder {

    private final RegistrationPayloadAssembler registrationPayloadAssembler;
    private final OutpatientPayloadAssembler outpatientPayloadAssembler;
    private final HospitalizedPayloadAssembler hospitalizedPayloadAssembler;
    private final CommonPayloadAssembler commonPayloadAssembler;

    /**
     * 解析 serviceId。
     *
     * @param issueKind 开票种类码
     * @return 博思 serviceId
     */
    public String resolveServiceId(String issueKind) {
        if (IssueKind.COM.getCode().equals(issueKind)) {
            return BossServiceIds.INVOICE_E_BILL_COMMON;
        }
        if (IssueKind.AMB.getCode().equals(issueKind)) {
            return BossServiceIds.INVOICE_E_BILL_OUTPATIENT;
        }
        if (IssueKind.IMP.getCode().equals(issueKind)) {
            return BossServiceIds.INV_E_BILL_HOSPITALIZED;
        }
        return BossServiceIds.INV_E_BILL_REGISTRATION;
    }

    /**
     * 根据单据种类组装业务 JSON。
     *
     * @param document boss 单据
     * @param lines 明细
     * @param snapshot health 快照
     * @return 业务 JSON 明文
     */
    public String build(EinvBossDocument document, List<EinvBossLine> lines, BossIssueSnapshot snapshot) {
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            return commonPayloadAssembler.assemble(document, lines, snapshot);
        }
        if (IssueKind.AMB.getCode().equals(document.getIssueKind())) {
            return outpatientPayloadAssembler.assemble(document, lines, snapshot);
        }
        if (IssueKind.IMP.getCode().equals(document.getIssueKind())) {
            return hospitalizedPayloadAssembler.assemble(document, lines, snapshot);
        }
        return registrationPayloadAssembler.assemble(document, lines, snapshot);
    }

    /**
     * 根据 health 票据类型推断 issue_kind。
     *
     * @param invoiceTypeCode health type_code
     * @return issue_kind
     */
    public String resolveIssueKind(String invoiceTypeCode) {
        if (OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT.equals(invoiceTypeCode)) {
            return IssueKind.AMB.getCode();
        }
        if (InpatientSettleConstants.INVOICE_TYPE_INPATIENT.equals(invoiceTypeCode)) {
            return IssueKind.IMP.getCode();
        }
        return IssueKind.REG.getCode();
    }

    /**
     * 根据 issue_kind 解析 busType。
     *
     * @param issueKind issue_kind
     * @return busType
     */
    public String resolveBusType(String issueKind) {
        if (IssueKind.AMB.getCode().equals(issueKind)) {
            return IssueKind.AMB.getBusType();
        }
        if (IssueKind.IMP.getCode().equals(issueKind)) {
            return IssueKind.IMP.getBusType();
        }
        if (IssueKind.COM.getCode().equals(issueKind)) {
            return null;
        }
        return IssueKind.REG.getBusType();
    }
}
