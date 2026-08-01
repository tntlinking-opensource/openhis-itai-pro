/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 加载中文 PDF 字体；优先 classpath 宋体 TTF，其次 Noto，再回退 Adobe 内置中文字体。
 * 正式打印应对齐 PrintConfig.font.family=SimSun：将 SimSun.ttf 放入
 * {@code module-emr-application/src/main/resources/emr/fonts/SimSun.ttf}。
 */
@Slf4j
@Component
public class EmrPdfFontFactory {

    private static final String[] FONT_RESOURCE_PATHS = {
            "/emr/fonts/SimSun.ttf",
            "/emr/fonts/NotoSansSC-Regular.otf",
            "/emr/fonts/simsun.ttf",
    };

    private volatile BaseFont cachedBaseFont;

    public BaseFont getBaseFont() {
        if (cachedBaseFont != null) {
            return cachedBaseFont;
        }
        synchronized (this) {
            if (cachedBaseFont != null) {
                return cachedBaseFont;
            }
            cachedBaseFont = loadBaseFont();
            return cachedBaseFont;
        }
    }

    public Font bodyFont(float sizePt, int style) {
        return new Font(getBaseFont(), sizePt, style);
    }

    private BaseFont loadBaseFont() {
        for (String path : FONT_RESOURCE_PATHS) {
            try (InputStream in = EmrPdfFontFactory.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                byte[] bytes = in.readAllBytes();
                String name = path.substring(path.lastIndexOf('/') + 1);
                log.info("EMR PDF 使用字体: {}", path);
                return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null);
            } catch (Exception e) {
                log.warn("加载字体失败 {}: {}", path, e.getMessage());
            }
        }
        try {
            return BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            log.warn("STSongStd-Light 不可用，回退 Helvetica（中文可能无法显示）: {}", e.getMessage());
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化 PDF 字体", ex);
        }
    }
}
