# EMR PDF 字体

将支持中文的 TTF/OTF 字体放入此目录，例如：

- `SimSun.ttf`（宋体）
- `NotoSansSC-Regular.otf`

渲染器按 `EmrPdfFontFactory` 中的路径顺序加载；若未放置字体文件，将回退到内置字体（中文可能无法正确显示）。
