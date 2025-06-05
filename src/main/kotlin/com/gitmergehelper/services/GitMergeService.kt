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
    
    /**
     * 获取Git用户名
     * 
     * @param project 项目实例
     * @return Git用户名，如果获取失败返回null
     */
    fun getGitUserName(project: Project): String? {
        return try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return null
            
            val handler = GitLineHandler(project, repository.root, GitCommand.CONFIG)
            handler.addParameters("user.name")
            val result = Git.getInstance().runCommand(handler)
            
            if (result.success() && result.output.isNotEmpty()) {
                result.output.first().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            log("获取Git用户名失败: ${e.message}")
            null
        }
    }
    
    /**
     * 创建并切换到新分支
     * 
     * @param project 项目实例
     * @param branchName 新分支名称
     * @param callback 结果回调
     */
    fun createAndCheckoutBranch(
        project: Project,
        branchName: String,
        callback: (MergeResult) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            callback(MergeResult.failure("已有操作正在进行中，请稍后再试"))
            return
        }
        
        log("开始创建分支: $branchName")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建分支", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = performCreateAndCheckoutBranch(project, branchName, indicator)
                    log("创建分支结束，结果: ${result.message}")
                    callback(result)
                } finally {
                    isOperationInProgress.set(false)
                }
            }
        })
    }
    
    /**
     * 执行创建并切换分支的核心逻辑
     * 
     * @param project 项目实例
     * @param branchName 新分支名称
     * @param indicator 进度指示器
     * @return 创建结果
     */
    private fun performCreateAndCheckoutBranch(
        project: Project,
        branchName: String,
        indicator: ProgressIndicator
    ): MergeResult {
        try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return MergeResult.failure("未找到Git仓库")
            
            // 1. 检查分支是否已存在
            indicator.text = "检查分支是否存在..."
            indicator.fraction = 0.2
            
            if (branchExists(project, repository, branchName)) {
                return MergeResult.failure("分支 '$branchName' 已存在")
            }
            
            // 2. 创建新分支
            indicator.text = "创建新分支..."
            indicator.fraction = 0.5
            
            val createResult = executeGitCommand(
                project, 
                repository, 
                GitCommand.CHECKOUT, 
                listOf("-b", branchName)
            )
            
            if (!createResult.success) {
                return MergeResult.failure("创建分支失败: ${createResult.message}")
            }
            
            // 3. 验证分支创建成功
            indicator.text = "验证分支创建..."
            indicator.fraction = 0.8
            
            val currentBranch = repository.currentBranchName
            if (currentBranch != branchName) {
                return MergeResult.failure("分支创建失败，当前分支: $currentBranch")
            }
            
            indicator.text = "分支创建完成"
            indicator.fraction = 1.0
            
            return MergeResult.success("成功创建并切换到分支 '$branchName'")
            
        } catch (e: Exception) {
            log("创建分支过程中发生异常: ${e.message}")
            return MergeResult.failure("创建分支过程中发生异常: ${e.message}")
        }
    }
    
    /**
     * 检查分支是否存在
     * 
     * @param project 项目实例
     * @param repository Git仓库
     * @param branchName 分支名称
     * @return 分支是否存在
     */
    private fun branchExists(project: Project, repository: GitRepository, branchName: String): Boolean {
        return try {
            // 检查本地分支
            val localHandler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            localHandler.addParameters("--list", branchName)
            val localResult = Git.getInstance().runCommand(localHandler)
            
            if (localResult.success() && localResult.output.any { 
                val cleanBranch = it.trim().removePrefix("*").trim()
                cleanBranch == branchName 
            }) {
                log("发现本地分支: $branchName")
                return true
            }
            
            // 检查远程分支
            val remoteHandler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            remoteHandler.addParameters("-r", "--list", "origin/$branchName")
            val remoteResult = Git.getInstance().runCommand(remoteHandler)
            
            if (remoteResult.success() && remoteResult.output.any { 
                it.trim().contains(branchName)
            }) {
                log("发现远程分支: origin/$branchName")
                return true
            }
            
            log("分支不存在: $branchName")
            false
            
        } catch (e: Exception) {
            log("检查分支存在性失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取所有分支列表
     * 
     * @param project 项目实例
     * @return 分支名称列表
     */
    fun getAllBranches(project: Project): List<String> {
        return try {
            log("开始获取分支列表...")
            
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
            if (repository == null) {
                log("未找到Git仓库")
                return emptyList()
            }
            
            log("找到Git仓库: ${repository.root.path}")
            
            // 首先获取本地分支
            val localBranches = getLocalBranches(project, repository)
            log("获取到本地分支: $localBranches")
            
            // 然后尝试获取远程分支（如果失败，只返回本地分支）
            val remoteBranches = try {
                getRemoteBranches(project, repository)
            } catch (e: Exception) {
                log("获取远程分支失败，将只使用本地分支: ${e.message}")
                emptyList()
            }
            log("获取到远程分支: $remoteBranches")
            
            // 合并并去重
            val allBranches = (localBranches + remoteBranches)
                .filter { branch ->
                    branch.isNotEmpty() && 
                    !branch.contains("HEAD") && 
                    !branch.contains("->")
                }
                .distinct()
                .sorted()
            
            log("合并后的分支列表: $allBranches")
            
            // 确保至少返回一些分支，优先使用所有获取到的分支
            if (allBranches.isNotEmpty()) {
                return allBranches
            }
            
            // 如果都失败了，尝试使用更简单的方式获取分支列表
            log("尝试使用简单方式获取分支列表...")
            val simpleBranches = getSimpleBranchList(project, repository)
            if (simpleBranches.isNotEmpty()) {
                log("简单方式获取到分支: $simpleBranches")
                return simpleBranches
            }
            
            log("警告：未获取到任何分支，返回当前分支")
            // 最后才fallback到当前分支
            val currentBranch = repository.currentBranchName
            if (currentBranch != null) {
                log("返回当前分支作为默认: $currentBranch")
                return listOf(currentBranch)
            }
            
            emptyList()
            
        } catch (e: Exception) {
            log("获取分支列表发生异常: ${e.message}")
            log("异常堆栈: ${e.stackTraceToString()}")
            emptyList()
        }
    }
    
    /**
     * 获取本地分支列表
     */
    private fun getLocalBranches(project: Project, repository: GitRepository): List<String> {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            val result = Git.getInstance().runCommand(handler)
            
            if (result.success()) {
                result.output.map { line ->
                    line.trim()
                        .removePrefix("*")
                        .trim()
                }.filter { it.isNotEmpty() }
            } else {
                log("获取本地分支失败: ${result.errorOutputAsJoinedString}")
                emptyList()
            }
        } catch (e: Exception) {
            log("获取本地分支异常: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取远程分支列表
     */
    private fun getRemoteBranches(project: Project, repository: GitRepository): List<String> {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            handler.addParameters("-r") // 只获取远程分支
            val result = Git.getInstance().runCommand(handler)
            
            if (result.success()) {
                result.output.map { line ->
                    line.trim()
                        .removePrefix("origin/")
                        .removePrefix("remotes/origin/")
                }.filter { branch ->
                    branch.isNotEmpty() && !branch.contains("HEAD")
                }
            } else {
                log("获取远程分支失败: ${result.errorOutputAsJoinedString}")
                emptyList()
            }
        } catch (e: Exception) {
            log("获取远程分支异常: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取当前分支名称
     * 
     * @param project 项目实例
     * @return 当前分支名称，如果获取失败返回null
     */
    fun getCurrentBranch(project: Project): String? {
        return try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return null
            
            repository.currentBranchName
        } catch (e: Exception) {
            log("获取当前分支失败: ${e.message}")
            null
        }
    }
    
    /**
     * 基于指定分支创建并切换到新分支
     * 
     * @param project 项目实例
     * @param branchName 新分支名称
     * @param baseBranch 基分支名称
     * @param callback 结果回调
     */
    fun createAndCheckoutBranchFromBase(
        project: Project,
        branchName: String,
        baseBranch: String,
        callback: (MergeResult) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            callback(MergeResult.failure("已有操作正在进行中，请稍后再试"))
            return
        }
        
        log("开始基于分支 '$baseBranch' 创建分支: $branchName")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建分支", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = performCreateAndCheckoutBranchFromBase(project, branchName, baseBranch, indicator)
                    log("创建分支结束，结果: ${result.message}")
                    callback(result)
                } finally {
                    isOperationInProgress.set(false)
                }
            }
        })
    }
    
    /**
     * 执行基于指定分支创建并切换分支的核心逻辑
     * 
     * @param project 项目实例
     * @param branchName 新分支名称
     * @param baseBranch 基分支名称
     * @param indicator 进度指示器
     * @return 创建结果
     */
    private fun performCreateAndCheckoutBranchFromBase(
        project: Project,
        branchName: String,
        baseBranch: String,
        indicator: ProgressIndicator
    ): MergeResult {
        try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
                ?: return MergeResult.failure("未找到Git仓库")
            
            // 1. 检查新分支是否已存在
            indicator.text = "检查分支是否存在..."
            indicator.fraction = 0.1
            
            if (branchExists(project, repository, branchName)) {
                return MergeResult.failure("分支 '$branchName' 已存在")
            }
            
            // 2. 验证基分支是否存在
            indicator.text = "验证基分支..."
            indicator.fraction = 0.2
            
            if (!branchExists(project, repository, baseBranch)) {
                return MergeResult.failure("基分支 '$baseBranch' 不存在")
            }
            
            // 3. 切换到基分支
            indicator.text = "切换到基分支..."
            indicator.fraction = 0.4
            
            val checkoutBaseResult = executeGitCommand(
                project, 
                repository, 
                GitCommand.CHECKOUT, 
                listOf(baseBranch)
            )
            
            if (!checkoutBaseResult.success) {
                return MergeResult.failure("切换到基分支失败: ${checkoutBaseResult.message}")
            }
            
            // 4. 从基分支创建新分支
            indicator.text = "创建新分支..."
            indicator.fraction = 0.7
            
            val createResult = executeGitCommand(
                project, 
                repository, 
                GitCommand.CHECKOUT, 
                listOf("-b", branchName)
            )
            
            if (!createResult.success) {
                return MergeResult.failure("创建分支失败: ${createResult.message}")
            }
            
            // 5. 验证分支创建成功
            indicator.text = "验证分支创建..."
            indicator.fraction = 0.9
            
            // 使用git命令获取当前分支名，而不是依赖repository对象
            val currentBranch = getCurrentBranchByCommand(project, repository)
            if (currentBranch != branchName) {
                return MergeResult.failure("分支创建失败，当前分支: $currentBranch，期望分支: $branchName")
            }
            
            indicator.text = "分支创建完成"
            indicator.fraction = 1.0
            
            return MergeResult.success("成功基于分支 '$baseBranch' 创建并切换到分支 '$branchName'")
            
        } catch (e: Exception) {
            log("创建分支过程中发生异常: ${e.message}")
            return MergeResult.failure("创建分支过程中发生异常: ${e.message}")
        }
    }

    /**
     * 使用git命令获取当前分支名
     * 
     * @param project 项目实例
     * @param repository Git仓库
     * @return 当前分支名称
     */
    private fun getCurrentBranchByCommand(project: Project, repository: GitRepository): String? {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            handler.addParameters("--show-current")
            val result = Git.getInstance().runCommand(handler)
            
            if (result.success() && result.output.isNotEmpty()) {
                result.output.first().trim()
            } else {
                // 如果上面的命令不支持，使用传统方式
                val handler2 = GitLineHandler(project, repository.root, GitCommand.BRANCH)
                val result2 = Git.getInstance().runCommand(handler2)
                
                if (result2.success()) {
                    result2.output.find { it.startsWith("*") }?.trim()?.removePrefix("*")?.trim()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            log("获取当前分支命令失败: ${e.message}")
            null
        }
    }

    /**
     * 简单方式获取分支列表（不区分本地和远程）
     */
    private fun getSimpleBranchList(project: Project, repository: GitRepository): List<String> {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
            handler.addParameters("-a") // 获取所有分支（本地和远程）
            val result = Git.getInstance().runCommand(handler)
            
            if (result.success()) {
                result.output.map { line ->
                    line.trim()
                        .removePrefix("*")
                        .removePrefix("remotes/origin/")
                        .removePrefix("origin/")
                        .trim()
                }.filter { branch ->
                    branch.isNotEmpty() && 
                    !branch.contains("HEAD") &&
                    !branch.contains("->")
                }.distinct()
            } else {
                log("简单方式获取分支失败: ${result.errorOutputAsJoinedString}")
                emptyList()
            }
        } catch (e: Exception) {
            log("简单方式获取分支异常: ${e.message}")
            emptyList()
        }
    }
} 