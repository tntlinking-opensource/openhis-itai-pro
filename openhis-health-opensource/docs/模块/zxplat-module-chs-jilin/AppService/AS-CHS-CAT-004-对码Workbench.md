# 对码 Workbench · 应用服务设计（AS）

> **页面边界**：批量对码作业台 — 查询待对码/已对码清单、批量触发 3301、作业状态；以及 **平台已对照（1317）** 本地镜像分页与从平台同步/正向回写。**在内** Workbench 列表、批处理入口、1317 镜像读与同步；**在外** 单条 3301/3302（**AS-CHS-CAT-003**）、health 主数据编辑。
>
> **编码**：AS-CHS-CAT-004 · **版本**：v0.2 · **日期**：2026-07-29  
> **详细设计编号**：AS-CHS-CAT-004

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | Workbench 分页查询；批量上传编排入口；1317 本地镜像分页；1317 平台同步（落库 + 正向回写院内 `yb_match_flag`，不改 `yb_no`） |
| **在外** | 3301/3302 单条维护页；国家目录 1312（**AS-CHS-CAT-001**） |
| **平台 infno** | 3301（经 CAT-003 invoker 复用）；**1317**（本页同步） |

## 2. 接口概览

| IF 编号 | 用户动作 | 读/写 | infno |
|---------|----------|-------|-------|
| IF-CHS-CAT-004-R-01 | 分页查询 Workbench 行 | 读 | — |
| IF-CHS-CAT-004-W-01 | 批量上传选中行 | 写 | 3301（前端逐条调 CAT-003） |
| IF-CHS-CAT-004-R-02 | 分页查询平台已对照本地镜像 | 读 | — |
| IF-CHS-CAT-004-W-02 | 从平台同步 1317（落库+正向回写） | 写 | 1317 |

## 3. 1317 回写规则

- 仅 `vali_flag=1` 的行尝试回写。
- 匹配键：`medins_list_codg` = 院内 `bus_no`；`list_type` 101/102→药品、201→诊疗、301→耗材。
- 命中则只写 `yb_match_flag=true`；**不**用 `med_list_codg` 覆盖院内 `yb_no`（耗材平台短码会盖掉完整码）。
- **不**因平台缺行而清院内已对码 / 触发 3302。

## 4. 关联

- 前端：`/chs/catalog-mapping-workbench` · API：`insuranceCatalogWorkbench/*`
- 规范：[1317-医药机构目录匹配信息查询](../apis/13xx/1317-医药机构目录匹配信息查询.md)

## 5. 院内不用 / 孤儿对照清理（v0.3）

| IF | 说明 |
|----|------|
| R-02 扩展 | 平台镜像行标注 `hospitalMatchStatus`：`ACTIVE` / `INACTIVE` / `MISSING`；筛选 `hospitalMatchFilter=UNUSED` |
| W-03 | `revokePlatformCatalogMatch3302`：按镜像行调 3302；院内命中则清 `yb_match_flag`，孤儿仅撤平台；成功后镜像 `vali_flag=0` |
