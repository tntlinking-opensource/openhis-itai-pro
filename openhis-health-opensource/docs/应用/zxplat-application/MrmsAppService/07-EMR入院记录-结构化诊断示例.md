# EMR 入院记录 — 结构化诊断与入院病情示例

> **用途**：供 MRMS 病案首页「导入诊断」时，从 EMR 文书自动回填 `admCondCode`（入院病情）  
> **消费方**：`EmrAdmissionConditionResolver`（`zxplat-domain/mrms/support）  
> **关联**：[03-病案首页-详细设计.md](./03-病案首页-详细设计.md) §5.3、[07.4 自动取值](./03-病案首页-详细设计.md#74-fillsource-合并算法)

---

## 1. 入院病情字典（adm_cond_type / 4401 adm_cond_code）

与术语 `ter_code_system.id = 434`（入院病情类型）对齐，病案首页存 **code 字符串**：

| code | 含义 |
|------|------|
| `1` | 有 |
| `2` | 临床未确定 |
| `3` | 情况不明 |
| `4` | 无 |

MRMS 按 **ICD-10 编码（大写）** 与 EMR 行匹配，例如 `J18.9` → `1`。

---

## 2. 解析器支持的三种写法（任选其一或组合）

### 2.1 推荐：隐藏数据元 + JSON 数组（`attrs.value`）

在入院记录模板中增加一个 **不参与正文展示** 的数据元（或放在「诊断」区域末尾），`elementId` 任意，`value` 存 JSON 数组：

```json
[
  {
    "diagCode": "J18.900",
    "diagName": "肺炎，未特指",
    "admCondCode": "1"
  },
  {
    "diagCode": "I10.x00",
    "diagName": "原发性高血压",
    "admCondCode": "2"
  }
]
```

Tiptap 节点示例（类型 `emrDataElement`，与编辑器扩展一致）：

```json
{
  "type": "emrDataElement",
  "attrs": {
    "elementId": "admissionDiagnosisStructured",
    "labelSnapshot": "入院诊断结构化",
    "value": "[{\"diagCode\":\"J18.900\",\"diagName\":\"肺炎，未特指\",\"admCondCode\":\"1\"}]",
    "editable": true,
    "widgetOverride": "text"
  }
}
```

**字段别名**（解析器均识别）：`diagCode` / `icdCode` / `conditionCode`；`admCondCode` / `admCond` / `adm_cond_code` / `admCondType` / `adm_cond_type`。

医生站保存诊断时，由 **带入逻辑或脚本** 同步更新该 `value`（与 `EncounterDiagnosis` 一致），勿依赖手工粘贴 JSON。

---

### 2.2 命名表格：按行单元格 `data-name`

表格 `attrs.name` 含 `diagnosis` 或 `admissionDiag`（不区分大小写），每行单元格命名：

| 单元格 `attrs.name` | 内容示例 |
|---------------------|----------|
| `diagCode` | `J18.900` |
| `admCondCode` | `1` |

Tiptap 片段（表头 + 一行数据）：

```json
{
  "type": "table",
  "attrs": { "name": "admissionDiagnosisList" },
  "content": [
    {
      "type": "tableRow",
      "content": [
        {
          "type": "tableHeader",
          "attrs": { "name": "diagCode" },
          "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "诊断编码" }] }]
        },
        {
          "type": "tableHeader",
          "attrs": { "name": "admCondCode" },
          "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "入院病情" }] }]
        }
      ]
    },
    {
      "type": "tableRow",
      "content": [
        {
          "type": "tableCell",
          "attrs": { "name": "diagCode" },
          "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "J18.900" }] }]
        },
        {
          "type": "tableCell",
          "attrs": { "name": "admCondCode" },
          "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "1" }] }]
        }
      ]
    }
  ]
}
```

前端表格扩展见 `emr-table.ts`（`attrs.name` ↔ `data-name`）。

---

### 2.3 整表 JSON：`table.attrs.value`

诊断表可将 **整表数据** 放在 `attrs.value`（与 2.1 相同数组结构）：

```json
{
  "type": "table",
  "attrs": {
    "name": "admissionDiagnosisTable",
    "value": "[{\"diagCode\":\"J18.900\",\"admCondCode\":\"1\"}]"
  },
  "content": []
}
```

适合「表格 UI 绑定 value、行由组件渲染」的模板设计。

---

## 3. 完整入院记录 doc 最小示例

以下为可写入 `emr_template.content` / `emr_document.content` 的 **最小可测** 文档（含段落 + 2.1 结构化数据元）：

```json
{
  "type": "doc",
  "content": [
    {
      "type": "paragraph",
      "attrs": { "textAlign": "center" },
      "content": [{ "type": "text", "text": "入院记录" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "初步诊断：" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "肺炎，未特指（J18.900）" }]
    },
    {
      "type": "emrDataElement",
      "attrs": {
        "elementId": "admissionDiagnosisStructured",
        "labelSnapshot": "入院诊断结构化（MRMS）",
        "value": "[{\"diagCode\":\"J18.900\",\"diagName\":\"肺炎，未特指\",\"admCondCode\":\"1\"}]",
        "editable": true,
        "widgetOverride": "text"
      }
    }
  ]
}
```

**文书类型**：`document_type_code = ADMISSION_RECORD`（优先被 MRMS 读取；其次 `DISCHARGE_SUMMARY`，再其余同就诊文书）。

---

## 4. 与 HIS 诊断的关系

| 来源 | 作用 |
|------|------|
| `EncounterDiagnosis` + 目录 | MRMS AutoFill 经 `CaseRecordAutoFillQueryService` 带入 **ICD 编码/名称** |
| EMR 结构化 JSON | 补充 **admCondCode**（按 `diagCode` 匹配） |

二者 **编码需一致**（同一 ICD-10，大小写不敏感）。若 EMR 无结构化行，首页「入院病情」列为空，可手工在 MRMS 首页 T5 填写。

---

## 5. 联调步骤

1. 患者有就诊 `encounterId`，且存在类型为 `ADMISSION_RECORD` 的 EMR 文书，content 含 §3 示例数据元。
2. 医生站/HIS 已有出院/入院诊断（`adm_encounter_diagnosis`），编码与 EMR 中 `J18.900` 一致。
3. 打开 MRMS 病案首页 → **导入本章（诊断）** 或 **全部导入**。
4. 诊断 T5「入院病情」列应出现 `1`（或模板中配置的值）。
5. 保存后检查 `mrms_homepage_diagnosis.adm_cond_code`。

---

## 6. 模板实施建议（实施/信息科）

1. 在入院记录模板增加 **隐藏或折叠** 的 `admissionDiagnosisStructured` 数据元（§2.1）。
2. **Wave 1 已装配**：住院医生站保存/删除诊断后，后端自动回写该数据元（`EmrAdmissionDiagnosisSyncExtension`）；`admCondCode` 保留 EMR 中已有值。
3. 若已有诊断表格 UI，优先用 §2.2 命名表格，便于医生直接编辑入院病情下拉（术语 434）。
4. 编码使用 **医保/4401 口径** 的 ICD-10 字符串，与 MRMS 首页、`ConditionDefinition.conditionCode` 保持一致。

---

## 7. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-19 | 初版：对齐 `EmrAdmissionConditionResolver` Wave 1 解析规则 |
| v1.1 | 2026-06-19 | V7 入院记录种子模板；住院诊断保存自动回写 `admissionDiagnosisStructured` |
