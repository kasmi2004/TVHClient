package org.tvheadend.tvhclient.ui.features.dvr.timer_recordings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.data.repository.AppRepository
import org.tvheadend.tvhclient.domain.entity.Channel
import org.tvheadend.tvhclient.domain.entity.ServerProfile
import org.tvheadend.tvhclient.domain.entity.TimerRecording
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class TimerRecordingViewModel(application: Application) : AndroidViewModel(application) {

    @Inject
    lateinit var appContext: Context
    @Inject
    lateinit var appRepository: AppRepository
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    var recording = TimerRecording()
    val recordings: LiveData<List<TimerRecording>>
    var recordingProfileNameId: Int = 0

    var isTimeEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                startTimeInMillis = Calendar.getInstance().timeInMillis
                stopTimeInMillis = Calendar.getInstance().timeInMillis
            }
        }

    init {
        MainApplication.getComponent().inject(this)
        recordings = appRepository.timerRecordingData.getLiveDataItems()
    }

    fun getRecordingById(id: String): LiveData<TimerRecording> {
        return appRepository.timerRecordingData.getLiveDataItemById(id)
    }

    fun loadRecordingByIdSync(id: String) {
        recording = appRepository.timerRecordingData.getItemById(id)
    }

    var startTimeInMillis: Long = 0
        get() {
            return recording.startTimeInMillis
        }
        set(milliSeconds) {
            field = milliSeconds
            recording.start = getMinutesFromTime(milliSeconds)
        }

    var stopTimeInMillis: Long = 0
        get() {
            return recording.stopTimeInMillis
        }
        set(milliSeconds) {
            field = milliSeconds
            recording.stop = getMinutesFromTime(milliSeconds)
        }

    /**
     * The start and stop time handling is done in milliseconds within the app, but the
     * server requires and provides minutes instead. In case the start and stop times of
     * a recording need to be updated the milliseconds will be converted to minutes.
     */
    private fun getMinutesFromTime(milliSeconds : Long) : Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val minutes = (hour * 60 + minute).toLong()
        Timber.d("Time in millis is $milliSeconds, start minutes are $minutes")
        return minutes
    }

    fun getChannelList(): List<Channel> {
        val defaultChannelSortOrder = appContext.resources.getString(R.string.pref_default_channel_sort_order)
        val channelSortOrder = Integer.valueOf(sharedPreferences.getString("channel_sort_order", defaultChannelSortOrder) ?: defaultChannelSortOrder)
        return appRepository.channelData.getChannels(channelSortOrder)
    }

    fun getRecordingProfileNames(): Array<String> {
        return appRepository.serverProfileData.recordingProfileNames
    }

    fun getRecordingProfile(): ServerProfile? {
        return appRepository.serverProfileData.getItemById(appRepository.serverStatusData.activeItem.recordingServerProfileId)
    }
}
