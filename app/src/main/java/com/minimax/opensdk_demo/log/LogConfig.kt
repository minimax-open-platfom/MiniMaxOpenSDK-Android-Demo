package com.minimax.opensdk_demo.log


/**
 *  Created by Rain on 2022/12/14
 *
 */


/**
 * 日志Print配置
 * [stackTraceEnable] 是否打印堆栈
 * [syncToSever] 是否上传到server
 */
class LogConfig(
    val stackTraceEnable: Boolean = false,
    val syncToSever: Boolean = false
)