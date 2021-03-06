package org.tvheadend.tvhclient.ui.features.dvr.series_recordings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.data.repository.AppRepository
import org.tvheadend.tvhclient.domain.entity.Channel
import org.tvheadend.tvhclient.domain.entity.SeriesRecording
import org.tvheadend.tvhclient.domain.entity.ServerProfile
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class SeriesRecordingViewModel(application: Application) : AndroidViewModel(application) {

    @Inject
    lateinit var appContext: Context
    @Inject
    lateinit var appRepository: AppRepository
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    var recording = SeriesRecording()
    val recordings: LiveData<List<SeriesRecording>>
    var recordingProfileNameId: Int = 0

    var isTimeEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                startTimeInMillis = Calendar.getInstance().timeInMillis
                startWindowTimeInMillis = Calendar.getInstance().timeInMillis
            }
        }

    init {
        MainApplication.getComponent().inject(this)
        recordings = appRepository.seriesRecordingData.getLiveDataItems()
    }

    fun getRecordingById(id: String): LiveData<SeriesRecording> {
        return appRepository.seriesRecordingData.getLiveDataItemById(id)
    }

    fun loadRecordingByIdSync(id: String) {
        recording = appRepository.seriesRecordingData.getItemById(id)
        // In case one of the values is negative the time setting shall be disabled
        isTimeEnabled = recording.start >= 0 && recording.startWindow >= 0
    }

    var startTimeInMillis: Long = 0
        get() {
            Timber.d("Get time in millis is ${recording.startTimeInMillis}, start minutes are ${recording.start}")
            return recording.startTimeInMillis
        }
        set(milliSeconds) {
            field = milliSeconds
            recording.start = getMinutesFromTime(milliSeconds)
        }

    var startWindowTimeInMillis: Long = 0
        get() {
            Timber.d("Get time in millis is ${recording.startWindowTimeInMillis}, start minutes are ${recording.startWindow}")
            return recording.startWindowTimeInMillis
        }
        set(milliSeconds) {
            field = milliSeconds
            recording.startWindow = getMinutesFromTime(milliSeconds)
        }

    /**
     * The start and stop time handling is done in milliseconds within the app, but the
     * server requires and provides minutes instead. In case the start and stop times of
     * a recording need to be updated the milliseconds will be converted to minutes.
     */
    private fun getMinutesFromTime(milliSeconds: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val minutes = (hour * 60 + minute).toLong()
        Timber.d("Set time in millis is $milliSeconds, start minutes are $minutes")
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
