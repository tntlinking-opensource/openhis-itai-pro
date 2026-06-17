/** 13xx 文件型目录下载接口（与后端 CatalogFileParserRegistry 对齐） */
export const CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS = [
  { label: '1301 西药中成药', value: '1301' },
  { label: '1302 中药饮片', value: '1302' },
  { label: '1305 医疗服务项目', value: '1305' },
  { label: '1306 医用耗材', value: '1306' },
  { label: '1307 疾病与诊断', value: '1307' },
  { label: '1308 手术操作', value: '1308' },
  { label: '1309 门诊慢特病', value: '1309' },
  { label: '1314 中医疾病', value: '1314' },
  { label: '1315 中医证候', value: '1315' },
] as const;

export type CatalogFileDownloadInfno =
  (typeof CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS)[number]['value'];

/** 1305 双版本链分支 */
export const CATALOG_1305_VER_BRANCH_OPTIONS = [
  { label: 'F002 分支', value: 'F002' },
  { label: 'F003 分支', value: 'F003' },
] as const;

export type Catalog1305VerBranch = (typeof CATALOG_1305_VER_BRANCH_OPTIONS)[number]['value'];

/** 各 infno 全量重拉时的规范初始 ver */
export function defaultCatalogStartVer(infno: string, verBranch?: string): string {
  if (infno === '1305') {
    return verBranch === 'F003' ? 'F003' : 'F002';
  }
  if (infno === '1308') {
    return '-1';
  }
  return '0';
}

export function catalogDownloadHint(infno: string): string {
  const base = `${infno} 正式下载无轮次上限，停链依赖平台 infcode/无新文件；TXT 下一 ver 对齐 OpenHIS ReadCsv。游标取最新快照。`;
  if (infno === '1305') {
    return `${base} 1305 须分别从 F002、F003 各拉一条版本链。`;
  }
  if (infno === '1308') {
    return `${base} 1308 全量重拉初始 ver 为 -1。`;
  }
  return base;
}

export function catalogPipelineCollectDesc(infno: string): string {
  return `循环：${infno} 查版本 → 9102 下 ZIP → 解压 → 读 TXT 下一 ver`;
}

/** 正式下载流水线阶段（与后端 phaseSummaries 对齐） */
export const CATALOG_DOWNLOAD_PHASES = [
  {
    key: 'collect',
    label: '拉取 ZIP/TXT',
    shortLabel: '① 拉取',
    detail: '循环：13xx 查版本 → 9102 下 ZIP → 解压 → 读下一 ver',
  },
  {
    key: 'collect_done',
    label: '收集完成',
    shortLabel: '① 就绪',
    detail: '全部版本的 ZIP 与 TXT 已在本地就绪',
  },
  {
    key: 'parse',
    label: '解压并解析',
    shortLabel: '② 解析',
    detail: '按版本从本地 ZIP 解压 TXT 并解析',
  },
  {
    key: 'import',
    label: '入库与游标',
    shortLabel: '③ 入库',
    detail: '逐版本 upsert 目录镜像，显示已入库行数',
  },
] as const;

export type CatalogDownloadPhaseKey = (typeof CATALOG_DOWNLOAD_PHASES)[number]['key'];

/** 页面顶部三步概览 */
export function buildCatalogPipelineOverview(infno: string) {
  return [
    {
      title: '循环拉取',
      desc: catalogPipelineCollectDesc(infno),
    },
    {
      title: '并行解析',
      desc: '全部 TXT 解析为入库行',
    },
    {
      title: '逐版入库',
      desc: '写入镜像 + 追加游标历史',
    },
  ] as const;
}

/** 阶段一内单版本子步骤 */
export function buildCatalogVersionLoopSteps(infno: string) {
  return [
    `${infno} 查询版本（data.ver = 本地最大版本号；ZIP+元数据已缓存则跳过平台）`,
    '9102 下载 ZIP（同版本已缓存则跳过）',
    '解压得到 TXT',
    '读 TXT 版本列作为下一轮 ver',
  ] as const;
}
