# 生产样式偏差改造清单（dev vs build）

> 背景：院点生产包与开发环境样式不一致（布局塌、查询按钮换行、两栏变一列等）。  
> 根因摘要见对话结论；本清单只列**可执行改造项**与优先级。  
> 扫描口径：`apps/web-ele/src`（不含废弃 `views/warehouse`）；约 591 个 `.vue`。  
> 生成日期：2026-07-26。

---

## 0. 改造原则（先定规则，再改文件）

### 0.1 布局写法（硬约束）

| 场景 | 推荐 | 禁止 |
|------|------|------|
| 输入框 + 按钮同一行 | 外层 `flex flex-nowrap items-center gap-*`；输入外包一层 `flex-1 min-w-0`（或 `width:0;flex:1`）；按钮 `shrink-0` | `ElInput`/`ElSelect` 直接作 flex 子项且依赖其默认 `width:100%` |
| 关键栅格/两栏三栏 | Tailwind 原子类，或**外挂 `.css` + `import`**（参照 `station-tri-pane-layout.css`） | 仅靠 SFC `<style scoped>` 撑关键布局 |
| 院点 `#/` override | 主线未命中时 `return null`（走 `package.json` imports） | resolve 成 src 绝对路径（生产会丢 scoped CSS） |

### 0.2 验收口径（每改一处）

1. `pnpm run build:ele:project`（与线上一致）后本地 `preview` / 静态打开。  
2. 对比开发态同一页面：搜索行、工具条、侧栏栅格无换行/塌陷。  
3. 在 `dist/css` 中能搜到该页**关键 class**（若走外挂 CSS / 原子类则验对应选择器或工具类仍生效）。

### 0.3 金标准参照

- 外挂 CSS：`components/layouts/station-tri-pane-layout.css`  
- 输入行加固：`components/doctor-station/diagnosis/components/diagnosis-picker-dialog.vue`（`search-input-wrap`）  
- 全局同步样式：`bootstrap.ts` 顶部同步 `import`（勿改回异步）

---

## 1. P0 — 止血与守门（本周）

| # | 项 | 说明 | 状态 |
|---|----|------|------|
| P0-1 | 诊断开立弹窗搜索行 | **根因：院点 `overrides/.../diagnosis-picker-dialog.vue` 覆盖主线**；已在 override 用外挂 css + 原子类；主线同步 | **已做（v3 院点）** |
| P0-1c | 中药处方页（朝阳） | override `tcm-advice.vue` + `tcm-prescription-header.vue`：外挂 layout css；label 48px+nowrap；页签 new-tab 去重叠 | **已做** |
| P0-1b | 高置信同构 4 处 | `elep-catalog-picker-dialog`、`lis-pacs-item-mapping/edit-dialog`、`peis/personal-register` 历史工具条、`register-list-toolbar` | **已做** |
| P0-2 | 院点 override 插件回归 | 确认 `createOpenHisProjectOverridePlugin`：主线 `#/` 仍 `return null`；`?vue&type=style` 不误改道 | 待确认 |
| P0-3 | 构建后 CSS 冒烟脚本 | `build:ele:project` 后检查关键选择器必存在（见 §5） | 待做 |
| P0-4 | 发版命令对齐 | 文档/发布脚本明确：线上 = `build:ele:project`，禁止用 `dev:ele` 视觉当生产验收 | 待做 |

**P0 人工回归页（院点包）：**

1. 门诊医生站 → 新增诊断弹窗（西医/中医）  
2. 任意工作站三栏页（`StationTriPaneLayout`）  
3. 门诊挂号 / 收费各一页工具条

---

## 2. P1 — 高流量业务：flex 行内控件（优先门诊/住院医生护士）

> 扫描命中「`ElInput` 后近距 `ElButton` + flex/工具条语境」约 **74** 处。P1 先改**医生站 / 门诊 / 住院工作站**。

### 2.1 门诊医生站 / 诊断 / 医嘱

