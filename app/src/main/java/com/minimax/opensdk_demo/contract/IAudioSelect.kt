package com.minimax.opensdk_demo.contract

import android.app.Activity
import android.content.Intent
import com.minimax.opensdk_demo.log.Logger
import com.minimax.opensdk_demo.MainActivity

/**
 *  Created by xijue on 2024/3/8
 *
 */
interface IAudioSelect {

    fun Activity.selectAudio()

    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): String?
}

class AudioSelect : IAudioSelect {

    val PICK_AUDIO = 1

    override fun Activity.selectAudio() {
        // 生成选择音频文件的代码
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        startActivityForResult(intent, PICK_AUDIO)
    }

    override fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): String? {
        if (requestCode == PICK_AUDIO && resultCode == Activity.RESULT_OK) {
            // 处理选择音频文件的结果
            val uri = data?.data
            Logger.i(MainActivity.LOG_TAG) {
                "audio file uri: $uri"
            }
            return uri?.toString()

        }
        return null
    }

}