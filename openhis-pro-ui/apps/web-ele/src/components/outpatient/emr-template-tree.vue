<script setup lang="ts">
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { TreeNodeData } from 'element-plus/es/components/tree/src/tree.type';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { TemplateDto } from '#/api/emr/template';

import { nextTick, onMounted, ref, watch } from 'vue';

import { ElMessage, ElTree } from 'element-plus';

import { getTemplateDocumentTypesApi, listTemplatesForStationApi } from '#/api/emr/template';
import {
  EMR_TEMPLATE_STATION_KIND,
  type EmrTemplateStationKind,
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
    /** 仅加载启用模板；默认 true */
    templatesEnabledOnly?: boolean;
    /** 医生站场景：门诊 / 住院；传入时走 listForStation */
    stationKind?: EmrTemplateStationKind;
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

async function fetchTemplatesForTree(): Promise<TemplateDto[]> {
  const kind = props.stationKind;
  if (
    kind === EMR_TEMPLATE_STATION_KIND.OUTPATIENT ||
    kind === EMR_TEMPLATE_STATION_KIND.INPATIENT
  ) {
    const list = await listTemplatesForStationApi({
      stationKind: kind,
      enabled: props.templatesEnabledOnly ? true : undefined,
    });
    return Array.isArray(list) ? list : [];
  }
  return [];
}

async function loadTree() {
  loading.value = true;
  try {
    const docTypeParams = props.documentTypesEnabledOnly ? { enabled: true } : {};
    const [types, templates] = await Promise.all([
      getTemplateDocumentTypesApi(docTypeParams),
      fetchTemplatesForTree(),
    ]);
    const typeList = Array.isArray(types) ? types : [];
    treeData.value = buildEmrTemplateTree(typeList, templates);
    await nextTick();
    treeRef.value?.filter((props.filterText ?? '').trim());
  } catch (error) {
    console.error(error);
    ElMessage.error('加载病历模板树失败');
    treeData.value = [];
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
      暂无文书类型或模板
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
