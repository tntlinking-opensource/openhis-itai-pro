# 公费配置 · 应用服务设计（AS）

> **编码**：AS-CMP-GF-CFG-001 · **版本**：v1.1 · **日期**：2026-07-19  
> **类名**：`CampusGfConfigAppService` · **路由**：`/campus/gf/config`  
> **HTTP**：`/api/app/campusGfConfig`  
> **成稿编号**：DM-CMP-GF-002
> **ADR**：[ADR-CMP-001](../ADR-CMP-001-成码前决策.md) D6/D7  
> **遗留 UI**：nenu-vue-his `/gf/percent`（CFG-03 部分）

---

## 2. 接口

| IF | 方法 | 说明 |
|----|------|------|
| IF-CMP-CFG-01 | `getMasterSwitch` / `saveMasterSwitch` | 总开关 |
| IF-CMP-CFG-02 | `getCalcMode`（可选） | **弱化**：不作收银默认路径；可不实现 `save` |
| IF-CMP-CFG-03 | `listFreePercents` / `saveFreePercents` | 甲乙丙；**必带 chargeModeEnum** |

`saveFreePercents`：按 `chargeModeEnum`（`SELF_PAY_GF` \| `AFTER_YB_GF`）保存甲乙丙列表；ratio 0～1。

收银路径以预结算请求 `gfChargeMode` 为准（ADR D5），与 CFG-02 无关。

---

## 5. 异常

`GF_SWITCH_INVALID` / `GF_RATIO_OUT_OF_RANGE` / `GF_CHARGE_MODE_REQUIRED`
