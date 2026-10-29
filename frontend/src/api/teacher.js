import request from './request'

/** 我的课程（学校安排） */
export function fetchTeacherCourses() {
  return request.get('/teacher/courses')
}

/** 课程申报：本人申报的全部课程及审核状态 */
export function fetchCourseApplies() {
  return request.get('/teacher/course-applies')
}

/** 教师申报新课程 */
export function createCourseApply(data) {
  return request.post('/teacher/courses', data)
}

/** 教师修改本人待审核 / 已拒绝的课程 */
export function updateCourseApply(id, data) {
  return request.put(`/teacher/courses/${id}`, data)
}

/** 教师删除本人待审核 / 已拒绝的课程 */
export function deleteCourseApply(id) {
  return request.delete(`/teacher/courses/${id}`)
}

/** 选择该课程的学生名单 */
export function fetchCourseStudents(courseId) {
  return request.get(`/teacher/courses/${courseId}/students`)
}

/** 批量录入/修改成绩 */
export function saveCourseScores(courseId, items) {
  return request.post(`/teacher/courses/${courseId}/scores`, { items })
}

/** 成绩统计 */
export function fetchCourseStatistics(courseId) {
  return request.get(`/teacher/courses/${courseId}/statistics`)
}

/** 我的教职信息 */
export function fetchTeacherProfile() {
  return request.get('/teacher/profile')
}
