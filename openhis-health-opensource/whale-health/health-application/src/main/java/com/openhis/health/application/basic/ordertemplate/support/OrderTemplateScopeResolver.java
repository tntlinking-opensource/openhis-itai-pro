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

package com.openhis.health.application.basic.ordertemplate.support;

import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.OrderTemplateBinding;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组套模板绑定维度 → 归属对象 id 解析（BR-BAS-OTL-008）。
 *
 * <p>PERSONAL → 当前医务人员；DEPARTMENT → 当前会话科室；HOSPITAL → 租户级医院根组织。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderTemplateScopeResolver {

    /** 医务人员解析（个人归属）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 会话/管辖科室解析（科室归属）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 组织读侧：解析全院根组织。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 按绑定类型解析归属对象 id；请求已传 objectId 时直接采用。
     *
     * @param bindingEnum 绑定类型 code
     * @param requestObjectId 请求携带的归属 id，可空
     * @return 归属对象 id
     * @throws BusinessException 绑定非法或上下文缺失时
     */
    public Long resolveObjectId(String bindingEnum, Long requestObjectId) {
        if (requestObjectId != null) {
            return requestObjectId;
        }
        OrderTemplateBinding binding = EnumUtil.fromCode(OrderTemplateBinding.class, bindingEnum);
        if (binding == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "绑定类型不合法");
        }
        // 个人归属：缺省取当前登录医务人员
        if (binding == OrderTemplateBinding.PERSONAL) {
            return currentPractitionerResolver.requirePractitionerId(
                ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
                "请先绑定医务人员后再保存个人组套");
        }
        // 科室归属：缺省取当前会话科室
        if (binding == OrderTemplateBinding.DEPARTMENT) {
            return managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
                ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT,
                "请先选择科室");
        }
        // 全院归属：取租户内启用医院根组织
        if (binding == OrderTemplateBinding.HOSPITAL) {
            return resolveHospitalRootOrganizationId();
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "绑定类型不合法");
    }

    /**
     * 解析租户级医院根组织 id（type=HOSPITAL 且启用）。
     *
     * @return 医院组织 id
     * @throws BusinessException 未找到医院根组织时
     */
    private Long resolveHospitalRootOrganizationId() {
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
                continue;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(organization.getTypeEnum())) {
                return organization.getId();
            }
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "未找到全院根组织，无法保存全院组套");
    }
}
