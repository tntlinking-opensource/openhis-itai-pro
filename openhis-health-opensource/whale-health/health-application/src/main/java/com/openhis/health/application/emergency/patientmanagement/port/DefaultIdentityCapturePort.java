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

package com.openhis.health.application.emergency.patientmanagement.port;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 默认身份采集：支持 MANUAL/ID_CARD_READER 简单解析（18 位证号或原文 trim）。
 */
@Component
public class DefaultIdentityCapturePort implements IdentityCapturePort {

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(GeneralSharedConstants.ID_CARD_REGEX);

    @Override
    public IdentityCaptureResult normalize(String channelCode, String rawPayload) {
        IdentityCaptureResult result = new IdentityCaptureResult();
        if (StringUtils.isBlank(rawPayload)) {
            result.setHintMessage("读卡内容为空");
            return result;
        }
        String trimmed = rawPayload.trim();
        if (ID_CARD_PATTERN.matcher(trimmed).matches()) {
            result.setNormalizedIdCard(trimmed.toUpperCase());
            result.setHintMessage("已识别身份证号，请核对姓名等信息");
            return result;
        }
        String digits = trimmed.replaceAll("\\s+", "");
        if (digits.length() >= 15 && ID_CARD_PATTERN.matcher(digits).matches()) {
            result.setNormalizedIdCard(digits.toUpperCase());
            result.setHintMessage("已识别身份证号，请核对姓名等信息");
            return result;
        }
        result.setNormalizedIdCard(trimmed);
        result.setHintMessage("未能严格校验证号格式，请人工核对后保存");
        return result;
    }
}
