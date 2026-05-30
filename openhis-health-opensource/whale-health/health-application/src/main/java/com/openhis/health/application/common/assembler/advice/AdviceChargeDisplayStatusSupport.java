/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.common.assembler.advice;

import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 医嘱/处置列表：收费明细状态归并与「状态」列展示态（退费优先于请求态，不修改请求 status_enum）。
 */
public final class AdviceChargeDisplayStatusSupport {

    /** 列表「状态」列：待退费（对应 {@link ChargeItemStatus#REFUNDING}）。 */
    public static final String LIST_DISPLAY_REFUNDING_TEXT = "待退费";

    /** 列表「状态」列：已退费（对应 {@link ChargeItemStatus#REFUNDED}）。 */
    public static final String LIST_DISPLAY_REFUNDED_TEXT = "已退费";

    private AdviceChargeDisplayStatusSupport() {
    }

    /**
     * 列表「状态」列展示码与文案（收费退费时优先收费态，否则回落请求态）。
     *
     * @param requestStatusEnum     请求状态码
     * @param requestStatusEnumText 请求状态展示
     * @param chargeStatusEnum      行级收费汇总态码
     */
    public record ListDisplayStatus(String displayStatusEnum, String displayStatusEnumText) {
    }

    /**
     * 同一请求锚点下多条收费行状态归并：取 {@link ChargeItemStatus#getSortOrder()} 较大者（退费态优先于已结算等）。
     *
     * @param existingStatus 已归并态
     * @param incomingStatus 新收费行态
     * @return 归并后的状态码
     */
    public static String mergeChargeStatusForRollup(String existingStatus, String incomingStatus) {
        if (StringUtils.isBlank(incomingStatus)) {
            return existingStatus;
        }
        if (StringUtils.isBlank(existingStatus)) {
            return incomingStatus;
        }
        ChargeItemStatus existing = EnumUtil.fromCode(ChargeItemStatus.class, existingStatus);
        ChargeItemStatus incoming = EnumUtil.fromCode(ChargeItemStatus.class, incomingStatus);
        if (existing == null) {
            return incomingStatus;
        }
        if (incoming == null) {
            return existingStatus;
        }
        if (incoming.getSortOrder() > existing.getSortOrder()) {
            return incomingStatus;
        }
        if (incoming.getSortOrder() < existing.getSortOrder()) {
            return existingStatus;
        }
        return existingStatus;
    }

    /**
     * 收费汇总态是否为退费相关（列表「状态」列须覆盖请求态展示）。
     *
     * @param chargeStatusEnum 收费状态码
     * @return 退费相关时为 true
     */
    public static boolean isRefundChargeStatus(String chargeStatusEnum) {
        if (StringUtils.isBlank(chargeStatusEnum)) {
            return false;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(chargeStatusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(chargeStatusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 收费状态 → 列表展示文案（退费态使用护士站/医生站统一口语，其余走枚举 display）。
     *
     * @param chargeStatusEnum 收费状态码
     * @return 展示文案
     */
    public static String resolveChargeStatusDisplayTextForAdviceList(String chargeStatusEnum) {
        if (StringUtils.isBlank(chargeStatusEnum)) {
            return "";
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(chargeStatusEnum)) {
            return LIST_DISPLAY_REFUNDING_TEXT;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(chargeStatusEnum)) {
            return LIST_DISPLAY_REFUNDED_TEXT;
        }
        ChargeItemStatus parsed = EnumUtil.fromCode(ChargeItemStatus.class, chargeStatusEnum);
        if (parsed != null) {
            return parsed.getDisplay();
        }
        return chargeStatusEnum;
    }

    /**
     * 解析医生站/住院医生站医嘱列表「状态」列展示态（不改动请求 statusEnum 字段语义）。
     *
     * @param requestStatusEnum     请求状态码
     * @param requestStatusEnumText 请求状态展示
     * @param chargeStatusEnum      行级收费汇总态
     * @return 展示码与文案
     */
    public static ListDisplayStatus resolveAdviceListDisplayStatus(
        String requestStatusEnum,
        String requestStatusEnumText,
        String chargeStatusEnum
    ) {
        if (isRefundChargeStatus(chargeStatusEnum)) {
            String text = resolveChargeStatusDisplayTextForAdviceList(chargeStatusEnum);
            return new ListDisplayStatus(chargeStatusEnum, text);
        }
        String requestCode = StringUtils.defaultIfBlank(requestStatusEnum, "");
        String requestText = StringUtils.defaultIfBlank(requestStatusEnumText, requestCode);
        return new ListDisplayStatus(requestCode, requestText);
    }

    /**
     * 是否为退单/退药逆向复制出的请求行（{@code refund_*_id} 非空），处置单列表应排除。
     *
     * @param refundPairRequestId 指向原单的退单主键
     * @return 逆向复制行时为 true
     */
    public static boolean isRefundOrderCopyRequest(Long refundPairRequestId) {
        return refundPairRequestId != null;
    }

    /**
     * 护士站处置单是否纳入该行：已完成请求，或虽非完成态但收费已为退费中/已退费。
     *
     * @param requestStatusEnum 请求状态码
     * @param serviceTable      请求表持久化名
     * @param requestId         请求主键
     * @param chargeStatusEnum  已解析的行级收费汇总态（可为 null）
     * @return 纳入列表时为 true
     */
    public static boolean shouldIncludeNurseStationTreatmentSheetRow(
        String requestStatusEnum,
        String serviceTable,
        Long requestId,
        String chargeStatusEnum
    ) {
        if (RequestStatus.COMPLETED.getCode().equals(requestStatusEnum)) {
            return true;
        }
        if (requestId == null || StringUtils.isBlank(serviceTable)) {
            return false;
        }
        return isRefundChargeStatus(chargeStatusEnum);
    }

    /**
     * 护士站处置列表「状态」列：收费退费态优先，否则回落执行态文案（待执行/已完成）。
     *
     * @param chargeStatusEnum    行级收费汇总态
     * @param executionStatusText 执行进度展示（待执行/已完成）
     * @return 列表展示码与文案
     */
    public static ListDisplayStatus resolveNurseTreatmentLineDisplayStatus(
        String chargeStatusEnum,
        String executionStatusText
    ) {
        if (isRefundChargeStatus(chargeStatusEnum)) {
            String refundText = resolveChargeStatusDisplayTextForAdviceList(chargeStatusEnum);
            return new ListDisplayStatus(chargeStatusEnum, refundText);
        }
        String executionText = StringUtils.defaultIfBlank(executionStatusText, "待执行");
        return new ListDisplayStatus("", executionText);
    }
}
