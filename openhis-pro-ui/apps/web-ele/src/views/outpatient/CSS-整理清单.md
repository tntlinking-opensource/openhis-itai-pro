# 门诊（outpatient）组件 CSS 整理清单

> 用途：后续集中做样式收敛、去重、抽共享层时按本表执行。  
> 统计口径：`<style` 至 `</style>` 行数（含标签行）；`:deep` 为文件中 `:deep(` 出现次数。

---

## 处理类型说明

| 类型  | 含义                                                                              |
| ----- | --------------------------------------------------------------------------------- |
| **A** | 保留在组件内（业务唯一、版式强绑定或复杂度高）                                    |
| **B** | 优先用 Uno 原子类替代自定义类（布局/间距/简单排版）                               |
| **C** | 抽门诊域共享（`_styles/*.scss`、`uno` shortcuts，或仅门诊父级 import 的局部 CSS） |
| **D** | 抽子组件（大段 UI 块拆开后样式随块迁移）                                          |
| **E** | 穿透样式集中（多文件重复的 `:deep(.el-table…)` 等，统一壳或 partial）             |
| **0** | 无样式块，暂不处理                                                                |

---

## 一、无 `<style>` 的组件（9）

| 文件路径                                                   | 建议  |
| ---------------------------------------------------------- | ----- |
| `doctor-station/advice/components/advice-order-select.vue` | **0** |
| `doctor-station/index.vue`                                 | **0** |
| `charge/register/components/history.vue`                   | **0** |
| `dispense/index.vue`                                       | **0** |
| `dispense/components/dispense-queue-panel.vue`             | **0** |
| `nurse-station/components/nurse-footer-bar.vue`            | **0** |
| `nurse-station/components/nurse-action-record-drawer.vue`  | **0** |
| `nurse-station/components/nurse-queue-panel.vue`           | **0** |
| `charge/refund/index.vue`                                  | **0** |

---

## 二、有 `<style>` 的组件（21）

按 `<style>` 块行数**从多到少**排序（便于优先处理大块）。

| 行数（约） | `:deep` | 文件路径 | 内容概要 | 建议类型 |
| --- | --- | --- | --- | --- |
| ~151 | 6 | `charge/charge/settlement-dialog.vue` | Grid/卡片布局、金额强调、`el-button` / `el-radio-button` / `el-descriptions` 穿透 | **B + D**，颜色 **C**（token） |
| ~144 | 7 | `doctor-station/advice/components/western-advice-detail-form.vue` | 药品信息行、grid 表单项、控件宽度、含 `@media` | **B + C**（与中药明细合并共享） |
| ~142 | 28 | `dispense/components/dispense-rx-center.vue` | 成组 `ElTable` 边框/间隙行/CSS 变量，与业务结构强绑定 | **A**；可选同目录 `scss` **C**（仍仅发药用） |
| ~120 | 0 | `dispense/components/prescription-sheet-view.vue` | 处方纸仿真、scss 嵌套、固定色 | **A**；可选抽出 `prescription-sheet.scss` **C** |
| ~86 | 1 | `doctor-station/tcm-advice/components/tcm-advice-detail-form.vue` | 与西药明细上半段高度同构 | **C**（与 western 合并一份共享样式） |
| ~88 | 7 | `charge/charge/index.vue` | 筛选条、患者信息条、vxe 高度/隐藏全屏等 | **B + C + D**；vxe 若多页相同再 **E** |
| ~58 | 1 | `doctor-station/advice/components/advice-edit-card.vue` | 卡片头渐变、阴影、药品区分隔、下拉 z-index | **A + C**（z-index 与医嘱域统一策略） |
| ~47 | 0 | `doctor-station/diagnosis/index.vue`（布局）+ `components/diagnosis-quick-tree.vue` / `components/diagnosis-saved-table.vue`（`.quick-tree` / `.diagnosis-table-wrap`） | flex/grid、树/表外框 `#e5e7eb` | **B + C**（与诊断弹窗「带边框内容区」合并） |
| ~43 | 7 | `charge/register/components/register-form-area.vue` | `el-form` / 输入框紧凑行高 | **C + E**（挂号表单紧凑模式可单文件集中） |
| ~40 | 0 | `doctor-station/diagnosis/components/diagnosis-picker-dialog.vue` | 弹窗内布局、表格外框、星级 | **B + C** |
| ~34 | 6 | `doctor-station/tcm-advice/tcm-advice.vue` | `el-tree`、`el-tabs`、`el-table` 表头 `#eaf4ff` | **E + C** |
| ~28 | 1 | `nurse-station/components/nurse-right-sidebar.vue` | 打印按钮 grid + `el-button` 满宽换行 | **B** 或 **C**（若侧栏复用） |
| ~26 | 5 | `doctor-station/advice/index.vue` | 医嘱表编辑列隐藏、overflow、表头色 | **E**（表头与下面两处统一） |
| ~20 | 0 | `doctor-station/tcm-advice/components/tcm-advice-collapsed.vue` | 折叠行排版 | **A** |
| ~20 | 0 | `doctor-station/advice/components/western-advice-collapsed.vue` | 折叠行排版 | **A** |
| ~13 | 2 | `doctor-station/doctor-patient-list.vue` | 队列 `el-radio-button` 撑满 | **C** 或保留 **A** |
| ~10 | 2 | `charge/daily-settlement/index.vue` | `el-card` body、`el-form-item` 间距 | **B** 或 **C** |
| ~6 | 0 | `charge/register/index.vue` | 仅卡片 header padding | **A** 或并入挂号域 **C** |
| ~6 | 1 | `nurse-station/index.vue` | 执行主按钮最小宽度 | **C**（若跨页复用） |
| ~6 | 1 | `nurse-station/components/nurse-execute-center.vue` | 表头 `#eaf4ff`（注释对齐医生站） | **E** |
| ~5 | 1 | `dispense/components/trace-scan-dialog.vue` | 单行输入字号 | **A** |

