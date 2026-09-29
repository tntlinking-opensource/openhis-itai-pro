/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 医嘱/处置列表：收费明细状态归并与「状态」列展示态（退费优先于请求态，不修改请求 status_enum）。
 */
public final class AdviceChargeDisplayStatusAssembler {

    /** 列表「状态」列：待退费（对应 {@link ChargeItemStatus#REFUNDING}）。 */
    public static final String LIST_DISPLAY_REFUNDING_TEXT = "待退费";

    /** 列表「状态」列：已退费（对应 {@link ChargeItemStatus#REFUNDED}）。 */
    public static final String LIST_DISPLAY_REFUNDED_TEXT = "已退费";

    /**
     * 医技站口语：{@link ChargeItemStatus#PENDING_SETTLEMENT}（护士执行/划价入账后待结算）。
     *
     * <p>枚举 display 仍为「待结算」；医技执行单/费用清单用本口语，避免与「尚未划价」混淆。</p>
     */
    public static final String LIST_DISPLAY_PENDING_SETTLEMENT_PRICED_TEXT = "已划价待结算";

    /** 工具类禁止实例化。 */
    private AdviceChargeDisplayStatusAssembler() {
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
        if (StringUtils.isEmpty(incomingStatus)) {
            return existingStatus;
        }
        if (StringUtils.isEmpty(existingStatus)) {
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
        // 排序权重相同：保留先归并态，避免展示抖动
        return existingStatus;
    }

    /**
     * 收费汇总态是否为退费相关（列表「状态」列须覆盖请求态展示）。
     *
     * @param chargeStatusEnum 收费状态码
     * @return 退费相关时为 true
     */
    public static boolean isRefundChargeStatus(String chargeStatusEnum) {
        if (StringUtils.isEmpty(chargeStatusEnum)) {
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
     * 收费汇总态是否仍为护士站可执行的有效计费态（待收费/已结算）。
     *
     * <p>退费中/已退费、空态或其它终态均视为不可再发放；供耗材侧栏 **BR-NUR-018** 与执行门禁共用。</p>
     *
     * @param chargeStatusEnum 收费状态码；可空
     * @return 仍可按已计费路径执行时为 true
     */
    public static boolean isActiveBillableChargeStatus(String chargeStatusEnum) {
        // 无收费锚点：护士站耗材侧栏不得当「待发放」勾选
        if (StringUtils.isEmpty(chargeStatusEnum)) {
            return false;
        }
        // 待收费：签发后尚未收银，侧栏仍可展示但执行另有门控
        if (ChargeItemStatus.PLANNED.getCode().equals(chargeStatusEnum)) {
            return true;
        }
        // 已结算：门诊护士站耗材待发主路径
        if (ChargeItemStatus.SETTLED.getCode().equals(chargeStatusEnum)) {
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
        if (StringUtils.isEmpty(chargeStatusEnum)) {
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
     * 医技工作站收费状态展示文案。
     *
     * <p>{@code PENDING_SETTLEMENT} → 「已划价待结算」（护士执行入账后）；其余同
     * {@link #resolveChargeStatusDisplayTextForAdviceList}。</p>
     *
     * @param chargeStatusEnum 收费状态码
     * @return 展示文案
     */
    public static String resolveMedicalTechChargeStatusDisplayText(String chargeStatusEnum) {
        if (StringUtils.isEmpty(chargeStatusEnum)) {
            return "";
        }
        if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeStatusEnum)) {
            return LIST_DISPLAY_PENDING_SETTLEMENT_PRICED_TEXT;
        }
        return resolveChargeStatusDisplayTextForAdviceList(chargeStatusEnum);
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
        // 退费态优先于请求态：列表「状态」列须展示待退费/已退费
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
        // 已完成请求始终纳入处置单
        if (RequestStatus.COMPLETED.getCode().equals(requestStatusEnum)) {
            return true;
        }
        if (requestId == null || StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        // 非完成态但已退费：仍展示以便护士跟进退费进度
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
