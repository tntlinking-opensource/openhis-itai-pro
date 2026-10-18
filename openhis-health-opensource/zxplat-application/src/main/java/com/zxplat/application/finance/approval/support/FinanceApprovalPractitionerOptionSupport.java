/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.support;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.domain.share.constants.PractitionerOptionConstants;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 财务审批 init：制单人/审批人下拉装配。
 *
 * <p>制单人：在职医务人员全量（首版不按岗位收窄）；审批人：持有
 * {@link PlatformRoleCodeConstants#ROLE_ACCOUNTANT} 且绑定在职医务人员。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalPractitionerOptionSupport {

    private final PractitionerQueryService practitionerQueryService;
    private final UserAppService userAppService;

    /**
     * 制单人下拉：在职医务人员全量。
     *
     * @return Option；主数据异常由调用方吞并为空列表
     */
    public List<OptionDto> listApplicantOptions() {
        List<Practitioner> practitioners = this.listAllActivePractitioners();
        return this.toOptions(practitioners);
    }

    /**
     * 审批人下拉：财务登录角色绑定的在职医务人员。
     *
     * @return Option；未绑定人员时为空列表
     */
    public List<OptionDto> listApproverOptions() {
        List<Long> userIds =
            this.userAppService.listActiveUserIdsByRoleCode(PlatformRoleCodeConstants.ROLE_ACCOUNTANT);
        // 财务未绑定在职人员时下拉为空，不回落到已废弃的财务审批角色
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Practitioner> practitioners = this.practitionerQueryService.listActivePractitionersByBoundUserIds(userIds);
        return this.toOptions(practitioners);
    }

    /**
     * 分页拉取全部在职医务人员（不按岗位收窄）。
     *
     * @return 人员列表
     */
    private List<Practitioner> listAllActivePractitioners() {
        List<Practitioner> acc = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                "",
                PageRequest.of(
                    pageIndex,
                    PractitionerOptionConstants.OPTION_SCAN_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, FieldNameConstants.ID)));
            if (!page.hasContent()) {
                break;
            }
            for (Practitioner practitioner : page.getContent()) {
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                acc.add(practitioner);
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }
        return acc;
    }

    /**
     * 医务人员 → OptionDto（value 为 practitioner.id）。
     *
     * @param practitioners 人员列表
     * @return Option 列表
     */
    private List<OptionDto> toOptions(List<Practitioner> practitioners) {
        List<OptionDto> options = new ArrayList<>();
        if (practitioners == null || practitioners.isEmpty()) {
            return options;
        }
        long sort = 1L;
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            String label = practitioner.getName();
            if (StringUtils.isEmpty(label)) {
                continue;
            }
            options.add(new OptionDto(sort, String.valueOf(practitioner.getId()), label));
            sort = sort + 1L;
        }
        return options;
    }
}
