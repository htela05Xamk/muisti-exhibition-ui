package fi.metatavu.muisti.exhibitionui.persistence.model

/**
 * Enumeration that describe device settings
 *
 */
enum class DeviceSettingName {

    /**
     * Setting for storing device's exhibition id
     */
    EXHIBITION_ID,

    /**
     * Setting for storing device's id
     */
    DEVICE_ID,

    /**
     * Setting for storing device's key
     */
    DEVICE_KEY,

    /**
     * Setting for storing device's exhibition device id
     */
    EXHIBITION_DEVICE_ID,

    /**
     * Setting for storing device's rfid device id
     */
    EXHIBITION_RFID_DEVICE,

    /**
     * Setting for storing device's rfid antenna id
     */
    EXHIBITION_RFID_ANTENNA,

    /**
     * Setting for storing device rotation flip boolean
     */
    DEVICE_ROTATE_FLIP,

    /**
     * Setting for storing force video play boolean
     */
    FORCE_VIDEO_PLAY

}