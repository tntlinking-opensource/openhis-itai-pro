# 历史 EMR 模板迁移指南

> **样例模板**：入院记录（`apps/web-ele/template.json`）  
> **日期**：2026-08-22（页眉金标准以 2026-08-24 Skill 为准）  
> **用途**：批量刷历史模板前，先按本文档做**结构 P0**（假页眉去重）；节点类型 / 页眉 float / `heightMm:0` 以项目 Skill `emr-template-migration` 为准。  
> **关联**：`.cursor/skills/emr-template-migration/SKILL.md`；编辑器分页见 [EDITOR-GAP-DECISIONS.md](./EDITOR-GAP-DECISIONS.md) §1、§6。

---

## 1. 问题现象（以入院记录为例）

| 场景 | 表现 |
| --- | --- |
| **编辑器（L1）** | 分页处院徽/标题/患者条叠在一起；核实签名行漂到页顶；第 2 页出现两套页眉感（纸面 `pageChrome` 页脚 + 正文内重复块） |
| **打印预览（L2）** | 大体可读，但页眉重复、分页边界与屏上不一致；院徽 base64 在 JSON 内重复 3 次 |

**根因一句话**：历史模板用「正文里手动复制页眉 + `emrPageBreak`」模拟 Word 多页；当前编辑器已具备 **A4 软分页 + `pageChrome` 真页眉页脚**，两套机制叠加后屏上必乱，打印只是「碰巧还能排」。

---

## 2. 样例模板结构解剖

### 2.1 顶层块序列（迁移前）

| 序号 | 类型 | `attrs.name` | 说明 |
| --: | --- | --- | --- |
| 0 | paragraph | `hospitalTitle` | 院徽 + `orgName` |
| 1 | paragraph | `formTitle` | 「入院记录」 |
| 2 | table | `patientHeader` | 首页完整患者信息（7 行 × 2 列） |
| 3 | horizontalRule | — | 首页分隔线 |
| 4–12 | paragraph | 病史各段 | 主诉、现病史…中医望闻问切 |
| 13 | **emrPageBreak** | — | 硬分页符 |
| 14 | table | `verifyBlock` | 核实人签名（**应在第 1 页末，却在分页符后**） |
| 15–18 | paragraph/table/hr | `hospitalTitle`…`pageHeader` | **第 2 页重复页眉（应删除）** |
| 19–31 | paragraph/table | 体格检查…心界表 | 第 2 页正文 |
| 32 | **emrPageBreak** | — | 硬分页符 |
| 33–36 | paragraph/table/hr | `hospitalTitle`…`pageHeader` | **第 3 页重复页眉（应删除）** |
| 37–47 | paragraph | 腹诊…诊断 | 第 3 页正文 |
| 48 | table | `signBlock` | 医师签名区 |
| 49 | paragraph | — | 空段 |

### 2.2 统计特征（用于批量识别）

```
emrPageBreak          × 2
hospitalTitle         × 3  （仅保留 × 1）
formTitle             × 3  （仅保留 × 1）
patientHeader         × 1  （仅首页）
pageHeader            × 2  （迁移后删除，改 pageChrome 或接受不重复）
verifyBlock           × 1
horizontalRule        × 3  （删除重复后保留首页 1 条 + 必要时正文内 1 条）
emrDataElement        × 63（text 39 + multiline 24）
image（院徽 base64）  × 3  （迁移后保留 1 份）
```

### 2.3 历史模板的「假页眉」约定

| 命名块 | 作用 | 当前编辑器处理 |
| --- | --- | --- |
| `hospitalTitle` | 院徽 `image[title=hospitalLogo]` + `emrDataElement(orgName)` | 正文首段 + scss flex 居中；**不会**自动在第 2 页重复 |
| `formTitle` | 文书标题（如「入院记录」） | 同上 |
| `patientHeader` | 首页多行患者信息表 | 仅出现在插入位置 |
| `pageHeader` | 续页简表（姓名 + 住院号） | 历史模板在每次 `emrPageBreak` 后复制 |
| `verifyBlock` | 核实签名行 | 应在第 1 页内容末，不应紧跟 `emrPageBreak` 出现在第 2 页顶 |
| `signBlock` | 文末医师签名 | 保留在正文末尾 |

