/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 住院嘱托医嘱与出院带药诊疗行写前 Gate；门诊禁开嘱托识别同源（AS-IP-DOR-003 · BR-IP-ORD-028 / BR-IP-ORD-029；AS-OP-DOR-003 · BR-ORD-032）。
 *
 * <p>嘱托识别双锚：{@code category_enum=52}
 *（{@link ServiceRequestCategoryConstants#SERVICE_CATEGORY_ENTRUST}）或活动定义名称精确为
 * {@link GeneralSharedConstants#ENTRUST_ACTIVITY_NAME}；住院正文 trim 后非空，否则抛
 * {@link DoctorStationAdviceConstants#EX_IP_ORD_306}；门诊命中嘱托抛
 * {@link DoctorStationAdviceConstants#EX_ORD_308}。</p>
 *
 * <p>出院带药：{@link TherapyType#DISCHARGE_MEDICATION} 仅允许药品 Request，诊疗行（含嘱托）拒绝。</p>
 *
 * <p>执行侧（护士站）：嘱托无视医生开立 {@code rate_code}，整单仅执行一次（与临嘱单槽同构，
 * {@code BR-IP-EXE-042} / {@code BR-IP-ORD-028⑦}）；频次字段仅展示。</p>
 *
 * <p>落点：{@code application.shared.advice}（与 {@link InpatientNursingOrderGate} 同包；
 * 禁止新增 {@code com.zxplat.plugin.*}）。</p>
 */
@Component
public class InpatientEntrustOrderGateSupport {

    /**
     * 是否为嘱托活动（类目 52 或目录名「嘱托」）。
     *
     * @param categoryEnum 活动定义 / ServiceRequest {@code category_code}/{@code category_enum}
     * @param activityName 活动定义名称（可空）
     * @return true 表示须按嘱托规则校验正文
     */
    public boolean isEntrustActivity(String categoryEnum, String activityName) {
        // 类目 52 为术语「嘱托」锚点（DM-WKF-004 §4.3 / BR-IP-ORD-028⑥）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST.equals(StringUtils.trimToNull(categoryEnum))) {
            return true;
        }
        // 目录名精确「嘱托」为第二识别锚点，禁止仅靠备注或 type_enum 推断
        return GeneralSharedConstants.ENTRUST_ACTIVITY_NAME.equals(StringUtils.trimToNull(activityName));
    }

    /**
     * 诊疗医嘱列表展示名：嘱托行优先 {@code entrustContent}（BR-IP-ORD-028④）；否则回落 catalog 名称。
     *
     * <p>住院医生站、护士站医嘱校对/执行/划价父医嘱等读路径须与本方法同源，禁止列表只显示目录名「嘱托」。</p>
     *
     * @param categoryEnum       ServiceRequest {@code category_enum}
     * @param catalogDisplayName 活动定义/catalog 名称（亦作嘱托第二识别锚）
     * @param entrustContent     嘱托正文（{@code wkf_service_request.entrust_content}）
     * @return 列表内容列展示名；非嘱托或无正文时回落 {@code catalogDisplayName}
     */
    public String resolveServiceAdviceDisplayName(
        String categoryEnum,
        String catalogDisplayName,
        String entrustContent) {
        // 嘱托：内容列优先医生录入正文（BR-IP-ORD-028④）
        if (isEntrustActivity(categoryEnum, catalogDisplayName)) {
            String content = StringUtils.trimToNull(entrustContent);
            if (content != null) {
                return content;
            }
        }
        return catalogDisplayName;
    }

    /**
     * 嘱托行 CREATE/UPDATE：{@code entrustContent} trim 后必填（BR-IP-ORD-028③）。
     *
     * @param categoryEnum   类目 code
     * @param activityName   活动名称
     * @param entrustContent 医生录入嘱托正文
     * @param errorCode      业务语义码（通常 {@link DoctorStationAdviceConstants#EX_IP_ORD_306}）
     * @param message        用户提示
     * @throws BusinessException 命中嘱托且正文空/空白时抛出
     */
    public void requireEntrustContentIfEntrustActivity(
        String categoryEnum,
        String activityName,
        String entrustContent,
        String errorCode,
        String message
    ) {
        // 非嘱托诊疗行不强制正文（常规备注嘱托可选）
        if (!isEntrustActivity(categoryEnum, activityName)) {
            return;
        }
        // 嘱托正文为医嘱内容真源；空/空白拒绝落库，避免列表只显示目录名「嘱托」
        if (StringUtils.trimToNull(entrustContent) == null) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 出院带药 therapy 禁止开立/改写诊疗行（含嘱托/申请单）（BR-IP-ORD-029②）。
     *
     * @param therapyEnum 行 therapy code（{@link TherapyType#getCode()}）
     * @param errorCode   业务语义码（通常 {@code SERVICE_REQUEST_INVALID_ARGUMENT}）
     * @param message     用户提示
     * @throws BusinessException therapy 为 {@link TherapyType#DISCHARGE_MEDICATION} 时抛出
     */
    public void assertNotDischargeMedicationForServiceLine(
        String therapyEnum,
        String errorCode,
        String message
    ) {
        // 出院带药仅药品 Request；同批诊疗/嘱托行一律拒绝（不因皮试/用法软约定而豁免本条）
        if (TherapyType.DISCHARGE_MEDICATION.getCode().equals(StringUtils.trimToNull(therapyEnum))) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 门诊禁开嘱托：命中类目 52 或目录名「嘱托」即拒绝（BR-ORD-032 / BR-IP-ORD-028②）。
     *
     * @param categoryEnum 活动定义 / ServiceRequest 类目 code
     * @param activityName 活动定义名称（可空）
     * @throws BusinessException 命中嘱托时抛 {@link DoctorStationAdviceConstants#EX_ORD_308}
     */
    public void assertNotEntrustForOutpatient(String categoryEnum, String activityName) {
        // 门诊目录/写路径均不得落嘱托；住院侧另走 requireEntrustContentIfEntrustActivity
        if (isEntrustActivity(categoryEnum, activityName)) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_ORD_308,
                DoctorStationAdviceConstants.MSG_OUTPATIENT_ENTRUST_FORBIDDEN);
        }
    }
}
