/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment;

import com.openhis.health.integration.payment.command.ThirdPartyQueryCommand;
import com.openhis.health.integration.payment.command.ThirdPartyRefundCommand;
import com.openhis.health.integration.payment.command.ThirdPartyRefundConfirmCommand;
import com.openhis.health.integration.payment.command.ThirdPartyReverseCommand;
import com.openhis.health.integration.payment.command.ThirdPartyScanCommand;
import com.openhis.health.integration.payment.command.ThirdPartyScanConfirmCommand;
import com.openhis.health.integration.payment.result.ThirdPartyQueryResult;
import com.openhis.health.integration.payment.result.ThirdPartyRefundResult;
import com.openhis.health.integration.payment.result.ThirdPartyReverseResult;
import com.openhis.health.integration.payment.result.ThirdPartyScanResult;

/**
 * 第三方收单（银联 BPC 等）扩展契约；unionpay-bpc Module 以 {@code @Primary} 覆盖 NoOp。
 *
 * <p><b>调用方</b>：{@code ThirdPartyPaymentSettleSupport}、{@code ThirdPartyPaymentRefundSupport}、
 * 及各 financial AppService（经 Support 间接调用）。</p>
 *
 * <p><b>实现</b>：{@code ThirdPartyPaymentExtensionImpl}（unionpay-bpc-adapter）。</p>
 */
public interface ThirdPartyPaymentExtension {

    /**
     * 院点是否开通银联被扫（模块 + contract 均就绪时为 true）。
     *
     * @return false 时收银不出现「扫码支付」入口
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 是否由本 Extension 处理该支付渠道。
     *
     * @param channelCode {@code payChannelCode} / {@code pay_enum}
     * @return {@code SCAN_PAY} 时为 true（unionpay-bpc 实现）
     */
    default boolean supportsChannel(String channelCode) {
        return false;
    }

    /**
     * 确认 Win 被扫回传（通道 C · P0）。
     *
     * @param command 含 merTradeNo、tradeNo、payType、respCode 等
     * @return 落库 {@code upc_bpc_order} 后的结果
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default ThirdPartyScanResult applyClientScanResult(ThirdPartyScanConfirmCommand command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 确认 Win 退货回传（通道 C · P0）。
     *
     * @param command 含原 merTradeNo、refundMerTradeNo、respCode 等
     * @return 逆向单落库结果
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default ThirdPartyRefundResult applyClientRefundResult(ThirdPartyRefundConfirmCommand command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 查单 G（P0：读 {@code upc_bpc_order} 或 Win 轮询辅助）。
     *
     * @param command merTradeNo / tradeNo / paymentId
     * @return 订单状态快照
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default ThirdPartyQueryResult queryOrder(ThirdPartyQueryCommand command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 被扫 C 服务端出站（通道 A · P1+）。
     *
     * @param command 含 scanCode、amount 等
     * @return BPC 交易结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyScanResult consumerScan(ThirdPartyScanCommand command) {
        throw new UnsupportedOperationException("第三方收单服务端出站未实现");
    }

    /**
     * 退货 R 服务端出站（通道 A · P1+）。
     *
     * @param command 含 merTradeNo、refundAmount 等
     * @return BPC 退货结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyRefundResult refund(ThirdPartyRefundCommand command) {
        throw new UnsupportedOperationException("第三方收单服务端出站未实现");
    }

    /**
     * 撤销 D（P2 · 通道 A）。
     *
     * @param command 含 merTradeNo 等
     * @return 撤销结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyReverseResult reverse(ThirdPartyReverseCommand command) {
        throw new UnsupportedOperationException("第三方收单撤销未实现");
    }
}
