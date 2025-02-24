package fi.metatavu.muisti.exhibitionui.views

import android.animation.TimeInterpolator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.util.Pair
import fi.metatavu.muisti.exhibitionui.ExhibitionUIApplication
import fi.metatavu.muisti.exhibitionui.pages.PageView
import kotlinx.android.synthetic.main.activity_page.*
import fi.metatavu.muisti.exhibitionui.visitors.VisitorSessionContainer
import java.util.*
import kotlin.math.max
import android.os.PersistableBundle
import android.transition.Fade
import android.transition.Visibility
import android.util.Log
import android.view.*
import android.view.animation.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import com.github.rongi.rotate_layout.layout.RotateLayout
import fi.metatavu.muisti.api.client.models.*
import fi.metatavu.muisti.api.client.models.Animation
import fi.metatavu.muisti.exhibitionui.BuildConfig
import fi.metatavu.muisti.exhibitionui.R
import fi.metatavu.muisti.exhibitionui.actions.PageActionProvider
import fi.metatavu.muisti.exhibitionui.actions.PageActionProviderFactory
import fi.metatavu.muisti.exhibitionui.mqtt.MqttClientController
import fi.metatavu.muisti.exhibitionui.mqtt.MqttTopicListener
import fi.metatavu.muisti.exhibitionui.pages.PageViewContainer
import fi.metatavu.muisti.exhibitionui.settings.DeviceSettings
import fi.metatavu.muisti.exhibitionui.visitors.VisibleTagsContainer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muisti activity abstract class
 */
abstract class MuistiActivity : AppCompatActivity() {

    protected var muistiViewModel: MuistiViewModel? = null
    private val handler: Handler = Handler()
    private val deviceGroupEvents = mutableMapOf<String, Array<ExhibitionPageEvent>>()
    private val keyDownListeners = mutableListOf<KeyCodeListener>()
    private val keyUpListeners = mutableListOf<KeyCodeListener>()
    private var buttonClickCounter = 0
    private val indexClickCounterHandler = Handler()
    private val settingsClickCounterHandler = Handler()
    private val loginClickCounterHandler = Handler()
    protected var currentPageView: PageView? = null
    val transitionElements: MutableList<View> = mutableListOf()
    var pageInteractable = false
    var transitionTime = 300L

