<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>我的课程</span>
          <el-button type="primary" link :loading="loading" @click="loadList">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </template>

      <el-skeleton v-if="loading && !courses.length" :rows="4" animated />

      <el-empty v-else-if="!courses.length" description="暂无授课任务" />

      <el-row v-else :gutter="16">
        <el-col
          v-for="course in courses"
          :key="course.id"
          :xs="24"
          :sm="12"
          :lg="8"
        >
          <el-card shadow="hover" class="course-card">
            <div class="course-head">
              <div>
                <span class="course-no">{{ course.courseNo }}</span>
                <span class="course-name">{{ course.name }}</span>
              </div>
              <el-tag :type="courseTypeTag(course.courseType)" size="small">
                {{ course.courseType || '-' }}
              </el-tag>
            </div>

            <el-descriptions :column="2" size="small" class="mt-8">
              <el-descriptions-item label="学分">{{ course.credit ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="学时">{{ course.hours ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="学期" :span="2">
                {{ course.semesterName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="上课时间" :span="2">
                {{ course.weekdayLabel || formatWeekday(course.weekday) }}
                第{{ course.startSection }}-{{ course.endSection }}节
              </el-descriptions-item>
              <el-descriptions-item label="上课地点" :span="2">
                {{ course.location || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="容量">{{ course.capacity ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="已选人数">
                <el-tag type="primary" size="small">{{ course.selectedCount ?? 0 }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div class="course-foot">
              <el-button type="primary" @click="goStudents(course)">
                <el-icon><User /></el-icon>学生名单 / 成绩录入
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>课程一览</span>
      </template>
      <el-table :data="courses" border stripe empty-text="暂无数据">
        <el-table-column prop="courseNo" label="课程号" width="120" />
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column prop="hours" label="学时" width="80" align="center" />
        <el-table-column label="性质" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeTag(row.courseType)" size="small">{{ row.courseType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上课时间" width="150">
          <template #default="{ row }">
            {{ row.weekdayLabel || formatWeekday(row.weekday) }}
            第{{ row.startSection }}-{{ row.endSection }}节
          </template>
        </el-table-column>
        <el-table-column prop="location" label="上课地点" width="120" />
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="selectedCount" label="已选人数" width="100" align="center" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goStudents(row)">学生名单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchTeacherCourses } from '@/api/teacher'
import { courseTypeTag, formatWeekday } from '@/utils/dict'

const router = useRouter()
const courses = ref([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchTeacherCourses()
    courses.value = res?.data || []
  } catch (e) {
    courses.value = []
  } finally {
    loading.value = false
  }
}

function goStudents(course) {
  router.push(`/teacher/courses/${course.id}/students`)
}

onMounted(loadList)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.course-card {
  margin-bottom: 16px;
}

.course-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-no {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
}

.course-name {
  font-size: 16px;
  font-weight: 600;
}

.course-foot {
  margin-top: 12px;
  text-align: right;
}
</style>
