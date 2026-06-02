/**
 * 知识库管理：类型与数据访问。
 * 后端 Controller 就绪后，将下列 *Api 函数改为 requestClient 调用（路径建议 /emg/knowledge/**）。
 */
import type { PageApiResponse, PageParams } from '#/types/api-response';

/** 知识库条目 */
export interface KnowledgeBaseItem {
  id: number;
  name: string;
  pinyinCode: string;
  triageLevel: number;
  type: string;
  category: string;
  sortOrder: number;
  status: number;
  createTime?: string;
  updateTime?: string;
}

/** 知识库列表查询参数 */
export interface KnowledgeBaseQueryParams extends PageParams {
  name?: string;
  pinyinCode?: string;
  triageLevel?: number | null;
  type?: string | null;
  category?: string | null;
}

let MOCK_KNOWLEDGE_BASE: KnowledgeBaseItem[] = [
  {
    id: 1,
    name: '头痛',
    pinyinCode: 'toutong',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 1,
    status: 1,
  },
  {
    id: 2,
    name: '胸痛',
    pinyinCode: 'xiong tong',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'CHEST',
    sortOrder: 2,
    status: 1,
  },
  {
    id: 3,
    name: '腹痛',
    pinyinCode: 'fu tong',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'ABDOMEN',
    sortOrder: 3,
    status: 1,
  },
  {
    id: 4,
    name: '发热',
    pinyinCode: 'fa re',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'WHOLE',
    sortOrder: 4,
    status: 1,
  },
  {
    id: 5,
    name: '外伤',
    pinyinCode: 'wai shang',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'LIMBS',
    sortOrder: 5,
    status: 1,
  },
  {
    id: 6,
    name: '呼吸困难',
    pinyinCode: 'hu xi kun nan',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'CHEST',
    sortOrder: 6,
    status: 1,
  },
  {
    id: 7,
    name: '意识障碍',
    pinyinCode: 'yi shi zhang ai',
    triageLevel: 1,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 7,
    status: 1,
  },
  {
    id: 8,
    name: '抽搐',
    pinyinCode: 'chou chu',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 8,
    status: 1,
  },
  {
    id: 9,
    name: '出血',
    pinyinCode: 'chu xue',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 9,
    status: 1,
  },
  {
    id: 10,
    name: '恶心呕吐',
    pinyinCode: 'e xin ou tu',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'ABDOMEN',
    sortOrder: 10,
    status: 1,
  },
  {
    id: 11,
    name: '头晕',
    pinyinCode: 'tou yun',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 11,
    status: 1,
  },
  {
    id: 12,
    name: '乏力',
    pinyinCode: 'fa li',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'WHOLE',
    sortOrder: 12,
    status: 1,
  },
  {
    id: 13,
    name: '心悸',
    pinyinCode: 'xin ji',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'CHEST',
    sortOrder: 13,
    status: 1,
  },
  {
    id: 14,
    name: '晕厥',
    pinyinCode: 'yun jue',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 14,
    status: 1,
  },
  {
    id: 15,
    name: '咳血',
    pinyinCode: 'ke xue',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'CHEST',
    sortOrder: 15,
    status: 1,
  },
  {
    id: 16,
    name: '黄疸',
    pinyinCode: 'huang dan',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'ABDOMEN',
    sortOrder: 16,
    status: 1,
  },
  {
    id: 17,
    name: '腰痛',
    pinyinCode: 'yao tong',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 17,
    status: 1,
  },
  {
    id: 18,
    name: '关节痛',
    pinyinCode: 'guan jie tong',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'LIMBS',
    sortOrder: 18,
    status: 1,
  },
  {
    id: 19,
    name: '皮疹',
    pinyinCode: 'pi zhen',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 19,
    status: 1,
  },
  {
    id: 20,
    name: '少尿',
    pinyinCode: 'shao niao',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 20,
    status: 1,
  },
  {
    id: 21,
    name: '复视',
    pinyinCode: 'fu shi',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 21,
    status: 1,
  },
  {
    id: 22,
    name: '声音嘶哑',
    pinyinCode: 'sheng yin si ya',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 22,
    status: 1,
  },
  {
    id: 23,
    name: '吞咽困难',
    pinyinCode: 'tun yan kun nan',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 23,
    status: 1,
  },
  {
    id: 24,
    name: '肢体麻木',
    pinyinCode: 'zhi ti ma mu',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'LIMBS',
    sortOrder: 24,
    status: 1,
  },
  {
    id: 25,
    name: '肢体无力',
    pinyinCode: 'zhi ti wu li',
    triageLevel: 2,
    type: 'SYMPTOM',
    category: 'LIMBS',
    sortOrder: 25,
    status: 1,
  },
  {
    id: 26,
    name: '视力模糊',
    pinyinCode: 'shi li mo hu',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'HEAD',
    sortOrder: 26,
    status: 1,
  },
  {
    id: 27,
    name: '耳痛',
    pinyinCode: 'er tong',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 27,
    status: 1,
  },
  {
    id: 28,
    name: '鼻塞流涕',
    pinyinCode: 'bi sai liu ti',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'OTHER',
    sortOrder: 28,
    status: 1,
  },
  {
    id: 29,
    name: '腹泻',
    pinyinCode: 'fu xie',
    triageLevel: 3,
    type: 'SYMPTOM',
    category: 'ABDOMEN',
    sortOrder: 29,
    status: 1,
  },
  {
    id: 30,
    name: '便秘',
    pinyinCode: 'bian mi',
    triageLevel: 4,
    type: 'SYMPTOM',
    category: 'ABDOMEN',
    sortOrder: 30,
    status: 1,
  },
];

