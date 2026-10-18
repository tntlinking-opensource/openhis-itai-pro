# -*- coding: utf-8 -*-
"""生成《病历 PDF A4 改 A5 修改方案》PDF。"""
import shutil
from pathlib import Path

from fpdf import FPDF

OUT_DIR = Path(__file__).resolve().parent
OUT_PDF_CN = OUT_DIR / "病历PDF改A5修改方案.pdf"
OUT_PDF = OUT_DIR / "emr-pdf-a4-to-a5-plan.pdf"
FONT_PATH = Path("C:/Windows/Fonts/simsun.ttc")


class PlanPDF(FPDF):
    def header(self):
        if self.page_no() > 1:
            self.set_font("SimSun", size=9)
            self.set_text_color(120, 120, 120)
            self.cell(0, 8, "病历 PDF A4 → A5 修改方案", align="R")
            self.ln(4)

    def footer(self):
        self.set_y(-15)
        self.set_font("SimSun", size=9)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f"第 {self.page_no()} 页", align="C")


def add_title(pdf: PlanPDF, text: str, level: int = 1):
    sizes = {1: 18, 2: 14, 3: 12}
    pdf.set_font("SimSun", size=sizes.get(level, 12))
    pdf.set_text_color(20, 20, 20)
    pdf.ln(4 if level > 1 else 0)
    pdf.multi_cell(0, 8, text)
    pdf.ln(2)


def add_para(pdf: PlanPDF, text: str):
    pdf.set_font("SimSun", size=11)
    pdf.set_text_color(40, 40, 40)
    pdf.multi_cell(0, 7, text)
    pdf.ln(2)


def add_bullet(pdf: PlanPDF, text: str):
    pdf.set_font("SimSun", size=11)
    pdf.set_text_color(40, 40, 40)
    pdf.multi_cell(0, 7, f"• {text}")
    pdf.ln(1)


def add_code(pdf: PlanPDF, text: str):
    pdf.set_font("SimSun", size=10)
    pdf.set_text_color(30, 60, 120)
    pdf.set_fill_color(245, 247, 250)
    pdf.multi_cell(0, 6, text, fill=True)
    pdf.ln(3)