---

## 3. 当前编辑器期望形态（迁移目标）

### 3.1 正文 JSON（`content`）

```
doc
├── hospitalTitle          ← 仅 1 次，文档最前
├── formTitle              ← 仅 1 次
├── patientHeader          ← 仅 1 次（首页完整患者条）
├── horizontalRule         ← 首页分隔（可选）
├── … 正文段落/表格 …
├── verifyBlock            ← 若业务要求：放在第 1 段正文末、第一个 emrPageBreak 之前
├── emrPageBreak           ← 仅保留「章节级」硬分页（可选，见 §4.4）
├── … 第 2 页正文（无重复 hospitalTitle/formTitle/pageHeader）…
├── emrPageBreak
├── … 第 3 页正文 …
└── signBlock
```

**禁止**在 `emrPageBreak` 之后再次出现：`hospitalTitle`、`formTitle`、`pageHeader`、续页用 `horizontalRule`。

### 3.2 页眉页脚（`emrPageChrome` / `emrPageMargins`）

- **与 `PrintConfig.header/footer` 同源**（`emr-page-chrome.ts`），但**写在模板 `content` JSON 根上**，与 `type: doc`、`content: [...]` 并列。
- **批量迁模板**：直接改 JSON，经列表 **JSON** 弹窗保存入库；**不必改编辑器代码**。
- 正文里原来的 `hospitalTitle` / `formTitle` / 续页 `pageHeader` **迁出正文**，写入 `emrPageChrome.header.template`；正文只保留首页完整 `patientHeader`（去掉姓名/住院号那一行，避免与页眉重复）。

```json
{
  "type": "doc",
  "content": ["…正文…"],
  "emrPageChrome": {
    "header": {
      "align": "center",
      "enabled": true,
      "heightMm": 0,
      "template": "{{emrImg w=39:data:image/png;base64,…}}<span style=\"font-size:22pt;font-weight:700\">{orgName}</span>\n<span style=\"font-size:16pt\">{documentTitle}</span>\n<span style=\"font-size:12pt;float:left\">姓名：{patientName}　病房：{wardName}　床位：{bedNo}</span><span style=\"font-size:12pt;float:right\">住院号：{inpatientNo}</span><span style=\"display:block;clear:both;height:0\"></span>"
    },
    "footer": {
      "align": "center",
      "enabled": true,
      "heightMm": 12,
      "template": "第 {page} 页 (共 {total} 页)"
    }
  },
  "emrPageMargins": {
    "top": "25.4mm",
    "right": "31.8mm",
    "bottom": "25.4mm",
    "left": "31.8mm"
  }
}
```

- 占位符：`{orgName}`、`{documentTitle}`、`{patientName}`、`{inpatientNo}`（及任意元数据 `{fieldKey}`）；院徽用 `{{emrImg w=39:…}}`。
- **`heightMm: 0`**：运行时 DOM 实测；禁止写死 22/28/40。
- **姓名/住院号必须 float**，勿用空格假对齐。
- **注意**：可视化编辑弹窗打开时若未从 JSON 读回 `emrPageChrome`，页眉可能显示为默认空页眉；以 **JSON 弹窗保存后的库内 JSON** 为准验收。
- 完整旧→新对照与批量流程：`.cursor/skills/emr-template-migration/SKILL.md`。

### 3.3 可填域形态

| 阶段 | 形态 |
| --- | --- |
| 编辑器打开 | `emrDataElement` / `emrFreeText` 节点 → `flattenFillableNodesToMarks` → **mark 正文** |
| 入库 / 打印 | `serializeFillableMarksToNodes` → **节点 JSON** |

迁移脚本**直接改节点 JSON**即可；不必手写 mark。打开编辑器时会自动摊平。

### 3.4 加载管线（勿绕过）

