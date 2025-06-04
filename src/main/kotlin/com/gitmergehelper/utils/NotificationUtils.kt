package com.gitmergehelper.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

/**
 * 通知工具类
 * 提供统一的通知显示功能
 */
object NotificationUtils {
    
    private val NOTIFICATION_GROUP = NotificationGroup(
        "Git Merge Helper",
        NotificationDisplayType.BALLOON,
        true
    )
    
    /**
     * 显示信息通知
     * 
     * @param project 项目实例
     * @param content 通知内容
     * @param title 通知标题
     */
    fun showInfo(project: Project?, content: String, title: String = "信息") {
        showNotification(project, title, content, NotificationType.INFORMATION)
    }
    
    /**
     * 显示成功通知
     * 
     * @param project 项目实例
     * @param content 通知内容
     * @param title 通知标题
     */
    fun showSuccess(project: Project?, content: String, title: String = "成功") {
        showNotification(project, title, content, NotificationType.INFORMATION)
    }
    
    /**
     * 显示警告通知
     * 
     * @param project 项目实例
     * @param content 通知内容
     * @param title 通知标题
     */
    fun showWarning(project: Project?, content: String, title: String = "警告") {
        showNotification(project, title, content, NotificationType.WARNING)
    }
    
    /**
     * 显示错误通知
     * 
     * @param project 项目实例
     * @param content 通知内容
     * @param title 通知标题
     */
    fun showError(project: Project?, content: String, title: String = "错误") {
        showNotification(project, title, content, NotificationType.ERROR)
    }
    
    /**
     * 显示通知的核心方法
     * 
     * @param project 项目实例
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     */
    private fun showNotification(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType
    ) {
        val notification = NOTIFICATION_GROUP.createNotification(title, content, type)
        
        if (project != null) {
            Notifications.Bus.notify(notification, project)
        } else {
            Notifications.Bus.notify(notification)
        }
    }
} 