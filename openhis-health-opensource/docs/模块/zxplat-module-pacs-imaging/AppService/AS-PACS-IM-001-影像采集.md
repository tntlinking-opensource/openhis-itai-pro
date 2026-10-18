# 影像扩展 · 影像采集 · 应用服务设计（AS）

> **编码**：AS-PACS-IM-001 · **版本**：v1.2 · **日期**：2026-09-22  
> **菜单**：影像采集 · **类名**：`PacsImageCaptureAppService`  
> **路由**：`POST /api/app/pacsImageCapture/{method}`；C-STORE 入站另见 `POST /api/integration/pacs-imaging/cstore-notify`  
> **选型**：设备入库 = C-STORE → Orthanc；关键图 = MinIO 桶 `key-images`；阅片取像素不在本 AS

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 已检待采集列表、绑定 Study、手工 DCM 进 Orthanc、关键图进 MinIO、C-STORE 到达后回写工单 |
| **在外** | 登记/执行/报告正文；OHIF 像素渲染；Orthanc 进程本身 |
| **涉及域** | PacsWorkItem（读 + `hasMedia`）；ImagingStudy、Media（imaging，`module-pacs-imaging-domain` 已落地） |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **技师** | 查待采集、看详情、上传 DCM、保存/删除关键图 |
| **系统** | Orthanc 收到 C-STORE 后绑定 Study 并回写 `hasMedia` |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **待采集** | 工单 `completed` 且尚无 Study，或已有 Study 但无关键图；列表不落患者姓名列以外的 PHI |
| **Study** | DICOM Study Instance UID，存在 Orthanc；ZXPlat 只存索引 |
| **关键图** | JPEG/PNG，对象键在 MinIO `key-images`，不进 OHIF 序列 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期（序号 51～54、58）** | CAP-01/03/06/07（查询 API）/09/10/13/14/15 |
| **非目标** | CAP-07 DICOM C-FIND / `.wl` 文件生成；CAP-11/12、视频 CAP-17/18、CAP-19/20；CAP-02 检索并入列表；CAP-05/08/16 并入绑定或保存关键图，不单列按钮 |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsWorkItem | [DM-PACS-001](../../../领域/DomainModel/DM-PACS-001-PacsWorkItem-检查工单.md) |
| ImagingStudy / Media | **领域已实现**（`module-pacs-imaging-domain`）；契约见 [DM-PACS-003](../DomainModel/DM-PACS-003-影像索引与阅片端点.md) |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CAP-01** | 只列已检工单 | CAP-01 |
| **BR-CAP-02** | 绑定 Study 后写 ImagingStudy，并 `hasMedia=true` | CAP-06、CAP-10 |
| **BR-CAP-03** | 手工 DCM 经 STOW 进 Orthanc，不落 ZXPlat 库为像素 | CAP-09 |
| **BR-CAP-04** | 关键图只进 MinIO；`Media` 存对象键；至少一张则 `hasMedia=true` | CAP-13/14 |
| **BR-CAP-05** | 删除最后一张关键图且无 Study 时 `hasMedia=false` | CAP-14 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 | 状态变化 | 涉及领域 |
|----|----------|------|:--:|----------|----------|----------|
| **IF-PACS-CAP-01** | 待采集列表（含检索） | 分页已检待采集 | 读 | BR-CAP-01 | — | PacsWorkItem |
| **IF-PACS-CAP-03** | 打开采集详情 | 模态、部位、Study/关键图摘要 | 读 | — | — | PacsWorkItem、ImagingStudy、Media |
| **IF-PACS-CAP-06** | 绑定 Study/Series | 无 Study 则创建索引 | 写 | BR-CAP-02 | hasMedia=true | ImagingStudy、PacsWorkItem |
| **IF-PACS-CAP-09** | 上传 DCM | STOW 到 Orthanc 再绑定 | 写 | BR-CAP-03 | 同 CAP-06 | ImagingStudy |
| **IF-PACS-CAP-07** | MWL 待扫查询（HTTP） | 分页待执行工单供同步脚本 | 读 | — | — | PacsWorkItem |
| **IF-PACS-CAP-10** | （系统）C-STORE 到达 | 按 Accession/StudyUID 绑定 | 写 | BR-CAP-02 | hasMedia=true | ImagingStudy、PacsWorkItem |
| **IF-PACS-CAP-13** | 保存关键图（含截图） | 写入 MinIO + Media | 写 | BR-CAP-04 | hasMedia=true | Media、PacsWorkItem |
| **IF-PACS-CAP-14** | 删除关键图 | 删对象与索引 | 写 | BR-CAP-05 | 可能 hasMedia=false | Media、PacsWorkItem |
| **IF-PACS-CAP-15** | 关键图列表 | 预签名 URL 列表 | 读 | — | — | Media |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| G-IM-CAP-01 | DM 能力 | [DM-PACS-003](../DomainModel/DM-PACS-003-影像索引与阅片端点.md) §6.1 | `ensureStudy` / `bindFromCStore` | module-pacs-imaging-domain 实现 §6.1 | CAP-06/09/10 | P0 | ✅ 已关闭（领域已实现） |
| G-IM-CAP-02 | DM 能力 | DM-PACS-003 §6.2 | `saveKeyImage` / `deleteKeyImage` | module-pacs-imaging-domain 实现 §6.2 | CAP-13/14 | P0 | ✅ 已关闭（领域已实现） |
| G-IM-CAP-03 | QueryService 能力 | DM-PACS-003 §6.4 | 按工单查询 Study/Media | ImagingStudy/Media QueryService | CAP-03/15 | P0 | ✅ 已关闭（领域已实现） |