def build_pdf() -> Path:
    pdf = PlanPDF()
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.add_font("SimSun", "", str(FONT_PATH))
    pdf.add_page()

    add_title(pdf, "病历 PDF 纸张规格修改方案", 1)
    add_title(pdf, "A4 标准 → A5", 2)
    add_para(
        pdf,
        "文档版本：v1.0    编制日期：2026-08-24    适用系统：Whale OpenHIS EMR 文书模块",
    )
    pdf.ln(4)

    add_title(pdf, "一、背景与目标", 2)
    add_para(
        pdf,
        "当前病历文书 PDF 由 whale-module-emr 的 OpenPDF 渲染链路生成，默认纸张为 A4（210×297 mm）。"
        "业务需求将病历 PDF 改为 A5（148×210 mm），需同步调整后端渲染、前端编辑器画布与打印入参，保证编辑预览与纸质输出一致。",
    )

    add_title(pdf, "二、现状分析", 2)
    add_bullet(pdf, "后端默认：PrintConfig.PaperConfig 宽 210mm、高 297mm，纵向 portrait。")
    add_bullet(pdf, "渲染入口：DocumentAppService.generatePdf → OpenPdfRenderer.buildPageSize。")
    add_bullet(pdf, "前端编辑器：simple-editor.scss 中 --simple-editor-a4-w / a4-min-h 为 210mm / 297mm。")
    add_bullet(pdf, "前端打印：generateDocumentPdfApi 仅传 margins、footer，未传 paper 字段。")
    add_bullet(pdf, "病案首页等独立 PDF（如 HomepageOpenPdfRenderer）硬编码 PageSize.A4，不在本方案主链路内。")

    add_title(pdf, "三、纸张规格对照", 2)
    add_code(pdf, "A4 纵向：宽 210 mm × 高 297 mm\nA5 纵向：宽 148 mm × 高 210 mm（约为 A4 面积一半）")

    add_title(pdf, "四、修改方案", 2)

    add_title(pdf, "方案 A：全院统一改为 A5", 3)
    add_para(pdf, "适用：全院所有 EMR 文书 PDF 均使用 A5。")
    add_bullet(pdf, "后端 PrintConfig.java：PaperConfig 默认值改为 148 / 210；新增 a5Portrait()；PrintConfig 默认 paper 改为 a5Portrait()。")
    add_bullet(pdf, "前端 simple-editor.scss：--simple-editor-a4-w 改为 148mm，--simple-editor-a4-min-h 改为 210mm。")
    add_bullet(pdf, "页边距：建议由 25.4/31.8mm 调整为 15~20mm，避免 A5 正文区过窄。")
    add_bullet(pdf, "部署：需同时发布后端 whale-module-emr 与前端 openhis-pro-ui。")

    add_title(pdf, "方案 B：按文书类型区分（推荐）", 3)
    add_para(pdf, "适用：仅门诊病历等特定类型用 A5，住院病历等仍保持 A4。")
    add_bullet(pdf, "后端：保持 PrintConfig 默认 A4，通过 generatePdf 请求的 printConfig.paper 覆盖。")
    add_bullet(pdf, "前端 document.ts：扩展 EmrPrintConfigPayload，增加 paper { widthMm, heightMm, orientation }。")
    add_bullet(pdf, "前端 medical-record-form.vue：在 buildPrintConfigFromPageMargins 中按 documentTypeCode 注入 A5 paper。")
    add_bullet(pdf, "前端编辑器：可为不同文书类型绑定不同 pagePaper 配置，或弹窗书写时动态切换画布宽高。")
    add_bullet(pdf, "模板维护：emr-template/edit-dialog.vue 同步扩展 printConfig 传参。")

    add_title(pdf, "五、涉及文件清单", 2)
    add_code(
        pdf,
        "【后端】\n"
        "whale-module-emr/.../pdf/config/PrintConfig.java\n"
        "whale-module-emr/.../pdf/OpenPdfRenderer.java（只读，逻辑无需改）\n"
        "whale-module-emr/.../pdf/PrintConfigResolver.java（只读）\n"
        "whale-module-emr/.../document/request/GeneratePdfRequest.java（已支持 printConfig）\n\n"
        "【前端】\n"
        "openhis-pro-ui/.../api/emr/document.ts\n"
        "openhis-pro-ui/.../components/outpatient/medical-record-form.vue\n"
        "openhis-pro-ui/.../views/basic/emr-template/edit-dialog.vue\n"
        "openhis-pro-ui/.../emrEditor/components/simple-editor/simple-editor.scss\n"
        "openhis-pro-ui/.../components/outpatient/emr-template-maintain-dialog.vue（可选）",
    )

    add_title(pdf, "六、接口示例（方案 B）", 2)
    add_code(
        pdf,
        "POST /api/app/document/generatePdf\n"
        "{\n"
        "  \"documentId\": \"...\",\n"
        "  \"previewMode\": false,\n"
        "  \"printConfig\": {\n"
        "    \"paper\": { \"widthMm\": 148, \"heightMm\": 210, \"orientation\": \"portrait\" },\n"
        "    \"margins\": { \"topMm\": 20, \"rightMm\": 20, \"bottomMm\": 20, \"leftMm\": 20 },\n"
        "    \"footer\": { \"enabled\": true, \"template\": \"第 {page} 页\" }\n"
        "  }\n"
        "}",
    )

    add_title(pdf, "七、测试要点", 2)
    add_bullet(pdf, "预览 PDF 页面尺寸是否为 148×210 mm（可用 Adobe/浏览器属性核对）。")
    add_bullet(pdf, "编辑器画布宽度与 PDF 正文宽度是否一致，避免换行差异。")
    add_bullet(pdf, "页眉页脚、戒烟提示等 footer 在 A5 下是否正常换行、不溢出。")
    add_bullet(pdf, "长文书多页分页是否正常；mergePdf 合并多份文书时纸张一致。")
    add_bullet(pdf, "系统打印对话框选择 A5 纸型，检查无缩放错位。")
    add_bullet(pdf, "门诊/住院、不同 documentTypeCode 在方案 B 下纸张是否符合预期。")

    add_title(pdf, "八、风险与合规提示", 2)
    add_bullet(
        pdf,
        "《病历书写基本规范》第三十二条要求打印病历统一纸张、字体、字号及排版格式，行业惯例多为 A4。全院改 A5 前须院方书面确认。",
    )
    add_bullet(pdf, "A5 可容纳正文减少，长病历页数增加，可能影响归档与打印成本。")
    add_bullet(pdf, "若打印机默认 A4，需在打印对话框手动选 A5 或配置默认纸型。")
    add_bullet(pdf, "病案首页、体温单等独立 PDF 模块需单独评估，本方案不自动覆盖。")

    add_title(pdf, "九、实施步骤建议", 2)
    add_bullet(pdf, "1. 与院方确认适用范围（全院 A5 或仅门诊 A5）。")
    add_bullet(pdf, "2. 确定 A5 页边距与字号是否维持 12pt 或略调。")
    add_bullet(pdf, "3. 按选定方案修改后端/前端代码。")
    add_bullet(pdf, "4. 在测试环境用真实病历模板回归预览、打印、合并 PDF。")
    add_bullet(pdf, "5. 协调信息科配置打印机默认纸型。")
    add_bullet(pdf, "6. 灰度上线，观察一周后全院推广。")

    add_title(pdf, "十、工作量估算", 2)
    add_code(
        pdf,
        "方案 A（全院 A5）：后端 0.5 人日 + 前端 1 人日 + 测试 1 人日 ≈ 2.5 人日\n"
        "方案 B（按类型）：后端 0.5 人日 + 前端 1.5 人日 + 测试 1.5 人日 ≈ 3.5 人日",
    )

    pdf.output(str(OUT_PDF))
    shutil.copy2(OUT_PDF, OUT_PDF_CN)
    return OUT_PDF, OUT_PDF_CN


if __name__ == "__main__":
    paths = build_pdf()
    for p in paths:
        print(f"Generated: {p}")
