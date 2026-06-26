package com.openhis.whale.unionpay.bpc.application;

import com.openhis.whale.unionpay.bpc.application.scan.UnionPayBpcScanApplicationService;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.RetrySettleFromBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.QueryBpcOrderResult;
import com.openhis.whale.unionpay.bpc.contracts.result.RetrySettleFromBpcOrderResult;
import com.openhis.whale.unionpay.bpc.domain.order.BpcOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 银联 BPC 对外 AppService（POST /api/app/unionPayBpc/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class UnionPayBpcAppService {

    private final UnionPayBpcScanApplicationService scanApplicationService;

    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        return scanApplicationService.prepareConsumerScan(command);
    }

    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        return scanApplicationService.applyClientScanResult(command);
    }

    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return scanApplicationService.applyClientRefundResult(command);
    }

    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return scanApplicationService.queryOrder(command);
    }

    public List<BpcOrderSummaryDto> getOrderByPayment(Long paymentId) {
        return scanApplicationService.getOrdersByPayment(paymentId).stream()
                .map(BpcOrderSummaryDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 半成功补结算资格校验：BPC 已 SUCCESS 时允许 health 侧重传 clientScanResult settle。
     *
     * @param command paymentId + merTradeNo
     * @return 是否可补结算
     */
    public RetrySettleFromBpcOrderResult retrySettleFromBpcOrder(RetrySettleFromBpcOrderCommand command) {
        if (command == null || command.getPaymentId() == null
            || command.getMerTradeNo() == null || command.getMerTradeNo().isBlank()) {
            return new RetrySettleFromBpcOrderResult()
                .setEligible(false)
                .setMessage("paymentId 与 merTradeNo 不能为空");
        }
        boolean eligible = scanApplicationService.isRetrySettleEligible(
            command.getPaymentId(),
            command.getMerTradeNo());
        return new RetrySettleFromBpcOrderResult()
            .setEligible(eligible)
            .setMessage(eligible
                ? "可补结算，请重传完整 clientScanResult"
                : "未找到成功的 BPC 订单，不可补结算");
    }

    /** BPC 订单摘要（AppService 出参）。 */
    public record BpcOrderSummaryDto(
            Long orderId,
            Long paymentId,
            Integer detailIndex,
            String merTradeNo,
            String tradeNo,
            String tranType,
            Long txnAmtFen,
            String payType,
            String status,
            String respCode,
            String respMsg,
            String posNo) {

        static BpcOrderSummaryDto from(BpcOrder order) {
            return new BpcOrderSummaryDto(
                    order.getId(),
                    order.getPaymentId(),
                    order.getDetailIndex(),
                    order.getMerTradeNo(),
                    order.getTradeNo(),
                    order.getTranType(),
                    order.getTxnAmtFen(),
                    order.getPayType(),
                    order.getStatus(),
                    order.getRespCode(),
                    order.getRespMsg(),
                    order.getPosNo());
        }
    }
}