```ts
normalizeEmrRootDoc(raw)
  → scrubEmptyTextNodes
  → promoteDataElementValuesToContent   // attrs.value → content
  → flattenFillableNodesToMarks         // 节点 → mark
```

---

## 4. 迁移规则（批量脚本逐步执行）

### 4.1 P0 — 删除重复页眉块（必做）

**检测**：在 `doc.content` 顶层数组中，对每个 `emrPageBreak` 向后扫描，若出现以下连续模式则标记为「重复页眉段」：

```
emrPageBreak
  → (可选) table[name=verifyBlock]
  → paragraph[name=hospitalTitle]
  → paragraph[name=formTitle]
  → table[name=pageHeader]
  → horizontalRule
  → (可选) paragraph[name=pageHeaderHint]
```

**操作**：删除「重复页眉段」中除 `verifyBlock` 外的所有块（`hospitalTitle`、`formTitle`、`pageHeader`、`horizontalRule`；`pageHeaderHint` 若仅服务于续页眉则一并删，若正文需要则移到后续正文前）。

**样例**：入院记录删除序号 15–18、33–36 两段重复块（约 2×4 个顶层节点）。

**验收**：编辑器打开后，每个 `emrPageBreak` 后不再出现院徽/标题/姓名住院号条。

### 4.2 P0 — 调整 `verifyBlock` 位置（必做）

**检测**：`verifyBlock` 紧跟在 `emrPageBreak` 之后。

**操作**：将 `verifyBlock` 整体移动到**紧邻其前**那个 `emrPageBreak` 之前（即第 1 页正文末尾）。

**样例**：原顺序 `…tcmFourExams → emrPageBreak → verifyBlock → hospitalTitle…`  
改为 `…tcmFourExams → verifyBlock → emrPageBreak → peTitle…`。

### 4.3 P0 — 院徽 base64 去重（强烈建议）

**检测**：`doc` 内 `image[title=hospitalLogo]` 或 `hospitalTitle` 段内 `image` 出现次数 > 1。

**操作**：

1. 保留**第一处** `hospitalTitle` 内图片；
2. 删除后续重复 `hospitalTitle` 时图片已随块删除；
3. 可选：将院徽迁入 `pageChrome.header.template` 为 `{{emrImg w=39:…}}`，正文 `hospitalTitle` 只留 `{orgName}`（需配合 §4.6 持久化 `print_config`）。

**收益**：单模板 JSON 可从数百 KB 降到数十 KB 级。

### 4.4 P1 — `emrPageBreak` 保留策略

| 策略     | 适用                             | 说明                                        |
| -------- | -------------------------------- | ------------------------------------------- |
| **保留** | 多章节文书、必须与打印页边界一致 | 入院记录保留 2 个分页符，对应 3 页内容区    |
| **删除** | 单页或纯靠软分页                 | 删除所有 `emrPageBreak`，完全依赖 A4 软分页 |

注意：保留 `emrPageBreak` 时**不得**再在符后粘页眉。软分页与硬分页并存时，屏上页数可能与 PDF 略有偏差（见 EDITOR-GAP-DECISIONS B12）。

### 4.5 P1 — 同步 `attrs.value` 与 `content`（建议）

对文本类 `emrDataElement`（`widgetOverride` 为 `text` / `multiline`）：

```js
if (isTextLikeWidget(attrs.widgetOverride)) {
  const plain = flattenInlineText(node.content);
  if (plain && plain !== attrs.value) attrs.value = plain;
  if (!plain && attrs.value && !node.content?.length) {
    node.content = plainTextToJsonInline(attrs.value);
  }
}
```

避免打印侧只读 `attrs.value` 时与编辑器显示不一致。

### 4.6 P1 — 写入 `emrPageChrome`（页眉页脚迁出正文）

**操作**（仅改模板 JSON，见 §3.2）：

