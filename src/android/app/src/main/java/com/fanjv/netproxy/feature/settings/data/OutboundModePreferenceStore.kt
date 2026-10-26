package com.fanjv.netproxy.feature.settings.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 出站模式的界面偏好，存于管理器本地偏好，不写入模块配置。
 *
 * 应用分流默认允许广告（对应内核 RuleAllowAds）；去广告开关打开后，
 * 应用分流对应内核 Rule。开关只在应用分流下起作用，全局代理与直连不受影响。
 */
internal class OutboundModePreferenceStore(
    private val prefs: SharedPreferences
) {
    fun blockAdsInSplitMode(): Boolean = prefs.getBoolean("split_block_ads", false)

    fun setBlockAdsInSplitMode(enabled: Boolean) =
        prefs.edit { putBoolean("split_block_ads", enabled) }
}
