/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.service;

import com.zxplat.plugin.chs.domain.invoke.entity.YbInvokeLog;
import com.zxplat.plugin.chs.domain.invoke.service.YbInvokeLogQueryService;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.zxplat.plugin.chs.domain.settlement.YbSettlementVoucherStatus;
import com.zxplat.plugin.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSettledCorrectionQueryServiceTest {

    private static final Long PAYMENT_ID = 100L;
    private static final Long TENANT_ID = 1L;
    private static final Long OPERATOR_ID = 9L;

    @Mock
    private YbInvokeLogQueryService invokeLogQueryService;
    @Mock
    private YbSettlementRecordQueryService settlementRecordQueryService;
    @Mock
    private YbClinicPreSettleQueryService clinicPreSettleQueryService;

    private PaymentSettledCorrectionQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentSettledCorrectionQueryService(
            invokeLogQueryService,
            settlementRecordQueryService,
            clinicPreSettleQueryService);
    }

    @Test
    void skips2601WhenFailed2207FollowedBySuccessfulRetry() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 1, 5, 31, 35);
        LocalDateTime successAt = LocalDateTime.of(2026, 9, 1, 5, 32, 6);
        YbInvokeLog failed = failedInvoke("MSG-FAIL", failedAt);
        YbInvokeLog success = successInvoke("MSG-OK", successAt);
        when(invokeLogQueryService.listByPaymentDraftId(PAYMENT_ID, TENANT_ID))
            .thenReturn(List.of(failed, success));

        List<SettlementCorrectionCommand> commands =
            service.listUncertainByPayment(PAYMENT_ID, OPERATOR_ID, TENANT_ID);

        assertThat(commands).isEmpty();
    }

    @Test
    void usesFailedInvokeMsgidWithoutBindingLaterSuccessRecord() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 1, 5, 31, 35);
        YbInvokeLog failed = failedInvoke("MSG-FAIL", failedAt);
        failed.setChrgBchno("BATCH-1");
        when(invokeLogQueryService.listByPaymentDraftId(PAYMENT_ID, TENANT_ID))
            .thenReturn(List.of(failed));

        YbSettlementRecord successRecord = new YbSettlementRecord();
        successRecord.setId(200L);
        successRecord.setPaymentId(PAYMENT_ID);
        successRecord.setChrgBchno("BATCH-1");
        successRecord.setMsgid2207("MSG-OK");
        successRecord.setPsnNo("PSN001");
        successRecord.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        when(settlementRecordQueryService.listByPaymentId(PAYMENT_ID, TENANT_ID))
            .thenReturn(List.of(successRecord));

        YbClinicPreSettle pre = new YbClinicPreSettle();
        pre.setPsnNo("PSN001");
        when(clinicPreSettleQueryService.findOptionalLatestByPaymentDraftId(PAYMENT_ID, TENANT_ID))
            .thenReturn(Optional.of(pre));

        List<SettlementCorrectionCommand> commands =
            service.listUncertainByPayment(PAYMENT_ID, OPERATOR_ID, TENANT_ID);

        assertThat(commands).hasSize(1);
        SettlementCorrectionCommand command = commands.get(0);
        assertThat(command.getOmsgid()).isEqualTo("MSG-FAIL");
        assertThat(command.getSettlementRecordId()).isNull();
        assertThat(command.getPsnNo()).isEqualTo("PSN001");
    }

    @Test
    void bindsRecordOnlyWhenFailedMsgidMatchesMsgid2207() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 1, 5, 31, 35);
        YbInvokeLog failed = failedInvoke("MSG-FAIL", failedAt);
        when(invokeLogQueryService.listByPaymentDraftId(PAYMENT_ID, TENANT_ID))
            .thenReturn(List.of(failed));

        YbSettlementRecord matched = new YbSettlementRecord();
        matched.setId(201L);
        matched.setPaymentId(PAYMENT_ID);
        matched.setMsgid2207("MSG-FAIL");
        matched.setSetlId("SETL-1");
        matched.setPsnNo("PSN002");
        matched.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        when(settlementRecordQueryService.listByPaymentId(PAYMENT_ID, TENANT_ID))
            .thenReturn(List.of(matched));

        List<SettlementCorrectionCommand> commands =
            service.listUncertainByPayment(PAYMENT_ID, OPERATOR_ID, TENANT_ID);

        assertThat(commands).hasSize(1);
        SettlementCorrectionCommand command = commands.get(0);
        assertThat(command.getOmsgid()).isEqualTo("MSG-FAIL");
        assertThat(command.getSettlementRecordId()).isEqualTo(201L);
        assertThat(command.getPsnNo()).isEqualTo("PSN002");
    }

    private static YbInvokeLog failedInvoke(String msgid, LocalDateTime createdAt) {
        YbInvokeLog log = new YbInvokeLog();
        log.setInfno(ChsInfNo.OUTPATIENT_SETTLE);
        log.setInfcode("-1");
        log.setMsgid(msgid);
        log.setPaymentDraftId(PAYMENT_ID);
        log.setContractBusNo("BUS-1");
        log.setCreatedAt(createdAt);
        return log;
    }

    private static YbInvokeLog successInvoke(String msgid, LocalDateTime createdAt) {
        YbInvokeLog log = new YbInvokeLog();
        log.setInfno(ChsInfNo.OUTPATIENT_SETTLE);
        log.setInfcode(ChsPlatformCodes.INFCODE_SUCCESS);
        log.setMsgid(msgid);
        log.setPaymentDraftId(PAYMENT_ID);
        log.setContractBusNo("BUS-1");
        log.setCreatedAt(createdAt);
        return log;
    }
}
