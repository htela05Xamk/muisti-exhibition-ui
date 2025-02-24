package fi.metatavu.muisti.exhibitionui.views

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.metatavu.muisti.exhibitionui.persistence.ExhibitionUIDatabase
import fi.metatavu.muisti.exhibitionui.persistence.repository.DeviceSettingRepository
import fi.metatavu.muisti.exhibitionui.settings.DeviceSettings
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * View model for settings activity
 *
 * @param application application instance
 */
class SetupViewModel(application: Application): AndroidViewModel(application) {

    private val deviceSettingRepository: DeviceSettingRepository

    init {
        val deviceSettingDao = ExhibitionUIDatabase.getDatabase().deviceSettingDao()
        deviceSettingRepository = DeviceSettingRepository(deviceSettingDao)
    }

    /**
     * Sets device key
     *
     * @param deviceId identifier of the device
     */
    suspend fun setDeviceKey(deviceKey: String) = viewModelScope.launch {
        DeviceSettings.setDeviceKey(deviceKey)
    }

    /**
     * Sets the device ID
     *
     * @param deviceId Device ID to set
     */
    suspend fun setDeviceId(deviceId: UUID) = viewModelScope.launch {
        DeviceSettings.setDeviceId(deviceId)
    }
    /**
     * Returns device id if set
     *
     * @return device id or null if not set
     */
    suspend fun getDeviceId(): UUID? {
        return DeviceSettings.getDeviceId()
    }

}