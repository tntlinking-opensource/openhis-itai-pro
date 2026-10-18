# 吉林工伤接口规范（参考）

> 真源 Word / 转换产物均在本目录；代码仓 `zxplat-plugin` 不再保留副本。

## 文件

| 文件 | 说明 |
|------|------|
| [吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md](./吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md) | **AI 可读合订本**（Pandoc 自 `.doc` 转换） |
| `吉林省工伤保险信息系统协议机构接口开发手册-V1.3.docx` | 中间格式（LibreOffice 自 `.doc` 导出） |
| `核4吉林省工伤保险信息系统协议机构接口开发手册-V1.3.doc` | 原始 Word 文档 |
| `media/` | 流程图等图片资源 |

## 与省医保文档关系

- 省医保基线规范：[../医疗保障信息平台定点基线版医药机构接口规范-AI.md](../医疗保障信息平台定点基线版医药机构接口规范-AI.md)
- 工伤方案设计：[../../scheme/workinjury/README.md](../../scheme/workinjury/README.md)

## 再生成

```bash
# 需 LibreOffice（soffice）与 pandoc
soffice --headless --convert-to docx --outdir . "核4吉林省工伤保险信息系统协议机构接口开发手册-V1.3.doc"
pandoc "吉林省工伤保险信息系统协议机构接口开发手册-V1.3.docx" -t markdown --extract-media=./media -o "吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md"
# 将 md 内图片路径改为相对路径 ./media/...
```