---

## 三、横切重复项（优先收敛）

1. **工作站表格表头背景 `#eaf4ff`**  
   出现位置：`doctor-station/advice/index.vue`、`doctor-station/tcm-advice/tcm-advice.vue`、`nurse-station/components/nurse-execute-center.vue`。  
   **动作**：门诊统一 **E**（如 `outpatient-el-table-workstation` 类 + 一处 `:deep`，或 `_styles` partial 仅被门诊入口/父布局 import 一次）。

2. **西药/中药明细表单**  
   `advice/components/western-advice-detail-form.vue` 与 `tcm-advice-detail-form.vue` 上半段（`product-info-row`、`field-item`、`meta-label` 等）大量重复。  
   **动作**：**C** — 共享 `advice-drug-detail-form.shared.scss` 或薄包装布局组件，两文件只保留差异部分。

3. **带灰边圆角内容区**  
   `diagnosis/components/diagnosis-quick-tree.vue`、 `diagnosis-saved-table.vue` 与 `diagnosis/components/diagnosis-picker-dialog.vue`（`.table-wrap`）同类。  
   **动作**：**C** — 门诊「panel」语义类或小组件。

4. **挂号表单紧凑**  
   `register-form-area.vue` 大量 `el-form-item` / `el-input` 穿透；`register/index.vue` 另有极短 header 覆盖。  
   **动作**：**C** — 挂号域单文件或父级包一层 class 集中穿透。

---

## 四、建议执行顺序（降低风险）

1. **E**：统一三处 `#eaf4ff` 表头（改动面清晰、可视觉回归）。
2. **C**：合并中西药 detail 共享样式。
3. **C**：诊断列表 + 诊断弹窗「边框内容区」。
4. **B + D**：`settlement-dialog.vue` 拆子块 + Uno 收布局。
5. **charge/charge/index.vue**：患者条/工具条若与日结等重复再抽 **C**。
6. **A 收尾**：`dispense-rx-center.vue`、`prescription-sheet-view.vue`（仅维护性必要时再抽同目录 scss）。

---

## 五、与项目规范的衔接

- 页面类型与样式基线以 `frontend-page-type-and-style-standards` 为准；公共基线应落在**门诊域内共享**或业务子组件内，避免污染全局 `bootstrap`。
- 代码组织与组件选型以 `frontend-codegen` 为准。

---

_文档生成自 `apps/web-ele/src/views/outpatient` 下全部 `*.vue` 扫描；后续若增删组件请同步更新本表。_
