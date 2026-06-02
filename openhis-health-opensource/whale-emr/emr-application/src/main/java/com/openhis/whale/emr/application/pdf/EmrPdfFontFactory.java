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

package com.openhis.whale.emr.application.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 加载中文 PDF 字体；优先 classpath TTF，回退内置 Helvetica。
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
