<script setup lang="ts">
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { TreeNodeData } from 'element-plus/es/components/tree/src/tree.type';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { nextTick, onMounted, ref, watch } from 'vue';

import { ElMessage, ElTree } from 'element-plus';

import { getDocumentTypeListApi } from '#/api/emr/document-type';
import { getTemplateListApi, listTemplatesForStationApi } from '#/api/emr/template';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_STATION_KIND,
} from '#/views/basic/emr-template/emr-template-scope.constants';

import { buildEmrTemplateTree } from './build-emr-template-tree';

defineOptions({
  name: 'EmrTemplateTree',
});

const props = withDefaults(
  defineProps<{
    /** 仅展示启用文书类型；默认 true */
    documentTypesEnabledOnly?: boolean;
    /** 与外侧搜索框联动，筛选节点展示 */
    filterText?: string;
    /** 医生站场景：门诊 / 住院；传入时走 listForStation */
    stationKind?: EmrTemplateStationKind;
    /** 仅加载启用模板；默认 true */
    templatesEnabledOnly?: boolean;
  }>(),
  {
    documentTypesEnabledOnly: true,
    filterText: '',
    templatesEnabledOnly: true,
    stationKind: undefined,
  },
);

const emit = defineEmits<{
  select: [node: EmrTemplateTreeNode];
}>();

const treeRef = ref<InstanceType<typeof ElTree>>();
const loading = ref(false);
const treeData = ref<EmrTemplateTreeNode[]>([]);

const PAGE_SIZE = 200;

function resolveEncounterClassCode(kind: EmrTemplateStationKind | undefined): string | undefined {
  if (kind === EMR_TEMPLATE_STATION_KIND.OUTPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
  }
  if (kind === EMR_TEMPLATE_STATION_KIND.INPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.IMP;
  }
  return undefined;
}

/** 文书类型：走 documentType/getList（维护页同源，含 sortOrder） */
async function fetchDocumentTypesForTree(): Promise<DocumentTypeDto[]> {
  const params = props.documentTypesEnabledOnly ? { enabled: true } : {};
  const list = await getDocumentTypeListApi(params);
  const rows = unwrapApiList<DocumentTypeDto>(list);
  if (rows.length > 0 || !props.documentTypesEnabledOnly) {
    return rows;
  }
  const fallback = await getDocumentTypeListApi({});
  return unwrapApiList<DocumentTypeDto>(fallback);
}

/** 模板分页拉全量（listForStation 不可用时的回退） */
async function fetchTemplatesByPagedList(encounterClassCode?: string): Promise<TemplateDto[]> {
  const all: TemplateDto[] = [];
  let pageNo = 1;
  while (true) {
    const res = await getTemplateListApi({
      pageNo,
      pageSize: PAGE_SIZE,
      enabled: props.templatesEnabledOnly ? true : undefined,
      encounterClassCode,
    });
    const batch = res?.content ?? [];
    all.push(...batch);
    if (batch.length === 0 || batch.length < PAGE_SIZE) break;
    pageNo += 1;
  }
  return all;
}

async function fetchTemplatesForTree(): Promise<TemplateDto[]> {
  const kind = props.stationKind;
  const encounterClassCode = resolveEncounterClassCode(kind);
  if (
    kind !== EMR_TEMPLATE_STATION_KIND.OUTPATIENT &&
    kind !== EMR_TEMPLATE_STATION_KIND.INPATIENT
  ) {
    return fetchTemplatesByPagedList(undefined);
  }

  try {
    const list = await listTemplatesForStationApi({
      stationKind: kind,
      enabled: props.templatesEnabledOnly ? true : undefined,
    });
    const rows = unwrapApiList<TemplateDto>(list);
    if (rows.length > 0) {
      return rows;
    }
    // listForStation 成功但为空：回退分页拉 IMP/AMB 模板（含全院维护、未挂科室可见范围的模板）
    return fetchTemplatesByPagedList(encounterClassCode);
  } catch (stationError) {
    console.warn('[EmrTemplateTree] listForStation failed, fallback to getList', stationError);
    return fetchTemplatesByPagedList(encounterClassCode);
  }
}

async function loadTree() {
  loading.value = true;
  let partialFailure = false;
  try {
    const [typesResult, templatesResult] = await Promise.allSettled([
      fetchDocumentTypesForTree(),
      fetchTemplatesForTree(),
    ]);

    const typeList =
      typesResult.status === 'fulfilled'
        ? typesResult.value
        : ((partialFailure = true), console.error(typesResult.reason), []);
    const templates =
      templatesResult.status === 'fulfilled'
        ? templatesResult.value
        : ((partialFailure = true), console.error(templatesResult.reason), []);

    if (typeList.length === 0 && templates.length === 0) {
      const reason =
        typesResult.status === 'rejected'
          ? typesResult.reason
          : templatesResult.status === 'rejected'
            ? templatesResult.reason
            : undefined;
      if (reason) {
        throw reason;
      }
    }

    treeData.value = buildEmrTemplateTree(typeList, templates);
    if (partialFailure) {
      ElMessage.warning('部分模板数据加载失败，已展示可用内容');
    }
    await nextTick();
    treeRef.value?.filter((props.filterText ?? '').trim());
  } catch (error) {
    console.error(error);
    treeData.value = [];
    ElMessage.error('加载病历模板树失败');
  } finally {
    loading.value = false;
  }
}

function filterNode(value: string, data: TreeNodeData, _child: Node) {
  if (!value) return true;
  const node = data as EmrTemplateTreeNode;
  const kw = value.toLowerCase();
  return (node.label ?? '').toLowerCase().includes(kw);
}

watch(
  () => props.filterText,
  (val) => {
    treeRef.value?.filter((val ?? '').trim());
  },
);

watch(
  () => props.stationKind,
  () => {
    void loadTree();
  },
);

onMounted(() => {
  loadTree();
});

function handleNodeClick(data: EmrTemplateTreeNode) {
  if (data.isDocumentType) return;
  emit('select', data);
}

defineExpose({
  reload: loadTree,
});
</script>

<template>
  <div v-loading="loading" class="min-h-[120px]">
    <div v-if="!loading && treeData.length === 0" class="text-text-sub py-8 text-center text-sm">
      <p>暂无文书类型或模板</p>
      <p v-if="stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT" class="mt-2 text-xs">
        请在「基础数据 → 病历模板」维护住院（IMP）文书类型与模板，或联系管理员配置全院模板
      </p>
    </div>
    <ElTree
      v-else
      ref="treeRef"
      :data="treeData"
      :props="{ children: 'children', label: 'label' }"
      default-expand-all
      highlight-current
      node-key="nodeKey"
      :filter-node-method="filterNode"
      @node-click="handleNodeClick"
    />
  </div>
</template>
