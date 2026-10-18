/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 生成医保报文 msgid。
 *
 * @author ZXPlat CHS */
public final class MsgIdUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String getMsgId(String fixmedinsCode) {
        String time = LocalDateTime.now().format(FMT);
        int r = ThreadLocalRandom.current().nextInt(1000, 10000);
        return fixmedinsCode + time + r;
    }

    private MsgIdUtil() {
    }
}
