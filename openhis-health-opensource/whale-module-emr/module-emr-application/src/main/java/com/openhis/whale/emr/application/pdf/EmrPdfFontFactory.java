/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;

/**
 * 加载中文 PDF 字体；优先 classpath 宋体 TTF，其次本机 SimSun，再回退 Adobe STSong。
 * <p>
 * STSongStd-Light + UniGB-UCS2-H 把 ASCII 按汉字全角宽走字，半角 {@code mmHg} 会印成
 * {@code m m H g}。此时西文改走 Times（比例宽），汉字仍走宋体/STSong。
 * 正式打印应对齐 PrintConfig.font.family=SimSun：可将 SimSun.ttf 放入
 * {@code module-emr-application/src/main/resources/emr/fonts/SimSun.ttf}。
 * </p>
 */
@Slf4j
@Component
public class EmrPdfFontFactory {

    private static final String[] FONT_RESOURCE_PATHS = {
            "/emr/fonts/SimSun.ttf",
            "/emr/fonts/NotoSansSC-Regular.otf",
            "/emr/fonts/simsun.ttf",
    };

    /**
     * 本机宋体（TTC 须带面索引）。Windows 病历书写用系统 SimSun，与编辑器一致。
     */
    private static final String[] OS_FONT_SPECS = {
            "C:\\Windows\\Fonts\\simsun.ttc,0",
            "C:\\Windows\\Fonts\\simsun.ttc,1",
            "C:\\Windows\\Fonts\\SimSun.ttf",
            "/usr/share/fonts/truetype/arphic/uming.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
    };

    private volatile BaseFont cachedBaseFont;
    private volatile BaseFont cachedLatinBaseFont;
    /**
     * true：当前 CJK 字体拉丁字母按全角宽（约 1000/em），写英文必须拆到西文字体。
     */
    private volatile boolean cjkLatinIsFullEm = true;

    /**
     * 中文（宋体/STSong）BaseFont；首次调用时加载并缓存。
     *
     * @return CJK 字体
     */
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

    /**
     * 正文字体：宋体/STSong + 指定字号样式。英文走字宽由 {@link #appendText} 再拆。
     *
     * @param sizePt 字号（pt）
     * @param style  {@link Font#NORMAL} / BOLD 等
     * @return PDF Font
     */
    public Font bodyFont(float sizePt, int style) {
        return new Font(getBaseFont(), sizePt, style);
    }

    /**
     * 把一段文本写入 Phrase。CJK 字体若把 ASCII 画成全角宽，则按字符拆成 Times + 宋体两路 Chunk。
     *
     * @param phrase         目标段落或单元格
     * @param text           原文（半角英文、汉字、NBSP 均可）
     * @param styleTemplate  字号/加粗/下划线等；BaseFont 用工厂内的中英两套
     */
    public void appendText(Phrase phrase, String text, Font styleTemplate) {
        if (phrase == null || text == null || text.isEmpty()) {
            return;
        }
        addChunks(phrase, mixedPhrase(text, styleTemplate), false);
    }

    /**
     * 混排后的 Phrase，供页眉页脚 {@code ColumnText.showTextAligned} 使用。
     *
     * @param text           原文
     * @param styleTemplate  字号与样式
     * @return 可直接绘制的 Phrase；空文本则为空 Phrase
     */
    public Phrase phrase(String text, Font styleTemplate) {
        Phrase p = new Phrase();
        appendText(p, text, styleTemplate);
        return p;
    }

    /**
     * 把文本插到 Phrase 开头（如列表「1. 」前缀）。混排规则同 {@link #appendText}。
     *
     * @param phrase         目标段落
     * @param text           前缀原文
     * @param styleTemplate  字号与样式
     */
    public void prependText(Phrase phrase, String text, Font styleTemplate) {
        if (phrase == null || text == null || text.isEmpty()) {
            return;
        }
        addChunks(phrase, mixedPhrase(text, styleTemplate), true);
    }

    /**
     * 只追加 Chunk，禁止 {@code Phrase.add(Phrase)}：空段会把 FontSelector 默认 leading 盖掉正文 1.5 倍行距。
     *
     * @param target  目标段落
     * @param mixed   FontSelector 或单字体拆出的 Chunk 列表
     * @param prepend true 插到段首
     */
    private static void addChunks(Phrase target, Phrase mixed, boolean prepend) {
        if (mixed == null || mixed.isEmpty()) {
            return;
        }
        int insertAt = 0;
        // OpenPDF Phrase 为 ArrayList<Element>，须按 Element 遍历/add，不能传 Object
        for (Element element : mixed) {
            if (prepend) {
                target.add(insertAt, element);
                insertAt++;
            } else {
                target.add(element);
            }
        }
    }

