/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 住院护理级别医嘱只读门控（AS-IP-DOR-003 BR-IP-ORD-026、AS-IP-NUR-004 BR-IP-EXE-027）。
 *
 * <p>跨聚合读经 {@link ServiceRequestQueryService}（B.6.4）；写不变量仍由 {@code ServiceRequestDomainService} 收口。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientNursingOrderGate {

    /** 诊疗申请只读查询（护理级别并发与类目判定）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 护理级别（category {@code 26}）须为长期医嘱。
     *
     * @param categoryEnum 诊疗类目持久化码（活动定义 category_code）
     * @param therapyEnum  长临 code（{@link TherapyType}）
     * @param errorCode    业务语义码
     * @param message      用户提示
     * @throws BusinessException 临嘱护理级别时抛出
     */
    public void assertNursingLevelLongTermOnly(
        String categoryEnum,
        String therapyEnum,
        String errorCode,
        String message
    ) {
        // 非护理级别行：不约束长临，检验/嘱托等不受本 Gate 影响
        if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING.equals(StringUtils.trimToNull(categoryEnum))) {
            return;
        }
        // 护理级别须长期：临嘱护理级别会导致滚费与换级口径混乱（BR-IP-ORD-026①）
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 同就诊仅允许一条有效护理级别医嘱；换级须先停旧再开新。
     *
     * <p>仅当当前行类目为护理级别（{@code categoryEnum=26}）时校验；其它诊疗（检验/检查/嘱托等）
     * 不因患者已有护理级别而被拒（BR-IP-ORD-026②）。</p>
     *
     * @param categoryEnum            当前行诊疗类目（活动定义 category_code / SR.category_enum）
     * @param encounterId             就诊主键
     * @param excludeServiceRequestId 排除的护理级别医嘱主键（修改/签发自身）；新建传 null
     * @param errorCode               业务语义码
     * @param message                 用户提示
     * @throws BusinessException 当前行为护理级别且存在其它有效护理级别医嘱时抛出
     */
    public void assertNoConcurrentNursingLevelOrder(
        String categoryEnum,
        Long encounterId,
        Long excludeServiceRequestId,
        String errorCode,
        String message
    ) {
        // 非护理级别行：不跑并发 Gate，避免签发检验/嘱托等被已有护理级别误拦
        if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING.equals(StringUtils.trimToNull(categoryEnum))) {
            return;
        }
        // 同就诊已有其它有效护理级别：须先停旧再开新，禁止并行换级
        if (serviceRequestQueryService.existsConcurrentNursingLevelOrder(encounterId, excludeServiceRequestId)) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 护理级别医嘱不经护士站执行/不执行 Tab（仅校对；滚费由自动计费任务驱动）。
     *
     * @param serviceCategoryEnum 诊疗类目 code（{@code category_enum}）
     * @param errorCode           业务语义码
     * @param message             用户提示
     * @throws BusinessException 命中护理级别类目时抛出
     */
    public void assertNotExecutableAtNurseStation(String serviceCategoryEnum, String errorCode, String message) {
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING.equals(StringUtils.trimToNull(serviceCategoryEnum))) {
            throw new BusinessException(errorCode, message);
        }
    }
}
