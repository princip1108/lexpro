<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">待办案件</h2>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索案件名称或编号" :prefix-icon="Search" clearable />
        <el-button type="primary" :icon="Plus">新建任务</el-button>
      </div>
    </div>
    <SectionCard title="案件列表">
      <el-table :data="filteredCases" stripe>
        <el-table-column prop="name" label="案件名称" min-width="240" />
        <el-table-column prop="caseNo" label="案件编号" min-width="190" />
        <el-table-column prop="caseType" label="案件类型" width="100" />
        <el-table-column prop="handler" label="承办人" width="100" />
        <el-table-column prop="currentStage" label="当前环节" width="150" />
        <el-table-column prop="deadline" label="截止时间" width="170" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default>
            <el-button type="primary" link>办理</el-button>
            <el-button type="primary" link>查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </SectionCard>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import cases from '../mock/todoCases.json'

const keyword = ref('')
const statusType = {
  待审查: 'warning',
  生成中: 'primary',
  待确认: 'info',
  已完成: 'success',
  已超期: 'danger'
}

const filteredCases = computed(() => {
  if (!keyword.value) return cases
  return cases.filter((item) => item.name.includes(keyword.value) || item.caseNo.includes(keyword.value))
})
</script>
