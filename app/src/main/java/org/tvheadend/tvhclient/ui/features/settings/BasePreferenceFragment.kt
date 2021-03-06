package org.tvheadend.tvhclient.ui.features.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.domain.entity.ServerStatus
import org.tvheadend.tvhclient.ui.base.BaseActivity
import org.tvheadend.tvhclient.ui.common.callbacks.ToolbarInterface

abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    lateinit var toolbarInterface: ToolbarInterface
    lateinit var sharedPreferences: SharedPreferences
    lateinit var settingsViewModel: SettingsViewModel

    protected var isUnlocked: Boolean = false
    protected var htspVersion: Int = 13
    lateinit var serverStatus: ServerStatus

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (activity is ToolbarInterface) {
            toolbarInterface = activity as ToolbarInterface
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        settingsViewModel = ViewModelProviders.of(activity as BaseActivity).get(SettingsViewModel::class.java)

        serverStatus = settingsViewModel.serverStatus
        htspVersion = serverStatus.htspVersion
        isUnlocked = MainApplication.getInstance().isUnlocked
    }
}
