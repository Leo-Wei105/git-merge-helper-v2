package com.gitmergehelper.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.gitmergehelper.model.*
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.util.concurrent.atomic.AtomicBoolean
import com.intellij.openapi.vcs.changes.ChangeListManager

/**
 * Git合并核心服务
 * 实现自动化分支合并的主要业务逻辑
 */
@Service(Service.Level.APP)
class GitMergeService {
    
    private val isOperationInProgress = AtomicBoolean(false)
    
    companion object {
        /**
         * 获取服务实例
         * 
         * @return GitMergeService实例
         */
        fun getInstance(): GitMergeService {
            return ApplicationManager.getApplication().getService(GitMergeService::class.java)
        }
    }
    
    /**
     * 执行自动合并流程
     * 
     * @param project 项目实例
     * @param targetBranch 目标分支
     * @param callback 结果回调
     */
    fun executeAutoMerge(
        project: Project,
        targetBranch: String,
        callback: (MergeResult) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            callback(MergeResult.failure("已有合并操作正在进行中，请稍后再试"))
            return
        }
        log("开始自动合并流程，目标分支: $targetBranch")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "执行自动合并", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = performAutoMerge(project, targetBranch, indicator)
                    log("自动合并流程结束，结果: ${result.message}")
                    callback(result)
                } finally {
                    isOperationInProgress.set(false)
                }
            }
        })
    }
    
    /**
     * 快速提交并可选择继续合并
     * 
     * @param project 项目实例
     * @param commitMessage 提交消息
     * @param continueWithMerge 是否继续合并
     * @param targetBranch 目标分支（如果继续合并）
     * @param callback 结果回调
     */
    fun quickCommitAndMerge(
        project: Project,
        commitMessage: String,
        continueWithMerge: Boolean,
        targetBranch: String? = null,
        callback: (MergeResult) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            callback(MergeResult.failure("已有操作正在进行中，请稍后再试"))
            return
        }
        log("开始快速提交，提交信息: $commitMessage，继续合并: $continueWithMerge，目标分支: $targetBranch")
        if (!isValidCommitMessage(commitMessage)) {
            callback(MergeResult.failure("提交信息不能为空且不超过100字符"))
            isOperationInProgress.set(false)
            return
        }
        if (continueWithMerge && targetBranch != null && !isValidBranchName(targetBranch)) {
            callback(MergeResult.failure("目标分支名不合法"))
            isOperationInProgress.set(false)
            return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "快速提交", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = performQuickCommit(project, commitMessage, continueWithMerge, targetBranch, indicator)
                    log("快速提交结束，结果: ${result.message}")
                    callback(result)
                } finally {
                    isOperationInProgress.set(false)
                }
            }
        })
    }
    
    /**
     * 获取当前Git状态
     * 
     * @param project 项目实例
     * @return Git状态信息
     */
    fun getGitStatus(project: Project): GitStatusInfo? {
        try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return null
            
            val config = GitMergeHelperSettings.getInstance().getConfig()
            val currentBranch = repository.currentBranchName ?: "unknown"
            val isFeatureBranch = config.isFeatureBranch(currentBranch)
            val changeListManager = ChangeListManager.getInstance(project)
            val hasUncommittedChanges = changeListManager.allChanges.isNotEmpty()

            // 检查远程仓库状态
            val remoteStatus = if (repository.remotes.isNotEmpty()) "已连接" else "未连接"

            // 检查本地分支是否领先远程（可推送）
            var canPush = false
            try {
                val handler = GitLineHandler(project, repository.root, GitCommand.STATUS)
                handler.addParameters("-sb")
                val result = Git.getInstance().runCommand(handler)
                if (result.success()) {
                    // 例如：## feature/test...origin/feature/test [ahead 1]
                    canPush = result.output.any { it.contains("ahead") }
                }
            } catch (_: Exception) {}

            return GitStatusInfo(
                currentBranch = currentBranch,
                isFeatureBranch = isFeatureBranch,
                hasUncommittedChanges = hasUncommittedChanges,
                canPush = canPush,
                remoteStatus = remoteStatus
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 检查是否有合并冲突
     * 
     * @param project 项目实例
     * @return 冲突文件列表
     */
    fun checkConflicts(project: Project): List<String> {
        return try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return emptyList()
            val handler = GitLineHandler(project, repository.root, GitCommand.STATUS)
            handler.addParameters("--porcelain")
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) return emptyList()
            // 解析冲突文件（UU、AA、DD等）
            result.output.filter { 
                it.startsWith("UU") || it.startsWith("AA") || it.startsWith("DD")
            }.map { it.substring(3).trim() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 执行自动合并的核心逻辑
     */
    private fun performAutoMerge(
        project: Project,
        targetBranch: String,
        indicator: ProgressIndicator
    ): MergeResult {
        try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return MergeResult.failure("未找到Git仓库")
            
            val config = GitMergeHelperSettings.getInstance().getConfig()
            val originalBranch = repository.currentBranchName ?: ""
            
            // 1. 环境检查
            indicator.text = "检查环境..."
            indicator.fraction = 0.1
            val envCheckResult = checkEnvironment(project, repository, config, originalBranch)
            if (!envCheckResult.success) {
                return envCheckResult
            }
            
            // 2. 更新主分支
            indicator.text = "更新主分支..."
            indicator.fraction = 0.2
            val updateMainResult = updateMainBranch(project, repository, config.mainBranch)
            if (!updateMainResult.success) {
                return updateMainResult
            }
            
            // 3. 合并主分支到当前功能分支
            indicator.text = "合并主分支到功能分支..."
            indicator.fraction = 0.4
            val mergeMainResult = mergeMainToFeature(project, repository, config.mainBranch, originalBranch)
            if (!mergeMainResult.success) {
                return mergeMainResult
            }
            
            // 4. 推送功能分支
            indicator.text = "推送功能分支..."
            indicator.fraction = 0.6
            val pushFeatureResult = pushBranch(project, repository, originalBranch)
            if (!pushFeatureResult.success) {
                return pushFeatureResult
            }
            
            // 5. 合并功能分支到目标分支
            indicator.text = "合并到目标分支..."
            indicator.fraction = 0.8
            val mergeToTargetResult = mergeFeatureToTarget(project, repository, originalBranch, targetBranch)
            if (!mergeToTargetResult.success) {
                return mergeToTargetResult
            }
            
            // 6. 推送目标分支
            indicator.text = "推送目标分支..."
            indicator.fraction = 0.9
            val pushTargetResult = pushBranch(project, repository, targetBranch)
            if (!pushTargetResult.success) {
                return pushTargetResult
            }
            
            // 7. 切回原分支
            indicator.text = "完成..."
            indicator.fraction = 1.0
            checkoutBranch(project, repository, originalBranch)
            
            val mergedBranches = listOf(
                BranchMergeInfo(config.mainBranch, originalBranch, 0),
                BranchMergeInfo(originalBranch, targetBranch, 0)
            )
            
            return MergeResult.success(
                "自动合并完成！已将功能分支 '$originalBranch' 合并到目标分支 '$targetBranch'",
                mergedBranches
            )
            
        } catch (e: Exception) {
            return MergeResult.failure("合并过程中发生错误: ${e.message}")
        }
    }
    
    /**
     * 执行快速提交的核心逻辑
     */
    private fun performQuickCommit(
        project: Project,
        commitMessage: String,
        continueWithMerge: Boolean,
        targetBranch: String?,
        indicator: ProgressIndicator
    ): MergeResult {
        try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return MergeResult.failure("未找到Git仓库")
            
            // 1. 添加所有更改
            indicator.text = "添加文件..."
            indicator.fraction = 0.2
            val addResult = executeGitCommand(project, repository, GitCommand.ADD, listOf("."))
            if (!addResult.success) {
                return addResult
            }
            
            // 2. 提交更改
            indicator.text = "提交更改..."
            indicator.fraction = 0.6
            val commitResult = executeGitCommand(project, repository, GitCommand.COMMIT, listOf("-m", commitMessage))
            if (!commitResult.success) {
                return commitResult
            }
            
            // 3. 如果选择继续合并，执行自动合并
            if (continueWithMerge && targetBranch != null) {
                indicator.text = "继续执行合并..."
                indicator.fraction = 0.8
                return performAutoMerge(project, targetBranch, indicator)
            }
            
            indicator.fraction = 1.0
            return MergeResult.success("快速提交完成！")
            
        } catch (e: Exception) {
            return MergeResult.failure("提交过程中发生错误: ${e.message}")
        }
    }
    
    /**
     * 检查环境
     */
    private fun checkEnvironment(
        project: Project,
        repository: GitRepository,
        config: GitMergeConfig,
        currentBranch: String
    ): MergeResult {
        // 检查配置有效性
        val configValidation = config.validate()
        if (!configValidation.isValid) {
            return MergeResult.failure("配置无效: ${configValidation.errors.joinToString(", ")}")
        }
        
        // 检查是否为功能分支
        if (!config.isFeatureBranch(currentBranch)) {
            return MergeResult.failure("当前分支 '$currentBranch' 不是功能分支")
        }
        
        // 检查是否有未提交的更改
        val changeListManager = ChangeListManager.getInstance(project)
        val hasUncommittedChanges = changeListManager.allChanges.isNotEmpty()
        
        if (hasUncommittedChanges) {
            return MergeResult.failure("存在未提交的更改，请先提交或暂存")
        }
        
        return MergeResult.success("环境检查通过")
    }
    
    /**
     * 更新主分支
     */
    private fun updateMainBranch(
        project: Project,
        repository: GitRepository,
        mainBranch: String
    ): MergeResult {
        // 切换到主分支
        val checkoutResult = checkoutBranch(project, repository, mainBranch)
        if (!checkoutResult.success) {
            return checkoutResult
        }
        
        // 拉取最新更改
        return executeGitCommand(project, repository, GitCommand.PULL, emptyList())
    }
    
    /**
     * 合并主分支到功能分支
     */
    private fun mergeMainToFeature(
        project: Project,
        repository: GitRepository,
        mainBranch: String,
        featureBranch: String
    ): MergeResult {
        // 切换到功能分支
        val checkoutResult = checkoutBranch(project, repository, featureBranch)
        if (!checkoutResult.success) {
            return checkoutResult
        }
        // 合并主分支
        val mergeResult = executeGitCommand(project, repository, GitCommand.MERGE, listOf(mainBranch))
        if (!mergeResult.success) {
            // 检查是否为冲突导致
            val conflicts = checkConflicts(project)
            if (conflicts.isNotEmpty()) {
                return MergeResult.failure("合并出现冲突，请手动解决后再继续。冲突文件: ${conflicts.joinToString(", ")}")
            }
            return mergeResult
        }
        // 检查合并后是否有冲突
        val conflicts = checkConflicts(project)
        if (conflicts.isNotEmpty()) {
            return MergeResult.failure("合并出现冲突，请手动解决后再继续。冲突文件: ${conflicts.joinToString(", ")}")
        }
        return mergeResult
    }
    
    /**
     * 合并功能分支到目标分支
     */
    private fun mergeFeatureToTarget(
        project: Project,
        repository: GitRepository,
        featureBranch: String,
        targetBranch: String
    ): MergeResult {
        // 切换到目标分支
        val checkoutResult = checkoutBranch(project, repository, targetBranch)
        if (!checkoutResult.success) {
            return checkoutResult
        }
        // 合并功能分支
        val mergeResult = executeGitCommand(project, repository, GitCommand.MERGE, listOf(featureBranch))
        if (!mergeResult.success) {
            // 检查是否为冲突导致
            val conflicts = checkConflicts(project)
            if (conflicts.isNotEmpty()) {
                return MergeResult.failure("合并出现冲突，请手动解决后再继续。冲突文件: ${conflicts.joinToString(", ")}")
            }
            return mergeResult
        }
        // 检查合并后是否有冲突
        val conflicts = checkConflicts(project)
        if (conflicts.isNotEmpty()) {
            return MergeResult.failure("合并出现冲突，请手动解决后再继续。冲突文件: ${conflicts.joinToString(", ")}")
        }
        return mergeResult
    }
    
    /**
     * 切换分支
     */
    private fun checkoutBranch(
        project: Project,
        repository: GitRepository,
        branchName: String
    ): MergeResult {
        return executeGitCommand(project, repository, GitCommand.CHECKOUT, listOf(branchName))
    }
    
    /**
     * 推送分支
     */
    private fun pushBranch(
        project: Project,
        repository: GitRepository,
        branchName: String
    ): MergeResult {
        return executeGitCommand(project, repository, GitCommand.PUSH, listOf("origin", branchName))
    }
    
    /**
     * 执行Git命令
     */
    private fun executeGitCommand(
        project: Project,
        repository: GitRepository,
        command: GitCommand,
        args: List<String>
    ): MergeResult {
        try {
            log("执行Git命令: ${command.name()} ${args.joinToString(" ")}")
            val handler = GitLineHandler(project, repository.root, command)
            args.forEach { handler.addParameters(it) }
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                log("Git命令执行成功: ${command.name()}")
                return MergeResult.success("命令执行成功")
            } else {
                log("Git命令执行失败: ${command.name()}, 错误: ${result.errorOutputAsJoinedString}")
                return MergeResult.failure("命令执行失败: ${result.errorOutputAsJoinedString}")
            }
        } catch (e: VcsException) {
            log("Git命令执行异常: ${command.name()}, 异常: ${e.message}")
            return MergeResult.failure("Git命令执行异常: ${e.message}")
        }
    }

    /**
     * 解决冲突后自动提交并继续合并流程
     *
     * @param project 项目实例
     * @param commitMessage 提交信息（如：解决合并冲突）
     * @return 合并结果
     */
    fun resolveConflictsAndContinue(
        project: Project,
        commitMessage: String = "解决合并冲突"
    ): MergeResult {
        return try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return MergeResult.failure("未找到Git仓库")
            // 1. 添加所有更改
            val addResult = executeGitCommand(project, repository, GitCommand.ADD, listOf("."))
            if (!addResult.success) {
                return addResult
            }
            // 2. 提交更改
            val commitResult = executeGitCommand(project, repository, GitCommand.COMMIT, listOf("-m", commitMessage))
            if (!commitResult.success) {
                return commitResult
            }
            MergeResult.success("冲突已解决并提交，您可以继续后续合并流程。")
        } catch (e: Exception) {
            MergeResult.failure("解决冲突提交过程中发生错误: "+e.message)
        }
    }

    /**
     * 校验提交信息是否合法（非空且不超过100字符）
     */
    private fun isValidCommitMessage(msg: String): Boolean = msg.isNotBlank() && msg.length <= 100

    /**
     * 校验分支名是否合法（只允许字母、数字、/、_、-）
     */
    private fun isValidBranchName(name: String): Boolean = Regex("^[a-zA-Z0-9/_-]+$").matches(name)

    /**
     * 重置全局配置为默认
     */
    fun resetGlobalConfigToDefault() {
        GitMergeHelperSettings.getInstance().resetToDefault()
    }

    /**
     * 重置项目级配置为全局默认
     */
    fun resetProjectConfigToDefault(project: Project) {
        ProjectConfigService.getInstance(project).initFromGlobalConfig()
    }

    /**
     * 获取当前（全局）功能分支命名模式列表
     */
    fun getGlobalFeatureBranchPatterns(): List<String> {
        return GitMergeHelperSettings.getInstance().getConfig().featureBranchPatterns
    }

    /**
     * 添加全局功能分支命名模式
     */
    fun addGlobalFeatureBranchPattern(pattern: String): Boolean {
        val settings = GitMergeHelperSettings.getInstance()
        val config = settings.getConfig()
        if (pattern.isBlank() || config.featureBranchPatterns.contains(pattern)) return false
        config.featureBranchPatterns.add(pattern)
        settings.saveConfig(config)
        return true
    }

    /**
     * 删除全局功能分支命名模式
     */
    fun removeGlobalFeatureBranchPattern(pattern: String): Boolean {
        val settings = GitMergeHelperSettings.getInstance()
        val config = settings.getConfig()
        val removed = config.featureBranchPatterns.remove(pattern)
        if (removed) settings.saveConfig(config)
        return removed
    }

    /**
     * 获取项目级功能分支命名模式列表
     */
    fun getProjectFeatureBranchPatterns(project: Project): List<String> {
        return ProjectConfigService.getInstance(project).getProjectConfig().featureBranchPatterns
    }

    /**
     * 添加项目级功能分支命名模式
     */
    fun addProjectFeatureBranchPattern(project: Project, pattern: String): Boolean {
        val service = ProjectConfigService.getInstance(project)
        val config = service.getProjectConfig()
        if (pattern.isBlank() || config.featureBranchPatterns.contains(pattern)) return false
        config.featureBranchPatterns.add(pattern)
        service.saveProjectConfig(config)
        return true
    }

    /**
     * 删除项目级功能分支命名模式
     */
    fun removeProjectFeatureBranchPattern(project: Project, pattern: String): Boolean {
        val service = ProjectConfigService.getInstance(project)
        val config = service.getProjectConfig()
        val removed = config.featureBranchPatterns.remove(pattern)
        if (removed) service.saveProjectConfig(config)
        return removed
    }

    /**
     * 简单操作日志输出（后续可扩展为写入文件）
     * @param message 日志内容
     */
    private fun log(message: String) {
        println("[GitMergeHelper] $message")
    }
} 