    // TODO: Listen only device group messages
    private val mqttTriggerDeviceGroupEventListener = MqttTopicListener("${BuildConfig.MQTT_BASE_TOPIC}/events/deviceGroup/deviceGroupId", MqttTriggerDeviceGroupEvent::class.java) {
        val key = it.event
        if (key != null) {
            val events = deviceGroupEvents.get(key)
            if (events != null) {
                triggerEvents(events)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        muistiViewModel = ViewModelProvider(this).get(MuistiViewModel::class.java)
    }

    override fun onDestroy() {
        this.releaseView(currentPageView?.view)
        super.onDestroy()
        currentPageView?.lifecycleListeners?.forEach { it.onDestroy() }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        currentPageView?.lifecycleListeners?.forEach { it.onLowMemory() }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        currentPageView?.lifecycleListeners?.forEach { it.onSaveInstanceState(outState) }
    }

    override fun finish() {
        disableClickEvents(currentPageView?.page?.eventTriggers)
        this.closeView()

        if (getCurrentActivity() == this){
            setCurrentActivity(null)
        }

        currentPageView?.lifecycleListeners?.forEach { it.onPause() }

        super.finish()
    }

    override fun onResume() {
        if (ExhibitionUIApplication.instance.forcedPortraitMode == true) {
            setForcedPortraitMode()
        }
        super.onResume()

        currentPageView?.lifecycleListeners?.forEach { it.onResume() }
        val activity = this
        GlobalScope.launch {
            val previous = getCurrentActivity()
            delay(transitionTime)
            previous?.finish()
            pageInteractable = true
            setCurrentActivity(activity)
        }
    }

    override fun onPause() {
        super.onPause()

        this.closeView()

        currentPageView?.lifecycleListeners?.forEach { it.onPause() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        triggerKeyListeners(keyCode, keyDown = true)
        super.onKeyDown(keyCode, event)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        triggerKeyListeners(keyCode, keyDown = false)
        super.onKeyUp(keyCode, event)
        return true
    }

    /**
     * Navigates visitor to index page
     *
     * @param visitorSession visitor session
     */
    suspend fun goToIndexPage(visitorSession: VisitorSessionV2) {
        val indexPage = muistiViewModel?.getIndexPage(
            language = visitorSession.language,
            visitorSession = visitorSession
        )

        if (indexPage == null) {
            Log.d(javaClass.name, "Could not find index page")
            return
        }

        waitForPage(indexPage)
    }

    /**
     * Finds a view with tag
     *
     * @param tag tag
     * @return view or null if not found
     */
    fun findView(tag: String?): View? {
        return findViewWithTag(tag)
    }

    /**
     * Applies page transitions
     *
     * @param pageView page view object
     */
    protected fun applyPageTransitions(pageView: PageView) {
        val context = this

        val pageEnterTransitions = pageView.page.enterTransitions.mapNotNull { context.getPageAnimation(it.transition, Visibility.MODE_IN) }
        val pageExitTransitions = pageView.page.exitTransitions.mapNotNull { context.getPageAnimation(it.transition, Visibility.MODE_OUT) }

        with(window) {
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            allowEnterTransitionOverlap = true
            enterTransition = if(pageEnterTransitions.isNotEmpty()){
                pageEnterTransitions[0]
            } else {
                null
            }
            exitTransition  = if(pageExitTransitions.isNotEmpty()){
                pageExitTransitions[0]
            } else {
                null
            }
        }
    }

    /**
     * Opens a view
     *
     * Method adds page view as a child of page activity view and
     * applies page triggers into the page
     *
     * @param pageView
     */
    protected open fun openView(pageView: PageView) {
        pageView.view.layoutParams?.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        pageView.view.layoutParams?.width = ConstraintLayout.LayoutParams.MATCH_PARENT

        releaseView(pageView.view)
        currentPageView = pageView
        this.root.addView(pageView.view)
        setSharedElementTransitions(pageView.page.enterTransitions)
        setSharedElementTransitions(pageView.page.exitTransitions)

        if (ExhibitionUIApplication.instance.forcedPortraitMode != true) {
            requestedOrientation = pageView.orientation
        }

        MqttClientController.addListener(mqttTriggerDeviceGroupEventListener)
        pageView.lifecycleListeners.forEach { it.onPageActivate(this) }
        applyEventTriggers(pageView.page.eventTriggers)
    }

    /**
     * Closes a view
     *
     * Method cancels all pending scheduled events
     */
    private fun closeView() {
        MqttClientController.removeListener(mqttTriggerDeviceGroupEventListener)
        handler.removeCallbacksAndMessages(null)
        currentPageView?.lifecycleListeners?.forEach { it.onPageDeactivate(this) }
        removeSettingsAndIndexListeners()
    }

    /**
     * Changes activity to use immersive mode
     */
    protected fun setImmersiveMode() {
        supportActionBar?.hide()

        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }

    /**
     * Applies event triggers
     *
     * @param eventTriggers event triggers to be applied
     */
    private fun applyEventTriggers(eventTriggers: Array<ExhibitionPageEventTrigger>) {
        deviceGroupEvents.clear()
        eventTriggers.map(this::applyEventTrigger)
    }

    /**
     * Checks keycode listeners list and triggers the listeners with matching keyCode
     *
     * @param keyCode keycode of the button pressed
     * @param keyDown whether to trigger key down or key up listeners
     */
    private fun triggerKeyListeners(keyCode: Int, keyDown: Boolean){
        if(keyDown){
            keyDownListeners.forEach {
                if(it.keyCode == keyCode){
                    it.listener()
                }
            }
        } else {
            keyUpListeners.forEach {
                if(it.keyCode == keyCode){
                    it.listener()
                }
            }
        }
    }

    /**
     * Applies an event trigger
     *
     * @param eventTrigger event trigger to be applied
     */
    private fun applyEventTrigger(eventTrigger: ExhibitionPageEventTrigger) {
        val events = eventTrigger.events
        events ?: return

        val delay: Long = eventTrigger.delay ?: 0
        if (delay > 0) {
            scheduleTimedEvent(delay, events)
        }

        val clickViewId = eventTrigger.clickViewId

        if (clickViewId != null) {
            bindClickEventListener(clickViewId, events)
        }

        val deviceGroupEvent = eventTrigger.deviceGroupEvent

        if (deviceGroupEvent != null) {
            val deviceGroupEventList = deviceGroupEvents.get(deviceGroupEvent) ?: arrayOf()
            deviceGroupEvents[deviceGroupEvent] = deviceGroupEventList.plus(events)
        }

        val keyCodeUp = eventTrigger.keyUp
        val keyCodeDown = eventTrigger.keyDown

        if (keyCodeDown != null) {
            bindKeyCodeEventListener(keyCodeDown, events, true)
        }

        if (keyCodeUp != null) {
            bindKeyCodeEventListener(keyCodeUp, events, false)
        }
    }

    /**
     * Disables clickable attribute from views attached to event triggers.
     *
     * @param eventTriggers event triggers to disable click views from
     */
    private fun disableClickEvents(eventTriggers: Array<ExhibitionPageEventTrigger>?) {
        eventTriggers?.forEach {
            val clickViewId = it.clickViewId
            if (clickViewId != null) {
                findViewWithTag(clickViewId)?.isClickable = false
            }
        }
    }

    /**
     * Schedules timed events
     *
     * @param delay the delay (in milliseconds) until events will be triggered
     * @param events events to be triggered
     */
    private fun scheduleTimedEvent(delay: Long, events: Array<ExhibitionPageEvent>) {
        handler.postDelayed({
            triggerEvents(events)
        }, delay)
    }

    /**
     * Binds event trigger to a view click
     *
     * @param clickViewId client view id
     * @param events events to be triggered on click
     */
    private fun bindClickEventListener(clickViewId: String, events: Array<ExhibitionPageEvent>) {
        val clickView = this.findViewWithTag(clickViewId)

        if (clickView == null) {
            Log.d(this.javaClass.name, "Failed to locate view by id $clickViewId")
        } else {
            clickView.setOnClickListener {
                if (pageInteractable) {
                    triggerEvents(events)
                }
            }
        }
    }

    /**
     * Binds event trigger to a keycode
     *
     * @param keyCodeString keycode to trigger events with
     * @param events events to be triggered by keycode
     * @param keyDown whether to bind to key down or key up
     */
    private fun bindKeyCodeEventListener(keyCodeString: String, events: Array<ExhibitionPageEvent>, keyDown: Boolean) {
        val listener = {
            if (pageInteractable) {
                triggerEvents(events)
            }
        }
        val keyCode = KeyEvent.keyCodeFromString("KEYCODE_${keyCodeString.toUpperCase(Locale.ROOT)}")
        if(keyDown){
            keyDownListeners.add(KeyCodeListener(keyCode, listener))
        } else {
            keyUpListeners.add(KeyCodeListener(keyCode, listener))
        }
    }

    /**
     * Triggers events
     *
     * @param events events to be triggered
     */
    private fun triggerEvents(events: Array<ExhibitionPageEvent>) {
        events.forEach(this::triggerEvent)
    }

    /**
     * Triggers an event
     *
     * @param event event to be triggered
     */
    private fun triggerEvent(event: ExhibitionPageEvent) {
        val properties = event.properties
        val provider: PageActionProvider? = PageActionProviderFactory.buildProvider(event.action, properties)
        if (provider == null) {
            Log.d(this.javaClass.name, "Could not find page action provider for ${event.action}")
            return
        }

        provider.performAction(this)
    }

    /**
     * Returns android transition, Only supports fade.
     *
     * @param transition transition to parse
     * @param mode Visibility mode in or mode out based on
     * @return Android Transition or null if no supported animation can be generated.
     */
    protected fun getPageAnimation(transition: Transition, mode: Int) : android.transition.Transition? {
        return if(transition.animation == Animation.fade) {
            val fade = Fade()
            fade.duration = transition.duration.toLong()
            fade.interpolator = getInterpolator(transition.timeInterpolation)
            fade.mode = mode
            fade
        } else {
            null
        }
    }

    /**
     * Gets interpolator based on animation time iterpolation enum
     *
     * @param interpolator Animation time interpolation enum
     * @return Android TimeInterpolator
     */
    private fun getInterpolator(interpolator: AnimationTimeInterpolation): TimeInterpolator {
        return when (interpolator) {
            AnimationTimeInterpolation.accelerate -> AccelerateInterpolator()
            AnimationTimeInterpolation.acceleratedecelerate -> AccelerateDecelerateInterpolator()
            AnimationTimeInterpolation.anticipate -> AnticipateInterpolator()
            AnimationTimeInterpolation.anticipateovershoot -> AnticipateOvershootInterpolator()
            AnimationTimeInterpolation.bounce -> BounceInterpolator()
            AnimationTimeInterpolation.decelerate -> DecelerateInterpolator()
            AnimationTimeInterpolation.linear -> LinearInterpolator()
            AnimationTimeInterpolation.overshoot -> OvershootInterpolator()
        }
    }

    /**
     * Sets shared element transition target names for all elements
     *
     * @param transitions Array of ExhibitionPageTransitions
     */
    private fun setSharedElementTransitions(transitions: Array<ExhibitionPageTransition>) {
        transitions.forEach{
            it.options?.morph?.views?.map(this::setTransitionTargetName)
        }
    }

    /**
     * Sets transition target name for transition morph
     *
     * @param morphPair ExhibitionPageTransitionOptionsMorphView
     */
    private fun setTransitionTargetName(morphPair : ExhibitionPageTransitionOptionsMorphView) {
        val view = findView(morphPair.sourceId) ?: return
        view.transitionName = morphPair.targetId
        transitionElements.add(view)
    }

    /**
     * Removes all children from the specified views parent
     *
     * @param view view from which parent all children will be removed
     */
    private fun releaseView(view: View?) {
        view ?: return
        val parent = view.parent
        if (parent is ViewGroup){
            parent.removeAllViews()
        }
    }

    /**
     * Starts listening for settings button click
     *
     * @param button button
     */
    protected fun listenSettingsButton(button: Button) {
        button.setOnClickListener{
            settingsButtonClick()
        }
    }

    /**
     * Starts listening for login button click
     *
     * @param button button
     */
    protected fun listenLoginButton(button: Button) {
        button.setOnClickListener{
            loginButtonClick()
        }
    }

    /**
     * Removes settings and index page listeners
     */
    protected fun removeSettingsAndIndexListeners() {
        settingsClickCounterHandler.removeCallbacksAndMessages(null)
        indexClickCounterHandler.removeCallbacksAndMessages(null)
        loginClickCounterHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Navigates to the page with transitions
     *
     * @param pageId page id
     * @param sharedElements shared elements to morph during transition or null
     */
    fun goToPage(pageId: UUID, sharedElements: List<View>? = null) {
        val currentPageId = getCurrentPageId()
        if (pageId == currentPageId) {
            Log.d(javaClass.name, "Trying to navigate to the same page")
            return
        }

        pageInteractable = false
        val intent = Intent(this, PageActivity::class.java).apply {
            putExtra("pageId", pageId.toString())
        }

        val pageTransitionTime = max(window.exitTransition?.duration ?: 300, window.enterTransition?.duration  ?: 300)
        if (!sharedElements.isNullOrEmpty()) {
            val transitionElementPairs = sharedElements.map { Pair.create(it, it.transitionName ?: "") }.toTypedArray()
            val targetElements = transitionElementPairs.map { it.second }
            intent.apply { putStringArrayListExtra("elements", ArrayList(targetElements))}
            val options = ActivityOptions
                .makeSceneTransitionAnimation(this, *transitionElementPairs)
            intent.putExtra("pageTransitionTime", pageTransitionTime)
            startActivity(intent, options.toBundle())

        } else {
            intent.putExtra("pageTransitionTime", pageTransitionTime)
            startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
        }
    }

    /**
     * Returns current page id
     *
     * @return current page id or null if not found
     */
    private fun getCurrentPageId(): UUID? {
        val activity = this

        return if (activity is PageActivity) {
            activity.pageId
        } else {
            null
        }
    }

    /**
     * Finds a view with tag
     *
     * @param tag tag
     * @return view or null if not found
     */
    private fun findViewWithTag(tag: String?): View? {
        tag ?: return null
        return root.findViewWithTag(tag)
    }

    /**
     * Sets current activity into application
     *
     * @param activity activity
     */
    private fun setCurrentActivity(activity: MuistiActivity?) {
        ExhibitionUIApplication.instance.setCurrentActivity(activity)
    }

    /**
     * Gets the current activity
     *
     * @return activity or null
     */
    private fun getCurrentActivity() : Activity? {
        return ExhibitionUIApplication.instance.getCurrentActivity()
    }

    /**
     * Starts a settings activity
     */
    protected fun startSetupActivity() {
        val intent = Intent(this, SetupActivity::class.java)
        this.startActivity(intent)
        finish()
    }

    /**
     * Triggers on interaction in the ExhibitionUIApplication
     */
    private fun onInteraction() {
        ExhibitionUIApplication.instance.onInteraction()
    }

    /**
     * Sets forced portrait mode
     */
    fun setForcedPortraitMode() {
        val rotation = DeviceSettings.getRotationFlip()
        runOnUiThread {
            if (rotation) {
                findViewById<RotateLayout>(R.id.main_screen_rotate)?.angle = 270
            } else {
                findViewById<RotateLayout>(R.id.main_screen_rotate)?.angle = 90
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        onInteraction()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Starts Main Activity
     */
    fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        VisitorSessionContainer.endVisitorSession()
        this.startActivity(intent)
        finish()
    }

    /**
     * Logs in with debug account
     */
    private fun debugLogin() {
        VisibleTagsContainer.tagSeen(
                tag = BuildConfig.KEYCLOAK_DEMO_TAG,
                expireSlack = 1000L
        )
    }

    /**
     * Handler for settings button click
     *
     * Increases settings click count and navigates to settings if it has been clicked 5 times.
     * Counter resets to zero after 1 sec
     */
    private fun settingsButtonClick() {
        settingsClickCounterHandler.removeCallbacksAndMessages(null)
        buttonClickCounter += 1
        if (buttonClickCounter > 4) {
            startSetupActivity()
        } else {
            settingsClickCounterHandler.postDelayed({
                buttonClickCounter = 0
            }, 1000)
        }
    }

    /**
     * Handler for index button click
     *
     * Increases settings click count and navigates to index page if it has been clicked 5 times.
     * Counter resets to zero after 1 sec
     */
    protected fun indexButtonClick() {
        indexClickCounterHandler.removeCallbacksAndMessages(null)
        buttonClickCounter += 1
        if (buttonClickCounter > 4) {
            startMainActivity()
        } else {
            indexClickCounterHandler.postDelayed({
                buttonClickCounter = 0
            }, 1000)
        }
    }

    /**
     * Handler for login button click
     *
     * Increases settings click count and logs in if it has been clicked 5 times.
     * Counter resets to zero after 1 sec
     */
    protected fun loginButtonClick() {
        loginClickCounterHandler.removeCallbacksAndMessages(null)
        buttonClickCounter += 1
        if (buttonClickCounter > 4) {
            debugLogin()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                loginClickCounterHandler.postDelayed({
                    buttonClickCounter = 0
                }, 1000)
            }
        }
    }

    /**
     * Checks if page is constructed in the PageViewContainer and either navigates to it or keeps waiting
     *
     * @param pageId page id to navigate to once it is ready
     */
    private fun waitForPage(pageId: UUID) {
        handler.postDelayed({
            if (PageViewContainer.contains(pageId)) {
                goToPage(pageId)
            } else {
                waitForPage(pageId)
            }
        }, 500)
    }
}