应用层：序号 **51～54、57～58** 已落地（`PacsImageCaptureAppService` + Orthanc Lua 回调）。**CAP-10** 骨架已关闭（入站 Controller + `onCStoreReceived` 幂等）。**CAP-07** 已提供查询 API（`listModalityWorklist`）；DICOM C-FIND / `.wl` 不在本 AS。

PacsWorkItem 的 `getDetail`、`markHasMedia`、`searchPendingExecution`、`findByAccessionNumber` **已有**，不入 Gap。

---

## 4. 接口编排说明书

### 4.1 IF-PACS-CAP-01

| 维度 | 说明 |
|------|------|
| **主路径** | 查已检工单，排除已完整采集（规则见 BR-CAP-01） |
| **查询** | 先 `PacsWorkItemQueryService#searchCompleted`；是否已有 Study/关键图依赖 G-IM-CAP-03 |

### 4.2 IF-PACS-CAP-03

| 维度 | 说明 |
|------|------|
| **主路径** | 加载工单详情，再取 Study 与关键图摘要 |
| **失败** | 工单不存在 |

### 4.3 IF-PACS-CAP-06 / CAP-09 / CAP-10

| 维度 | 说明 |
|------|------|
| **主路径** | 校验工单已检 → 写 Study 索引 → `markHasMedia(true)`。CAP-09 在写索引前把 DCM 交给 Orthanc。CAP-10 由 Orthanc Lua `OnStableStudy` 回调 `POST /api/integration/pacs-imaging/cstore-notify`，按检查号找到工单 |
| **失败** | 工单未完成；检查号对不上；Orthanc 拒绝；回调 Token 非法 |

### 4.3.1 IF-PACS-CAP-07（查询 API）

| 维度 | 说明 |
|------|------|
| **主路径** | `listModalityWorklist` → `searchPendingExecution`；可选模态/科室；姓名可空 |
| **非目标** | 不生成 DICOM `.wl`；不提供 C-FIND SCP |

### 4.4 IF-PACS-CAP-13 / CAP-14 / CAP-15

| 维度 | 说明 |
|------|------|
| **主路径** | 保存：对象进 MinIO，索引进 Media，再 `hasMedia=true`。删除：删对象与索引，按 BR-CAP-05 决定标志。列表：只返回可打开的预签名地址 |
| **失败** | 对象存储不可用；图不属于该工单 |

---

## 5. 业务异常

| 码 | 场景 | 可重试 |
|----|------|:--:|
| `PACS_WORK_ITEM_NOT_FOUND` | 工单不存在 | 否 |
| `PACS_WORK_ITEM_INVALID_STATUS` | 未检不能采集 | 否 |
| `PACS_IMAGING_STUDY_BIND_FAILED` | Study 绑定失败 | 是 |
| `PACS_KEY_IMAGE_STORE_FAILED` | MinIO 写入失败 | 是 |

---

## 6. 事件语义

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `PacsWorkItemMediaFlagUpdatedEvent` | hasMedia 变化 | 报告列表可显示「有图」 |

不新发「像素已归档」事件；Orthanc 为影像真源。

---

## 7. 非功能性需求

关键图单张上传 P95 目标 &lt; 3s（不含超大 DCM）。C-STORE 回调须幂等：同一 StudyUID 重复通知不插入第二行。

---

## 8. 测试要点

- 未检工单不出现在待采集列表，也不能上传
- 绑定 Study 后 hasMedia=true，重复绑定不产生第二条 Study
- 关键图可列表、删除；删光且无 Study 时 hasMedia=false
- 关键图 URL 不能当 DICOM 给 OHIF
- C-STORE 回调 Token 错误拒绝；accession 不存在抛 NOT_FOUND；重复回调幂等
- MWL 列表仅待执行态；姓名缺失不阻断

---

## 9. 关联文档

- SDD：[AS-PACS-IM-001-SDD](../AppService-SDD/AS-PACS-IM-001-影像采集-SDD.md)
- 选型：[04-第三方组件选型.md](../04-第三方组件选型.md) §二
- 工单：[DM-PACS-001](../../../领域/DomainModel/DM-PACS-001-PacsWorkItem-检查工单.md)
- 部署：[deploy/README.md](../../../../zxplat-plugin/zxplat-module-pacs-imaging/deploy/README.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-10 首版；P2 能力列入非目标；领域缺口见 §3 |
| v1.1 | 2026-09-22 | §1.5/§3：领域已实现；应用 51～53 已落地；CAP-09/10 标骨架 |
| v1.2 | 2026-09-22 | 序号 58：CAP-10 关闭骨架（入站回调）；CAP-07 查询 API 已提供 |
