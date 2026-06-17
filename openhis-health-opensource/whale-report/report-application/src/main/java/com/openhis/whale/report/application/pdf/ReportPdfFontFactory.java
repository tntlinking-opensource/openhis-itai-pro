package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
public class ReportPdfFontFactory {

    private static final String[] FONT_RESOURCE_PATHS = {
            "/emr/fonts/SimSun.ttf",
            "/emr/fonts/NotoSansSC-Regular.otf",
            "/emr/fonts/simsun.ttf",
            "/report/fonts/SimSun.ttf",
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
            try (InputStream in = ReportPdfFontFactory.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                byte[] bytes = in.readAllBytes();
                String name = path.substring(path.lastIndexOf('/') + 1);
                log.info("Report PDF 使用字体: {}", path);
                return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null);
            } catch (Exception e) {
                log.warn("加载字体失败 {}: {}", path, e.getMessage());
            }
        }
        try {
            return BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            log.warn("STSongStd-Light 不可用: {}", e.getMessage());
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("无法加载 PDF 字体", ex);
        }
    }
}
