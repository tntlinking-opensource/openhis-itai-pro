/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.assembler.financial;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * payment {@code enterer_id} 读侧匹配：Practitioner 主键 + 绑定用户主键（存量兼容）。
 *
 * <p>挂号/收费写路径以 {@code adm_practitioner.id} 落库为主；历史或异常数据可能仍为
 * {@code whale_users.id}。日结与费用明细按经办人筛选时须同时命中两类标识。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentEntererMatchSupport {

    private final PractitionerQueryService practitionerQueryService;

    /**
     * 解析经办人 Practitioner 对应的 payment {@code enterer_id} 白名单。
     *
     * @param cashierPractitionerId 经办人 Practitioner 主键
     * @return 去重后的 enterer 匹配主键；入参为空时返回空列表
     */
    public List<Long> resolveEntererMatchIds(Long cashierPractitionerId) {
        if (cashierPractitionerId == null) {
            return List.of();
        }
        LinkedHashSet<Long> matchIds = new LinkedHashSet<>();
        matchIds.add(cashierPractitionerId);
        Practitioner practitioner = this.practitionerQueryService.findById(cashierPractitionerId);
        if (practitioner != null && practitioner.getUserId() != null) {
            // 存量 payment.enterer_id 可能存系统用户主键
            matchIds.add(practitioner.getUserId());
        }
        return new ArrayList<>(matchIds);
    }
}
