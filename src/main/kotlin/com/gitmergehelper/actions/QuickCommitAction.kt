package com.gitmergehelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.gitmergehelper.model.TargetBranch
import com.gitmergehelper.services.GitMergeHelperSettings
import com.gitmergehelper.services.GitMergeService
import com.gitmergehelper.utils.NotificationUtils

/**
 * 快速提交并合并操作
 * 允许用户快速提交未提交的更改，并可选择继续执行合并流程
 */
class QuickCommitAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        // 获取配置和服务
        val gitService = GitMergeService.getInstance()
        
        // 检查Git状态
        val gitStatus = gitService.getGitStatus(project)
        if (gitStatus == null) {
            Messages.showErrorDialog(
                project,
                "未找到Git仓库或无法获取Git状态",
                "Git合并助手"
            )
            return
        }
        
        // 检查是否有未提交的更改
        if (!gitStatus.hasUncommittedChanges) {
            Messages.showInfoMessage(
                project,
                "当前没有未提交的更改",
                "Git合并助手"
            )
            return
        }
        
        // 显示提交信息输入对话框
        showCommitDialog(project, gitStatus.isFeatureBranch)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
        
        if (project != null) {
            val gitService = GitMergeService.getInstance()
            val gitStatus = gitService.getGitStatus(project)
            
            // 根据是否有未提交更改更新显示
            if (gitStatus != null) {
                e.presentation.text = if (gitStatus.hasUncommittedChanges) {
                    "快速提交并合并 (${gitStatus.currentBranch})"
                } else {
                    "快速提交并合并 (无更改)"
                }
                e.presentation.isEnabled = gitStatus.hasUncommittedChanges
            }
        }
    }
    
    /**
     * 显示提交信息输入对话框
     * 
     * @param project 项目实例
     * @param isFeatureBranch 是否为功能分支
     */
    private fun showCommitDialog(project: Project, isFeatureBranch: Boolean) {
        val commitMessage = Messages.showInputDialog(
            project,
            "请输入提交消息：",
            "快速提交",
            Messages.getQuestionIcon()
        )
        
        if (commitMessage.isNullOrBlank()) {
            return
        }
        
        // 验证提交消息
        if (commitMessage.length > 100) {
            Messages.showErrorDialog(
                project,
                "提交消息不能超过100个字符",
                "Git合并助手"
            )
            return
        }
        
        // 如果是功能分支，询问是否继续合并
        var continueWithMerge = false
        if (isFeatureBranch) {
            val choice = Messages.showYesNoDialog(
                project,
                "提交后是否继续执行自动合并？",
                "快速提交",
                Messages.getQuestionIcon()
            )
            continueWithMerge = (choice == Messages.YES)
        }
        
        if (continueWithMerge && isFeatureBranch) {
            // 如果选择继续合并，显示目标分支选择
            showTargetBranchSelection(project, commitMessage)
        } else {
            // 只执行提交
            executeQuickCommit(project, commitMessage, false, null)
        }
    }
    
    /**
     * 显示目标分支选择（当选择继续合并时）
     * 
     * @param project 项目实例
     * @param commitMessage 提交消息
     */
    private fun showTargetBranchSelection(project: Project, commitMessage: String) {
        val config = GitMergeHelperSettings.getInstance().getConfig()
        val targetBranches = config.targetBranches
        
        if (targetBranches.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "未配置目标分支。\n请在设置中添加至少一个目标分支。",
                "Git合并助手"
            )
            return
        }
        
        val popupStep = object : BaseListPopupStep<TargetBranch>("选择合并目标分支", targetBranches) {
            override fun getTextFor(value: TargetBranch): String = value.toString()
            
            override fun onChosen(selectedValue: TargetBranch, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    executeQuickCommit(project, commitMessage, true, selectedValue.name)
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        
        val popup: ListPopup = JBPopupFactory.getInstance().createListPopup(popupStep)
        popup.showCenteredInCurrentWindow(project)
    }
    
    /**
     * 执行快速提交
     * 
     * @param project 项目实例
     * @param commitMessage 提交消息
     * @param continueWithMerge 是否继续合并
     * @param targetBranch 目标分支（如果继续合并）
     */
    private fun executeQuickCommit(
        project: Project,
        commitMessage: String,
        continueWithMerge: Boolean,
        targetBranch: String?
    ) {
        val gitService = GitMergeService.getInstance()
        
        val actionMessage = if (continueWithMerge) {
            "开始快速提交并合并到分支 '$targetBranch'..."
        } else {
            "开始快速提交..."
        }
        
        NotificationUtils.showInfo(
            project,
            actionMessage,
            "Git合并助手"
        )
        
        gitService.quickCommitAndMerge(
            project, 
            commitMessage, 
            continueWithMerge, 
            targetBranch
        ) { result ->
            if (result.success) {
                NotificationUtils.showSuccess(
                    project,
                    result.message,
                    "Git合并助手"
                )
                
                // 如果是完整的提交+合并流程，显示详细结果
                if (continueWithMerge && result.mergedBranches.isNotEmpty()) {
                    val details = result.mergedBranches.joinToString("\n") { 
                        "• ${it.fromBranch} → ${it.toBranch}"
                    }
                    
                    Messages.showInfoMessage(
                        project,
                        "${result.message}\n\n合并详情：\n$details",
                        "提交并合并完成"
                    )
                }
            } else {
                NotificationUtils.showError(
                    project,
                    result.message,
                    "Git合并助手"
                )
                
                // 如果有冲突，提供解决选项
                if (result.conflictFiles.isNotEmpty()) {
                    showConflictResolutionDialog(project, result.conflictFiles)
                }
            }
        }
    }
    
    /**
     * 显示冲突解决对话框
     * 
     * @param project 项目实例
     * @param conflictFiles 冲突文件列表
     */
    private fun showConflictResolutionDialog(project: Project, conflictFiles: List<String>) {
        val conflictList = conflictFiles.joinToString("\n• ", "• ")
        
        val choice = Messages.showYesNoDialog(
            project,
            "检测到合并冲突：\n$conflictList\n\n" +
                    "请手动解决冲突后，选择继续合并操作。",
            "合并冲突",
            "我知道了",
            "取消",
            Messages.getWarningIcon()
        )
        
        if (choice == Messages.YES) {
            NotificationUtils.showInfo(
                project,
                "请在编辑器中手动解决冲突，然后提交更改。",
                "Git合并助手"
            )
        }
    }
} 