1. 从**第一份** `hospitalTitle` 抽出 `image[title=hospitalLogo].attrs.src` → `header.template` 首行 `{{emrImg w=39:…}}`。
2. `formTitle` 固定文案（如「入院记录」）→ 用 `{documentTitle}` 占位（运行时由模板名/文书类型填充）。
3. 续页 `pageHeader` 简表（姓名 + 住院号）→ 写入 `header.template` 末行：`姓名：{patientName}　病房：{wardName}　床位：{bedNo}` + 右 `住院号：{inpatientNo}`（住院金标准，见 Skill §C2）。
4. 删除正文内 `hospitalTitle`、`formTitle`、各 `emrPageBreak` 后的 `pageHeader` 重复块。
5. `patientHeader` 去掉首行（姓名/住院号），保留性别起算的完整首页患者条。
6. 可选：根上写 `emrPageMargins`（打印标准 25.4 / 31.8mm）。

**入库**：列表 → **JSON** → 粘贴/编辑 → 保存（`serializeEmrTemplateContent` 会原样保留根级 `emrPageChrome` / `emrPageMargins`）。

### 4.7 P2 — 表格与空节点清理（可选）

| 项 | 操作 |
| --- | --- |
| `colwidth: null` | 双列表格可写 `[null,null]` 或按比例；非必须 |
| `text: " "` 占位 | `scrubEmptyTextNodes` 不删单空格；可改为无 text 子节点 |
| 末尾空 `paragraph` | 可删 |
| `lineHeight` / `paragraphMargin*` 全 `null` | 保持即可，走编辑器默认 |

### 4.8 不要改动的部分

- 命名段落 `attrs.name`（`peTitle`、`vitals` 等）— scss / PDF 有专用样式；
- `relativeCardiacBorder` 等业务表格结构；
- 非文本类控件（`select` / `date` / `checkbox`）保持节点形态，不转 mark；
- `signBlock` 文末签名区保留在正文；
- 首页 `patientHeader` 完整 7 行保留（产品要求首页信息全、续页简表靠 pageChrome）。

---

## 5. 样例：入院记录迁移前后对照

### 5.1 迁移前（顶层，50 个块）

见 §2.1；问题块：**#14–18**、**#33–36** 及 **#15、33 重复院徽**。

### 5.2 迁移后（正文顶层约 40 块 + 根级页眉）

**根级**（与 `content` 并列，不入正文数组）：

- `emrPageChrome.header`：院徽 + `{orgName}` + `{documentTitle}` + 姓名/住院号行
- `emrPageChrome.footer`：页码
- `emrPageMargins`：打印标准边距（可选）

**正文 `content` 顶层**（与现行金标准 `template.json` 对齐）：

```
0  patientHeader          ← 从性别行起，无姓名/住院号首行
1  horizontalRule
2–10  病史段
11  verifyBlock           ← 在第一个 emrPageBreak 前
12  emrPageBreak
13  peTitle                ← 符后直接章节，无假页头 / 无 pageHeaderHint
14–24 体格检查段
25  emrPageBreak
26–… 续页正文 + signBlock
```

删除：正文内全部 `hospitalTitle`、`formTitle`、break 后重复 `pageHeader`；院徽只保留在 `emrPageChrome` 一份。

### 5.3 预期效果

| 检查项          | 预期                                                    |
| --------------- | ------------------------------------------------------- |
| 编辑器第 1 页   | 院徽 + 标题 + 完整 patientHeader + 病史                 |
| 编辑器第 1 页末 | 核实签名行在分页符上方                                  |
| 编辑器第 2 页起 | 无重复院徽/标题；页脚显示页码（pageChrome）             |
| 打印预览        | 与迁移前版式接近，无双重页眉；页码由 PrintConfig 统一出 |
| JSON 体积       | 明显减少（去掉 2 份 base64 院徽 + 重复块）              |

---

## 6. 批量识别与执行

### 6.1 自动标记「待迁移」条件（满足任一）

