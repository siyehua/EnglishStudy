package com.siyehua.egnlishstudy.data

import com.siyehua.egnlishstudy.model.Content

/**
 * 课程队列的轻量持有者：列表页把当前展示的课程列表放进来，
 * 详情页/播放器据此实现"上一课/下一课"与自动连播。
 */
object LessonQueueHolder {
    var items: List<Content> = emptyList()
}
