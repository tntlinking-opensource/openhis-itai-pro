/**
 * 【1312】医保目录信息查询 data 节点筛选（与后端 Catalog1312FilterRequest 对齐）
 */
export interface Catalog1312FilterParams {
  listType?: string;
  /** 名称关键字，同时匹配 hilist_name / pinyin */
  keyword?: string;
  hilistCode?: string;
  hilistName?: string;
  pinyin?: string;
  /** yyyy-MM-dd；空=服务端自动游标 */
  updtTime?: string;
  valiFlag?: string;
  chrgitmLv?: string;
  lmtUsedFlag?: string;
  queryDate?: string;
  begndate?: string;
}

/** 从查询表单提取 1312 筛选字段 */
export function pickCatalog1312Filter(source: Catalog1312FilterParams): Catalog1312FilterParams {
  return {
    listType: source.listType || undefined,
    keyword: source.keyword?.trim() || undefined,
    hilistCode: source.hilistCode?.trim() || undefined,
    hilistName: source.hilistName?.trim() || undefined,
    pinyin: source.pinyin?.trim() || undefined,
    updtTime: source.updtTime || undefined,
    valiFlag: source.valiFlag || undefined,
    chrgitmLv: source.chrgitmLv || undefined,
    lmtUsedFlag: source.lmtUsedFlag || undefined,
    queryDate: source.queryDate || undefined,
    begndate: source.begndate || undefined,
  };
}
