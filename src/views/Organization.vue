<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">组织管理</h2>
      <div class="toolbar">
        <el-button :icon="User">人员同步</el-button>
        <el-button type="primary" :icon="Plus">新增部门</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <SectionCard v-for="item in data.stats" :key="item.label">
        <div class="org-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <p>{{ item.desc }}</p>
        </div>
      </SectionCard>
    </div>

    <div class="org-layout">
      <SectionCard title="部门架构">
        <div class="dept-grid">
          <div v-for="item in data.departments" :key="item.name" class="dept-card">
            <div class="dept-head">
              <strong>{{ item.name }}</strong>
              <el-tag size="small">{{ item.leader }}</el-tag>
            </div>
            <p>{{ item.focus }}</p>
            <div class="dept-meta">
              <span>{{ item.people }} 人</span>
              <span>{{ item.cases }} 件在办</span>
            </div>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="人员状态">
        <el-table :data="data.members" stripe>
          <el-table-column prop="name" label="姓名" width="90" />
          <el-table-column prop="department" label="部门" min-width="150" />
          <el-table-column prop="role" label="角色" min-width="130" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === '在线' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="负载" width="150">
            <template #default="{ row }">
              <el-progress :percentage="row.workload" :stroke-width="8" />
            </template>
          </el-table-column>
        </el-table>
      </SectionCard>
    </div>
  </div>
</template>

<script setup>
import { Plus, User } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/organization.json'
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.org-stat {
  display: grid;
  gap: 8px;
}

.org-stat span,
.org-stat p {
  color: #64748b;
}

.org-stat strong {
  color: var(--primary-deep);
  font-size: 30px;
}

.org-stat p {
  margin: 0;
  font-size: 13px;
}

.org-layout {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  align-items: start;
}

.dept-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dept-card {
  padding: 16px;
  background: #f7f9fd;
  border: 1px solid #edf1f7;
  border-radius: 6px;
}

.dept-head,
.dept-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dept-card p {
  min-height: 44px;
  margin: 12px 0;
  color: #64748b;
  line-height: 1.6;
}

.dept-meta {
  color: #3f5268;
  font-size: 13px;
}
</style>