let nextId = 31;

export async function listKnowledgeBaseApi(
  params: KnowledgeBaseQueryParams,
): Promise<PageApiResponse<KnowledgeBaseItem>> {
  let filtered = [...MOCK_KNOWLEDGE_BASE];

  if (params.name) {
    const k = params.name.trim();
    const kl = k.toLowerCase();
    filtered = filtered.filter(
      (item) => item.name.includes(k) || item.pinyinCode.toLowerCase().includes(kl),
    );
  }

  if (params.triageLevel != null) {
    filtered = filtered.filter((item) => item.triageLevel === params.triageLevel);
  }

  if (params.type) {
    filtered = filtered.filter((item) => item.type === params.type);
  }

  if (params.category) {
    filtered = filtered.filter((item) => item.category === params.category);
  }

  const pageIndex = Math.max(0, (params.pageNo ?? 1) - 1);
  const start = pageIndex * params.pageSize;
  const end = start + params.pageSize;
  const paginated = filtered.slice(start, end);

  return {
    content: paginated,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    totalCount: filtered.length,
    totalElements: filtered.length,
  };
}

export async function createKnowledgeBaseApi(
  item: Omit<KnowledgeBaseItem, 'id' | 'createTime' | 'updateTime'>,
): Promise<KnowledgeBaseItem> {
  const newItem: KnowledgeBaseItem = {
    ...item,
    id: nextId++,
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
  };
  MOCK_KNOWLEDGE_BASE.push(newItem);
  return newItem;
}

export async function updateKnowledgeBaseApi(
  item: Partial<KnowledgeBaseItem> & { id: number },
): Promise<KnowledgeBaseItem> {
  const index = MOCK_KNOWLEDGE_BASE.findIndex((i) => i.id === item.id);
  if (index === -1) {
    throw new Error('知识库项不存在');
  }
  const current = MOCK_KNOWLEDGE_BASE[index];
  if (!current) {
    throw new Error('知识库项不存在');
  }

  const next: KnowledgeBaseItem = {
    ...current,
    name: item.name ?? current.name,
    pinyinCode: item.pinyinCode ?? current.pinyinCode,
    triageLevel: item.triageLevel ?? current.triageLevel,
    type: item.type ?? current.type,
    category: item.category ?? current.category,
    sortOrder: item.sortOrder ?? current.sortOrder,
    status: item.status ?? current.status,
    createTime: item.createTime ?? current.createTime,
    updateTime: new Date().toISOString(),
  };
  MOCK_KNOWLEDGE_BASE[index] = next;
  return next;
}

export async function deleteKnowledgeBaseApi(id: number): Promise<void> {
  const index = MOCK_KNOWLEDGE_BASE.findIndex((i) => i.id === id);
  if (index === -1) {
    throw new Error('知识库项不存在');
  }
  MOCK_KNOWLEDGE_BASE.splice(index, 1);
}

export async function getKnowledgeBaseByIdApi(id: number): Promise<KnowledgeBaseItem | null> {
  return MOCK_KNOWLEDGE_BASE.find((item) => item.id === id) || null;
}

export async function listKnowledgeBaseByCategoryApi(
  category: string,
): Promise<KnowledgeBaseItem[]> {
  return MOCK_KNOWLEDGE_BASE.filter((item) => item.category === category && item.status === 1);
}
