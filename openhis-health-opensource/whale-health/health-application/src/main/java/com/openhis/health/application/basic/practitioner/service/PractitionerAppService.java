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

package com.openhis.health.application.basic.practitioner.service;

import com.openhis.health.application.basic.practitioner.dto.PractitionerProfileDto;
import com.openhis.health.application.basic.practitioner.request.SavePractitionerSignatureRequest;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerDomainService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 医务人员主档应用服务：岗位工作台维护医生签名等。
 *
 * <p>读侧经 {@link PractitionerQueryService} 加载 {@code adm_practitioner}；写侧签名更新委托
 * {@link PractitionerDomainService}，操作人经 {@link CurrentOperatorResolver} 门控。</p>
 */
@Service
@RequiredArgsConstructor
public class PractitionerAppService {

    /** 签名 Base64 最大字符数（约 400KB 原图压缩后） */
    private static final int SIGNATURE_MAX_LENGTH = 512_000;

    /** 医务人员读侧：按 id 加载主档与签名字段。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 医务人员写侧：更新签名图等主档属性。 */
    private final PractitionerDomainService practitionerDomainService;
    /** 写路径操作人解析：未登录时阻断签名保存。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 加载医务人员主档（含签名图），供岗位工作台「医生维护」区展示。
     *
     * @param practitionerId 医务人员主键
     * @return 含签名存在标志与 Base64 内容的档案 DTO
     * @throws ValidationException 主键为空或记录不存在时
     */
    public PractitionerProfileDto getProfile(Long practitionerId) {
        if (practitionerId == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        Practitioner p = practitionerQueryService.findById(practitionerId);
        if (p == null) {
            throw new ValidationException("医务人员不存在");
        }
        PractitionerProfileDto dto = new PractitionerProfileDto();
        dto.setPractitionerId(p.getId());
        dto.setName(p.getName());
        dto.setBusNo(p.getBusNo());
        dto.setStatusEnum(p.getStatusEnum());
        String sig = p.getSignature();
        // 前端据此决定是否展示「已有签名」占位与编辑区
        dto.setHasSignature(StringUtils.isNotBlank(sig));
        dto.setSignatureImage(sig);
        return dto;
    }

    /**
     * 保存签名图到 {@code adm_practitioner.signature}（Base64 / data URL）。
     *
     * @param request 含医务人员 id 与签名载荷；空载荷表示清除签名
     * @throws ValidationException 主键为空、图片过大或未登录时
     */
    public void saveSignature(SavePractitionerSignatureRequest request) {
        if (request == null || request.getPractitionerId() == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        String payload = request.getSignatureImage();
        // 非空签名须控制体积，避免超大 Base64 拖垮库表与接口
        if (StringUtils.isNotBlank(payload) && payload.length() > SIGNATURE_MAX_LENGTH) {
            throw new ValidationException("签名图片过大，请压缩后重试（建议宽不超过 400px）");
        }
        practitionerDomainService.updateSignature(
            request.getPractitionerId(),
            payload,
            currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录后再操作"));
    }
}