| 文件 | 风险点 | 动作 |
|------|--------|------|
| `components/doctor-station/diagnosis/components/diagnosis-picker-dialog.vue` | 搜索行 | 已加固；中医病/证表单行一并核对 |
| `components/doctor-station/diagnosis/components/advice-diagnosis-bind-panel.vue` | scoped flex 布局 | 关键行改原子类或外挂；Input 行包一层 |
| `components/doctor-station/diagnosis/components/diagnosis-saved-table.vue` | scoped flex | 表头/操作行改原子类 |
| `components/doctor-station/elep-advice/components/elep-catalog-picker-dialog.vue` | Input+Button | 同诊断弹窗模式 |
| `components/doctor-station/tcm-advice/tcm-advice.vue` | scoped 工具条 | 工具条改 `flex flex-wrap` 原子类为主 |
| `components/doctor-station/tcm-advice/components/tcm-prescription-header.vue` | scoped flex | 同上 |
| `views/outpatient/doctor-station/advice/components/advice-draft-bind-dialog.vue` | Input+Button / scoped | 包 Input + 原子类 |
| `views/outpatient/doctor-station/advice/components/history-advice-drawer.vue` | Input+Button / scoped | 同上 |
| `views/outpatient/doctor-station/advice/components/western-advice-detail-form.vue` | scoped 表单行 | 与中药明细共享时可一并收 |
| `views/outpatient/doctor-station/advice/components/advice-edit-card.vue` | scoped | 卡片头操作区原子类 |
| `views/outpatient/doctor-station/advice/components/advice-*-form-panel.vue` | scoped flex | 表单项宽度用包裹层 |
| `views/outpatient/doctor-station/doctor-patient-list.vue` | scoped | 队列筛选行 |
| `views/outpatient/doctor-station/service-apply/service-apply-dialog.vue` | scoped | 弹窗工具条 |

### 2.2 门诊收费 / 发药 / 护士

| 文件 | 动作 |
|------|------|
| `views/outpatient/charge/charge/index.vue` | 筛选条：Input 包一层 + 原子类 |
| `views/outpatient/charge/refund/index.vue` | 同上 |
| `views/outpatient/charge/register/components/register-form-area.vue` | 紧凑表单穿透保留；行布局原子类 |
| `views/outpatient/charge/register/components/register-left-panel.vue` 等 | 侧栏/摘要行 |
| `views/outpatient/dispense/components/dispense-queue-panel.vue` | 队列搜索行 |
| `views/outpatient/dispense/components/dispense-rx-center.vue` | 布局以业务为主，工具条仍按 0.1 |
| `views/outpatient/nurse-station/components/nurse-queue-panel.vue` | 队列搜索 |
| `views/outpatient/nurse-station/components/nurse-skin-test-center.vue` | 工具条 |
| `views/outpatient/return-medicine/components/return-medicine-queue-panel.vue` | 队列搜索 |
| `components/outpatient/patient-list.vue` | 列表搜索行 |

### 2.3 住院医生 / 护士 / 发药（同构优先）

| 文件 | 动作 |
|------|------|
| `views/inpatient/doctor-station/doctor-patient-list.vue` | 对齐门诊患者列表 |
| `views/inpatient/doctor-station/advice/components/*` | 与门诊医嘱同构文件一起改 |
| `views/inpatient/nurse-station/components/patient-list-panel.vue` | 队列搜索 |
| `views/inpatient/nurse-station/inpatient-billing/**` | 计费筛选行 |
| `views/inpatient/nurse-station/skin-test/**` | 皮试工具条 |
| `views/inpatient/dispense/components/*queue*` | 发药队列搜索 |
| `views/inpatient/return-medicine/components/*queue*` | 退药队列 |
| `components/trace/trace-batch-scan-dialog.vue` | 追溯码扫描行（已有部分 `min-width:0`，补全 Input 包裹） |

---

## 3. P2 — 其余 flex+Input+Button / scoped 布局（分域扫尾）

> 完整命中列表以扫描脚本为准；下列为域分组，改时按「搜索/筛选工具条」优先。

