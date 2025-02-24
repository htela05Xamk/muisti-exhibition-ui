package fi.metatavu.muisti.exhibitionui.views

import android.os.Bundle
import android.os.Handler
import androidx.lifecycle.Observer
import fi.metatavu.muisti.api.client.models.VisitorSessionV2
import fi.metatavu.muisti.exhibitionui.ExhibitionUIApplication
import fi.metatavu.muisti.exhibitionui.R
import fi.metatavu.muisti.exhibitionui.pages.PageViewContainer
import fi.metatavu.muisti.exhibitionui.settings.DeviceSettings
import fi.metatavu.muisti.exhibitionui.visitors.VisitorSessionContainer
import kotlinx.android.synthetic.main.activity_page.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Main activity class
 */
class MainActivity : MuistiActivity() {

    private var handler: Handler = Handler()

    private val visitorSessionObserver = Observer<VisitorSessionV2?> {
        onVisitorSessionChange(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VisitorSessionContainer.getLiveVisitorSession().observe(this, visitorSessionObserver)
        GlobalScope.launch {
            val deviceId = DeviceSettings.getDeviceId()
            val deviceKey = DeviceSettings.getDeviceKey()

            if (deviceId == null || deviceKey == null) {
                startSetupActivity()
            } else {
                val idlePage = when (val pageId = muistiViewModel?.getIdlePageId()) {
                    null -> null
                    else -> PageViewContainer.getPageView(pageId)
                }

                runOnUiThread {
                    if (idlePage != null) {
                        setContentView(R.layout.activity_page)
                        openView(idlePage)
                    } else {
                        setContentView(R.layout.activity_main)
                    }
                    if (ExhibitionUIApplication.instance.forcedPortraitMode == true) {
                        setForcedPortraitMode()
                    }

                    setImmersiveMode()
                    listenLoginButton(login_button)
                    listenSettingsButton(settings_button)
                    waitForForcedPortraitMode(idlePage?.orientation)
                }
            }
        }
    }

    override fun onDestroy() {
        VisitorSessionContainer.getLiveVisitorSession().removeObserver(visitorSessionObserver)
        removeSettingsAndIndexListeners()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        ExhibitionUIApplication.instance.readApiValues()
    }

    /**
     * Checks if forced portrait mode setting is loaded,
     * if true forcces portrait otherwise sets the specified orientation.
     *
     * @param orientation orientation to set if portrait mode is not forced.
     */
    private fun waitForForcedPortraitMode(orientation: Int?) {
        handler.postDelayed({
            if (ExhibitionUIApplication.instance.forcedPortraitMode == null) {
                waitForForcedPortraitMode(orientation)
            } else {
                if(ExhibitionUIApplication.instance.forcedPortraitMode == true) {
                    setForcedPortraitMode()
                } else {
                    requestedOrientation = orientation ?: return@postDelayed
                }
            }
        }, 500)
    }

    /**
     * Handler for visitor session changes
     *
     * @param visitorSession visitor session
     */
    private fun onVisitorSessionChange(visitorSession: VisitorSessionV2?) {
        visitorSession ?: return

        GlobalScope.launch {
            goToIndexPage(visitorSession)
        }
    }

}
