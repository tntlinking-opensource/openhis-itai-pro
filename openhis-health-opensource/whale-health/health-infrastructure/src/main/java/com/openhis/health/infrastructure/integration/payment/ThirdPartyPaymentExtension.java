/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.payment;

import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyQueryExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyReverseExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyScanExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyScanConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.payment.ExtensionOut.ThirdPartyQueryExtensionOut;
import com.openhis.health.infrastructure.integration.payment.ExtensionOut.ThirdPartyRefundExtensionOut;
import com.openhis.health.infrastructure.integration.payment.ExtensionOut.ThirdPartyReverseExtensionOut;
import com.openhis.health.infrastructure.integration.payment.ExtensionOut.ThirdPartyScanExtensionOut;

/**
 * 第三方收单（银联 BPC 等）扩展契约；unionpay-bpc Module 以 {@code @Primary} 覆盖 NoOp。
 *
 * <p><b>调用方</b>：{@code ThirdPartyPaymentSettleOrchestrator}、{@code ThirdPartyPaymentRefundOrchestrator}、
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
    default ThirdPartyScanExtensionOut applyClientScanResult(ThirdPartyScanConfirmExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 确认 Win 退货回传（通道 C · P0）。
     *
     * @param command 含原 merTradeNo、refundMerTradeNo、respCode 等
     * @return 逆向单落库结果
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default ThirdPartyRefundExtensionOut applyClientRefundResult(ThirdPartyRefundConfirmExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 查单 G（P0：读 {@code upc_bpc_order} 或 Win 轮询辅助）。
     *
     * @param command merTradeNo / tradeNo / paymentId
     * @return 订单状态快照
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default ThirdPartyQueryExtensionOut queryOrder(ThirdPartyQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单未装配");
    }

    /**
     * 被扫 C 服务端出站（通道 A · P1+）。
     *
     * @param command 含 scanCode、amount 等
     * @return BPC 交易结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyScanExtensionOut consumerScan(ThirdPartyScanExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单服务端出站未实现");
    }

    /**
     * 退货 R 服务端出站（通道 A · P1+）。
     *
     * @param command 含 merTradeNo、refundAmount 等
     * @return BPC 退货结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyRefundExtensionOut refund(ThirdPartyRefundExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单服务端出站未实现");
    }

    /**
     * 撤销 D（P2 · 通道 A）。
     *
     * @param command 含 merTradeNo 等
     * @return 撤销结果
     * @throws UnsupportedOperationException 未实现时
     */
    default ThirdPartyReverseExtensionOut reverse(ThirdPartyReverseExtensionIn command) {
        throw new UnsupportedOperationException("第三方收单撤销未实现");
    }
}
