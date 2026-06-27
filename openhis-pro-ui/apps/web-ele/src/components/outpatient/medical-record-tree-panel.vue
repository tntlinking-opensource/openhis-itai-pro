<script setup lang="ts">
import { ref } from 'vue';

import { Search } from '@element-plus/icons-vue';
import { ElInput, ElTree } from 'element-plus';

defineOptions({
  name: 'MedicalRecordTreePanel',
});

const emit = defineEmits<{
  search: [keyword: string];
  select: [item: MedicalRecordTreeNode];
}>();

interface MedicalRecordTreeNode {
  label: string;
  children?: MedicalRecordTreeNode[];
}

const searchKeyword = ref('');

// 病历树形数据
const treeData = ref([
  {
    label: '门诊病历',
    children: [
      {
        label: '门诊病历(1.0.0)',
      },
    ],
  },
  {
    label: '住院病历',
    children: [],
  },
  {
    label: '告知书',
    children: [],
  },
  {
    label: '知情同意书',
    children: [],
  },
  {
    label: '病案首页',
    children: [],
  },
]);

const handleSearch = () => {
  emit('search', searchKeyword.value);
};

const handleNodeClick = (data: MedicalRecordTreeNode) => {
  emit('select', data);
};
</script>

<template>
  <div class="flex h-full flex-col border-r bg-white">
    <!-- 搜索区域 -->
    <div class="flex h-[42px] flex-shrink-0 items-center border-b px-[10px]">
      <ElInput
        v-model="searchKeyword"
        placeholder="病历名称搜索..."
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon>
            <Search />
          </el-icon>
        </template>
      </ElInput>
    </div>

    <!-- 树形列表 -->
    <div class="flex-1 overflow-y-auto p-4">
      <ElTree
        :data="treeData"
        :props="{ children: 'children', label: 'label' }"
        default-expand-all
        highlight-current
        @node-click="handleNodeClick"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
// 样式已通过 Tailwind 类实现
</style>
