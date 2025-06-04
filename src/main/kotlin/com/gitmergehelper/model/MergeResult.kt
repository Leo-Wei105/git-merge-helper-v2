package com.gitmergehelper.model

/**
 * 合并操作结果
 * 
 * @property success 操作是否成功
 * @property message 结果消息
 * @property conflictFiles 冲突文件列表（如果有冲突）
 * @property mergedBranches 已合并的分支信息
 */
data class MergeResult(
    val success: Boolean,
    val message: String,
    val conflictFiles: List<String> = emptyList(),
    val mergedBranches: List<BranchMergeInfo> = emptyList()
) {
    companion object {
        /**
         * 创建成功结果
         * 
         * @param message 成功消息
         * @param mergedBranches 合并的分支信息
         * @return 成功的MergeResult
         */
        fun success(message: String, mergedBranches: List<BranchMergeInfo> = emptyList()): MergeResult {
            return MergeResult(true, message, emptyList(), mergedBranches)
        }
        
        /**
         * 创建失败结果
         * 
         * @param message 失败消息
         * @return 失败的MergeResult
         */
        fun failure(message: String): MergeResult {
            return MergeResult(false, message)
        }
        
        /**
         * 创建有冲突的结果
         * 
         * @param message 冲突消息
         * @param conflictFiles 冲突文件列表
         * @return 有冲突的MergeResult
         */
        fun conflict(message: String, conflictFiles: List<String>): MergeResult {
            return MergeResult(false, message, conflictFiles)
        }
    }
}

/**
 * 分支合并信息
 * 
 * @property fromBranch 源分支
 * @property toBranch 目标分支
 * @property commitCount 合并的提交数量
 * @property timestamp 合并时间戳
 */
data class BranchMergeInfo(
    val fromBranch: String,
    val toBranch: String,
    val commitCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Git操作状态
 */
enum class GitOperationStatus {
    /** 准备中 */
    PREPARING,
    /** 检查环境 */
    CHECKING_ENVIRONMENT,
    /** 更新主分支 */
    UPDATING_MAIN_BRANCH,
    /** 合并主分支到功能分支 */
    MERGING_MAIN_TO_FEATURE,
    /** 推送功能分支 */
    PUSHING_FEATURE_BRANCH,
    /** 合并功能分支到目标分支 */
    MERGING_FEATURE_TO_TARGET,
    /** 推送目标分支 */
    PUSHING_TARGET_BRANCH,
    /** 清理工作 */
    CLEANING_UP,
    /** 完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 有冲突 */
    CONFLICT
}

/**
 * Git状态信息
 * 
 * @property currentBranch 当前分支
 * @property isFeatureBranch 是否为功能分支
 * @property hasUncommittedChanges 是否有未提交的更改
 * @property canPush 是否可以推送
 * @property remoteStatus 远程状态
 */
data class GitStatusInfo(
    val currentBranch: String,
    val isFeatureBranch: Boolean,
    val hasUncommittedChanges: Boolean,
    val canPush: Boolean,
    val remoteStatus: String
) 