```text
1. 顶层 emrPageBreak 数量 ≥ 1
   AND 其后 5 个块内出现 name=hospitalTitle

2. hospitalTitle 出现次数 > 1

3. pageHeader 出现次数 ≥ 1 且存在 emrPageBreak

4. verifyBlock 紧跟 emrPageBreak（index 差 = 1）

5. image[title=hospitalLogo] 出现次数 > 1
```

### 6.2 建议执行顺序

```
1. 备份 content → content_backup
2. parseEmrContentToDoc + 深拷贝
3. §4.2 移动 verifyBlock
4. §4.1 删除重复页眉段（从后往前删，避免下标错位）
5. §4.3 院徽去重（若 4.1 已删重复 hospitalTitle 可跳过）
6. §4.5 value/content 同步
7. serializeEmrTemplateContent（strip 患者元数据）
8. （可选）写 print_config
9. 人工：编辑器打开 + 打印预览对照样例 PDF
```

### 6.3 回归用例（每个模板至少）

- [ ] 编辑器打开无报错、无空白页
- [ ] 首页 patientHeader 字段完整、可填域为 `[占位]` 样式
- [ ] 分页处无叠字、无重复院徽
- [ ] 保存 → 再打开 → 内容不丢
- [ ] 打印预览页数与迁移前相差 ≤ 1 页
- [ ] 套用到医生站后患者信息回填正常

---

## 7. 与历史文书（document）的差异

| 维度       | 模板                                   | 已保存文书                        |
| ---------- | -------------------------------------- | --------------------------------- |
| 重复页眉   | 按 §4 删除                             | **同规则**；注意已填 `value` 勿丢 |
| 患者元数据 | `serializeEmrTemplateContent` 会 strip | **不 strip**；只做结构迁移        |
| pageChrome | 写入模板 JSON 根 `emrPageChrome`       | 随文档或请求传入 `printConfig`    |
| mark 形态  | 入库为节点                             | 打开时仍走 `normalizeEmrRootDoc`  |

文书批量迁移需单独脚本：在 §4 基础上**禁止**调用 `stripPatientBoundMetadataFromContentJson`。

---

## 8. 未决项（批量前需产品/后端确认）

1. **可视化编辑读回**：列表 JSON 已含 `emrPageChrome` 时，可视化弹窗是否自动加载（当前以 JSON 为准）。
2. **续页简表字段**：`{inpatientNo}` 等是否均在元数据中有值（打印/预览依赖 metadata）。
3. **`pageHeaderHint` 类提示语**：续页是否还需要「（要求与委托授权人一致）」— 入院记录仅第 2 页有，迁移后保留在第一个 break 后。
4. **硬分页 vs 软分页**：是否统一删除所有 `emrPageBreak` 仅靠软分页（改动大，打印页边界会变）。

---

## 9. 参考实现入口

| 主题 | 路径 |
| --- | --- |
| 加载规范化 | `emrEditor/document-content.ts` → `normalizeEmrRootDoc` |
| 节点 ↔ mark | `emrEditor/components/simple-editor/emr-fillable-json.ts` |
| value 提升 | `emrEditor/components/simple-editor/emr-data-element-text.ts` |
| 页眉页脚 | `emrEditor/components/simple-editor/emr-page-chrome.ts` |
| 分页装饰 | `emrEditor/components/simple-editor/extensions/emr-pagination.ts` |
| 硬分页符 | `emrEditor/components/simple-editor/extensions/emr-page-break.ts` |
| 命名段样式 | `emrEditor/components/simple-editor/simple-editor.scss`（`hospitalTitle`、`patientHeader`、`pageHeader`） |
| 模板入库 | `api/emr/template.ts` → `serializeEmrTemplateContent` |
| 样例 JSON | `apps/web-ele/template.json` |

---

## 10. 变更记录

| 日期 | 说明 |
| --- | --- |
| 2026-08-24 | 页眉金标准改为 float 三行 + `heightMm:0`；结构目标对齐现行 `template.json`；细节以 Skill `emr-template-migration` 为准 |
| 2026-08-22 | 初版：以入院记录 `template.json` 为例，归纳 P0～P2 迁移规则与批量识别条件 |
