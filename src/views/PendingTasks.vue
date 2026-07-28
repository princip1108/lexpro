<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">待办任务</h2>
      <div class="toolbar">
        <el-button :icon="Refresh">刷新</el-button>
        <el-button type="primary" :icon="Check">批量确认</el-button>
      </div>
    </div>

    <div class="pending-stats">
      <SectionCard v-for="item in data.stats" :key="item.label">
        <div class="pending-stat" :style="{ '--accent': item.color }">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </SectionCard>
    </div>

    <div class="pending-layout">
      <SectionCard title="任务列表">
        <el-table :data="data.tasks" stripe>
          <el-table-column prop="title" label="任务名称" min-width="260" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="owner" label="负责人" width="90" />
          <el-table-column prop="deadline" label="截止时间" width="170" />
          <el-table-column prop="priority" label="优先级" width="90">
            <template #default="{ row }">
              <el-tag :type="row.priority === '高' ? 'danger' : row.priority === '中' ? 'warning' : 'info'">
                {{ row.priority }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType[row.status]">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default>
              <el-button type="primary" link>处理</el-button>
              <el-button type="primary" link>转办</el-button>
            </template>
          </el-table-column>
        </el-table>
      </SectionCard>

      <SectionCard title="任务动态">
        <el-timeline>
          <el-timeline-item v-for="item in data.timeline" :key="item.time" :timestamp="item.time" type="primary">
            <div class="timeline-card">{{ item.title }}</div>
          </el-timeline-item>
        </el-timeline>
      </SectionCard>
    </div>
  </div>
</template>

<script setup>
import { Check, Refresh } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/pendingTasks.json'

const statusType = {
  待处理: 'warning',
  处理中: 'primary',
  已超期: 'danger',
  待确认: 'info'
}
</script>

<style scoped>
.pending-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.pending-stat {
  position: relative;
  display: grid;
  gap: 8px;
  padding-left: 12px;
}

.pending-stat::before {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 0;
  width: 4px;
  background: var(--accent);
  border-radius: 4px;
  content: "";
}

.pending-stat span {
  color: #64748b;
}

.pending-stat strong {
  color: var(--accent);
  font-size: 30px;
}

.pending-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.timeline-card {
  padding: 12px 14px;
  color: #3f5268;
  background: #f4f8ff;
  border-radius: 6px;
}
</style>
