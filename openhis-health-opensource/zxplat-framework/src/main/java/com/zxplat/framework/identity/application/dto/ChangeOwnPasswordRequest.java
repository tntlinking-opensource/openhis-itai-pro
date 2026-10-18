/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户自助修改密码请求。
 * <p>须校验旧密码后才写入新哈希；用户 ID 取自登录会话，不由前端传入，避免越权改他人密码。</p>
 */
@Data
public class ChangeOwnPasswordRequest {

    /**
     * 当前密码（明文，仅传输校验用）
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * 新密码（明文，落库前由应用层 BCrypt 编码）
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码长度至少6位")
    private String newPassword;
}
