package com.openhis.whale.einvoice.boss.application.boss;

import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.whale.einvoice.boss.application.boss.assembler.CommonPayloadAssembler;
import com.openhis.whale.einvoice.boss.application.boss.assembler.HospitalizedPayloadAssembler;
import com.openhis.whale.einvoice.boss.application.boss.assembler.OutpatientPayloadAssembler;
import com.openhis.whale.einvoice.boss.application.boss.assembler.RegistrationPayloadAssembler;
import com.openhis.whale.einvoice.boss.contracts.constants.BossServiceIds;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 issue_kind 路由博思报文组装器。
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