| 域 | 代表路径 | 建议 |
|----|----------|------|
| 医保 CHS | `chs/catalog-notice`、`chs/reconciliation`、`chs/platform-dict`、`chs/*picker*` | 查询条统一 Input 包裹 |
| 预约 booking | `views/booking/**`（排班、号源、队列屏） | 工具条原子类化 |
| 基础数据 basic | `views/basic/**/edit-dialog.vue`、`ordertemplate` | 弹窗搜索行 |
| 财务 finance | `views/finance/**` | 调价/电票筛选 |
| 库存 inventory | `views/inventory/**` 主 pane 搜索 | 与单据工作站清单并行 |
| 病案 mrms | `views/mrms/**` | 诊断网格/质控 |
| 体检 peis | `views/peis/**` | 登记/报告查询条 |
| 报表 report | `views/report/**` | 查询区（同时遵守报表 Skill） |
| 其它 | `hiprint`、`emrEditor`、`demo`、`test` | 低优先级或按投诉驱动 |

**scoped + `display:flex` 自定义布局类**扫描约 **104** 处：不要求一次清完；P1 文件改完后，剩余按「用户投诉 / 院点反馈」插入迭代。

---

## 4. P3 — 工程化（防回归）

| # | 项 | 做法 |
|---|----|------|
| P3-1 | 构建冒烟 | 见 §5 脚本；接入 `build:ele:project` 后可选 step |
| P3-2 | codegen / Skill 补一句 | `frontend-codegen`：flex 行内禁止裸 `ElInput`；关键布局优先原子类或外挂 css |
| P3-3 | Cursor rule（可选） | 短 rule：生产样式偏差禁止只加 scoped flex 不加固 Input |
| P3-4 | Tailwind content | 院点 `projects/*/overrides` 已纳入 content；新增动态类名必须 safelist 或完整静态写 |
| P3-5 | 与门诊 `CSS-整理清单.md` 对齐 | 门诊 B/C 类收敛时，同步落实本清单 0.1，避免只抽 scss 仍裸 Input |

---

## 5. 构建后 CSS 冒烟（建议脚本要点）

`build:ele:project` 完成后检查 `apps/web-ele/dist`：

**必存在（示例，可增删）：**

- `station-tri-pane-layout__grid`（外挂关键布局）  
- `search-input-wrap` 或加固后的等价 class（诊断弹窗）  
- `layout-viewport-compact` 相关全局若线上启用则验 compact css 已打入 bootstrap chunk  

**禁止信号：**

- 关键页仅有组件 JS、对应 scoped 选择器（`[data-v-xxxx]` + 布局类）整段缺失，且该页布局只依赖 scoped  

实现建议：`scripts/check-prod-css-smoke.mjs`，失败则非 0 退出。

---

## 6. 单文件改造检查表（复制用）

```text
[ ] 搜索/筛选行：Input 已包裹或 width 受控；按钮 shrink-0；nowrap
[ ] 关键栅格：原子类或外挂 css，不单靠 scoped
[ ] 无动态拼接 Tailwind 类（或已 safelist）
[ ] build:ele:project + preview 对比开发态
[ ]（可选）dist 中可见关键 class
```

---

## 7. 建议排期

| 迭代 | 内容 |
|------|------|
| W1 | P0 全部 + P1 §2.1 诊断/医嘱弹窗与工具条 |
| W2 | P1 §2.2～2.3 门诊收费发药护士 + 住院同构 |
| W3 | P3 冒烟脚本 + Skill/规则；P2 按投诉与 CHS/booking 先扫 |
| 持续 | P2 分域扫尾；与 `CSS-整理清单` B/C 收敛合并做 |

---

## 8. 扫描统计（2026-07-26）

| 指标 | 数量 |
|------|------|
| `.vue` 文件 | 591 |
| `search-row` | 1（诊断弹窗） |
| flex 语境下 ElInput→ElButton | 74 |
| scoped + display:flex 布局类 | 104 |

复扫命令（仓库根目录）：

```bash
node scripts/scan-css-prod-drift-risk.mjs
```