    /**
     * 按当前中文字体是否「拉丁全角宽」决定：整段宋体，或 FontSelector 中英拆分。
     *
     * @param text           原文
     * @param styleTemplate  字号与样式
     * @return 仅含 Chunk 的 Phrase，由 {@link #addChunks} 逐个拷贝，避免整 Phrase add 覆盖 leading
     */
    private Phrase mixedPhrase(String text, Font styleTemplate) {
        // 先加载 CJK，才能判断拉丁是否全角宽
        getBaseFont();
        float size = 12f;
        int style = Font.NORMAL;
        if (styleTemplate != null) {
            if (styleTemplate.getSize() > 0f) {
                size = styleTemplate.getSize();
            }
            style = styleTemplate.getStyle();
        }
        // 宋体 TTF 拉丁已是比例宽时不必拆字体，与编辑器同一套 SimSun
        if (!cjkLatinIsFullEm) {
            Phrase phrase = new Phrase();
            phrase.add(new Chunk(text, new Font(getBaseFont(), size, style)));
            return phrase;
        }
        FontSelector selector = new FontSelector();
        // 西文先匹配：Times 比例宽；汉字 Times 没有，落到第二套 CJK
        Font latin = new Font(getLatinBaseFont(), size, style);
        Font cjk = new Font(getBaseFont(), size, style);
        if (styleTemplate != null && styleTemplate.getColor() != null) {
            latin.setColor(styleTemplate.getColor());
            cjk.setColor(styleTemplate.getColor());
        }
        selector.addFont(latin);
        selector.addFont(cjk);
        return selector.process(text);
    }

    /**
     * 西文伴随字体（Times）。STSong 把 mmHg 拉成全角宽时用它画 ASCII。
     *
     * @return Times-Roman；不可用则 Helvetica
     */
    private BaseFont getLatinBaseFont() {
        if (cachedLatinBaseFont != null) {
            return cachedLatinBaseFont;
        }
        synchronized (this) {
            if (cachedLatinBaseFont != null) {
                return cachedLatinBaseFont;
            }
            cachedLatinBaseFont = loadLatinBaseFont();
            return cachedLatinBaseFont;
        }
    }

    /**
     * 加载 Times；失败再 Helvetica。二者 ASCII 均为比例宽。
     *
     * @return 西文 BaseFont
     */
    private BaseFont loadLatinBaseFont() {
        try {
            return BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            log.warn("Times-Roman 不可用，西文回退 Helvetica: {}", e.getMessage());
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化 PDF 西文字体", ex);
        }
    }

    /**
     * 按 classpath → 本机 SimSun → STSong → Helvetica 加载中文字体，并记录拉丁是否全角宽。
     *
     * @return CJK BaseFont
     */
    private BaseFont loadBaseFont() {
        BaseFont loaded = loadClasspathCjkFont();
        if (loaded == null) {
            loaded = loadOsCjkFont();
        }
        if (loaded == null) {
            loaded = loadBuiltinCjkOrLatin();
        }
        rememberLatinAdvance(loaded);
        return loaded;
    }

    /**
     * 从 classpath 读 SimSun/Noto。
     *
     * @return 成功则字体，否则 null
     */
    private BaseFont loadClasspathCjkFont() {
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
        return null;
    }

    /**
     * 读本机宋体 TTC/TTF（Windows Fonts 等）。文件不存在则跳过。
     *
     * @return 成功则字体，否则 null
     */
    private BaseFont loadOsCjkFont() {
        for (String spec : OS_FONT_SPECS) {
            String filePath = ttcFilePath(spec);
            File file = new File(filePath);
            if (!file.isFile()) {
                continue;
            }
            try {
                log.info("EMR PDF 使用本机字体: {}", spec);
                return BaseFont.createFont(spec, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                log.warn("加载本机字体失败 {}: {}", spec, e.getMessage());
            }
        }
        return null;
    }

    /**
     * OpenPDF TTC 写法是 {@code 路径,面索引}；探测文件是否存在时要去掉索引。
     *
     * @param spec 字体路径或 {@code simsun.ttc,0}
     * @return 磁盘路径
     */
    private static String ttcFilePath(String spec) {
        if (spec == null) {
            return "";
        }
        int comma = spec.lastIndexOf(',');
        if (comma <= 0) {
            return spec;
        }
        String after = spec.substring(comma + 1);
        if (after.isEmpty()) {
            return spec.substring(0, comma);
        }
        for (int i = 0; i < after.length(); i++) {
            if (!Character.isDigit(after.charAt(i))) {
                return spec;
            }
        }
        return spec.substring(0, comma);
    }

    /**
     * Adobe 内置 STSong；再不行只能 Helvetica（中文会缺字）。
     *
     * @return 内置 CJK 或 Helvetica
     */
    private BaseFont loadBuiltinCjkOrLatin() {
        try {
            log.warn("未找到 SimSun.ttf，回退 STSongStd-Light；ASCII 将按全角宽，英文改走 Times");
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

    /**
     * 量 'i'/'m' 的 em 宽：二者都 ≥900 视为 CID 全角拉丁，打印英文必须拆西文字体。
     *
     * @param font 刚加载的 CJK BaseFont
     */
    private void rememberLatinAdvance(BaseFont font) {
        if (font == null) {
            cjkLatinIsFullEm = true;
            return;
        }
        int iWidth = 0;
        int mWidth = 0;
        try {
            iWidth = font.getWidth('i');
            mWidth = font.getWidth('m');
            // 量不到时按全角处理，避免 STSong 漏判
            if (iWidth <= 0 || mWidth <= 0) {
                cjkLatinIsFullEm = true;
            } else {
                cjkLatinIsFullEm = iWidth >= 900 && mWidth >= 900;
            }
        } catch (Exception e) {
            cjkLatinIsFullEm = true;
        }
        if (cjkLatinIsFullEm) {
            log.warn("CJK 字体拉丁字母按全角宽走字（i={} m={}），英文将改走 Times，避免 mmHg 被拉成 m m H g",
                    iWidth, mWidth);
        }
    }
}
