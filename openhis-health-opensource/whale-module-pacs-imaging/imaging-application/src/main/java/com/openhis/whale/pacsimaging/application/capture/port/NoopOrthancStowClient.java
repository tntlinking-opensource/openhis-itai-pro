/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.port;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Orthanc STOW 默认空实现：未开启 {@code orthanc.enabled} 时拒绝上传。
 */
@Component
@ConditionalOnProperty(
    name = "openhis.modules.pacs.imaging.orthanc.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopOrthancStowClient implements OrthancStowClient {

    @Override
    public StowResult store(byte[] dicomBytes) {
        throw new BusinessException(
            ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED,
            "Orthanc 未启用：请设置 openhis.modules.pacs.imaging.orthanc.enabled=true 并启动 deploy 栈");
    }
}
