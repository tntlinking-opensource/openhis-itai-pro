/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.practitioner.service;

import com.zxplat.application.basic.practitioner.dto.PractitionerProfileDto;
import com.zxplat.application.basic.practitioner.request.SavePractitionerSignatureRequest;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerDomainService;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PractitionerLifecycleConstants;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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

    /** 医务人员读侧：按 id 加载主档与签名字段。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 医务人员写侧：更新签名图等主档属性。 */
    private final PractitionerDomainService practitionerDomainService;
    /** 写路径操作人解析：未登录时阻断签名保存。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 加载医务人员主档（含签名图），供岗位工作台「医生维护」区展示。
     *
     * @param practitionerId 医务人员主键
     * @return 含签名存在标志与 Base64 内容的档案 DTO
     * @throws ValidationException 主键为空或记录不存在时
     */
    public PractitionerProfileDto getProfile(Long practitionerId) {
        // Gate：主键非空
        if (practitionerId == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        // Pre：加载医务人员主档
        Practitioner p = practitionerQueryService.findById(practitionerId);
        // Branch：记录不存在时阻断
        if (p == null) {
            throw new ValidationException("医务人员不存在");
        }
        // Asm：映射档案 DTO（含签名存在标志）
        PractitionerProfileDto dto = new PractitionerProfileDto();
        dto.setPractitionerId(p.getId());
        dto.setName(p.getName());
        dto.setBusNo(p.getBusNo());
        dto.setStatusEnum(p.getStatusEnum());
        String sig = p.getSignature();
        // 前端据此决定是否展示「已有签名」占位与编辑区
        dto.setHasSignature(StringUtils.isNotEmpty(sig));
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
        // Gate：医务人员标识非空
        if (request == null || request.getPractitionerId() == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        String payload = request.getSignatureImage();
        // Gate：非空签名须控制体积，避免超大 Base64 拖垮库表
        if (StringUtils.isNotEmpty(payload)
            && payload.length() > PractitionerLifecycleConstants.SIGNATURE_MAX_LENGTH) {
            throw new ValidationException("签名图片过大，请压缩后重试（建议宽不超过 400px）");
        }
        // Cmd：更新签名图（空载荷表示清除）
        practitionerDomainService.updateSignature(
            request.getPractitionerId(),
            payload,
            currentPractitionerResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录后再操作"));
    }
}
