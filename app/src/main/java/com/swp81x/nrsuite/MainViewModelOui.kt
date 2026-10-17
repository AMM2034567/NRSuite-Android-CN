package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import kotlinx.coroutines.launch

internal const val PREF_OUI_DB_PROMPTED = "oui_db_prompted"

internal fun MainViewModel.loadOuiDatabaseImpl() {
    scope.launch {
        ouiDatabaseRepository.load()
        if (ouiDatabaseRepository.status.value is OuiDatabaseStatus.NotDownloaded &&
            !preferences.getBoolean(PREF_OUI_DB_PROMPTED, false)
        ) {
            _requiresOuiDatabase.value = true
        }
    }
}

internal fun MainViewModel.downloadOuiDatabaseImpl() {
    _requiresOuiDatabase.value = false
    preferences.edit().putBoolean(PREF_OUI_DB_PROMPTED, true).apply()
    appendLog("正在下载 OUI/厂商数据库...")

    scope.launch {
        ouiDatabaseRepository.download()
        when (val status = ouiDatabaseRepository.status.value) {
            is OuiDatabaseStatus.Ready -> {
                appendLog("OUI/厂商数据库就绪：共 ${status.vendorCount} 个前缀。")
                addHistory(
                    "oui_db",
                    "OUI/厂商数据库已更新：共 ${status.vendorCount} 个前缀",
                    HistoryLevel.SUCCESS,
                )
            }
            is OuiDatabaseStatus.Error -> {
                appendLog("OUI/厂商数据库更新失败：${status.message}", level = com.swp81x.nrsuite.core.log.LogLevel.ERROR)
            }
            else -> Unit
        }
    }
}

internal fun MainViewModel.onOuiDatabasePromptShownImpl() {
    _requiresOuiDatabase.value = false
    preferences.edit().putBoolean(PREF_OUI_DB_PROMPTED, true).apply()
}

internal fun MainViewModel.lookupMacImpl(mac: String) {
    _macLookupResult.value = ouiDatabaseRepository.detailedLookup(mac)
}
