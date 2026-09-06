package com.pierfrancescosoffritti.androidyoutubeplayer.core.customui

import android.content.Intent
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.menu.YouTubePlayerMenu
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.menu.defaultMenu.DefaultYouTubePlayerMenu
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.utils.FadeViewHelper
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.views.YouTubePlayerSeekBar
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.views.YouTubePlayerSeekBarListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

open class DefaultPlayerUiController(
  private val youTubePlayerView: YouTubePlayerView,
  private val youTubePlayer: YouTubePlayer
) : PlayerUiController {

  val rootView: View = View.inflate(youTubePlayerView.context, R.layout.ayp_default_player_ui, null)

  private var youTubePlayerMenu: YouTubePlayerMenu = DefaultYouTubePlayerMenu(
    youTubePlayerView.context
  )

  /**
   * View used for for intercepting clicks and for drawing a black background.
   * Could have used controlsContainer, but in this way I'm able to hide all the control at once by hiding controlsContainer
   */
  private val panel: View = rootView.findViewById(R.id.panel)

  private val controlsContainer: View = rootView.findViewById(R.id.controls_container)
  private val extraViewsContainer: LinearLayout = rootView.findViewById(R.id.extra_views_container)
  private val ccButton: ImageView = rootView.findViewById(R.id.cc_button)
  private val settingsButton: ImageView = rootView.findViewById(R.id.settings_button)

  private val videoTitle: TextView = rootView.findViewById(R.id.video_title)
  private val liveVideoIndicator: TextView = rootView.findViewById(R.id.live_video_indicator)

  private val progressBar: ProgressBar = rootView.findViewById(R.id.progress)
  private val playPauseButton: ImageView = rootView.findViewById(R.id.play_pause_button)
  private val youTubeButton: ImageView = rootView.findViewById(R.id.youtube_button)
  private val fullscreenButton: ImageView = rootView.findViewById(R.id.fullscreen_button)

  private val customActionLeft: ImageView = rootView.findViewById(R.id.custom_action_left_button)
  private val customActionRight: ImageView = rootView.findViewById(R.id.custom_action_right_button)
  private val arrowDownButton: ImageView = rootView.findViewById(R.id.arrow_down_button)
  private val lockButton: ImageView = rootView.findViewById(R.id.lock_button)
  private val moreVideoContainer: LinearLayout = rootView.findViewById(R.id.more_video_container)
  private val moreVideoButton: ImageView = rootView.findViewById(R.id.more_video_button)
  private val bottomActionsRow: View = rootView.findViewById(R.id.bottom_actions_row)

  private val rewindFeedback: LinearLayout = rootView.findViewById(R.id.rewind_feedback_container)
  private val forwardFeedback: LinearLayout = rootView.findViewById(R.id.forward_feedback_container)

  private val youtubePlayerSeekBar: YouTubePlayerSeekBar =
    rootView.findViewById(R.id.youtube_player_seekbar)
  private val fadeControlsContainer: FadeViewHelper = FadeViewHelper(controlsContainer)

  // Volume/Brightness overlay views
  private val vbOverlay: View = rootView.findViewById(R.id.volume_brightness_overlay)
  private val vbOverlayIcon: ImageView = rootView.findViewById(R.id.vb_overlay_icon)
  private val vbOverlayProgress: ProgressBar = rootView.findViewById(R.id.vb_overlay_progress)
  private val vbOverlayText: TextView = rootView.findViewById(R.id.vb_overlay_text)

  // Volume/Brightness state
  private val audioManager = youTubePlayerView.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  private var volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
  private var brightnessLevel: Float = run {
    val activity = findActivity(youTubePlayerView.context)
    val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    if (currentBrightness < 0) {
      try {
        Settings.System.getInt(youTubePlayerView.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
          .toFloat() / 255f
      } catch (e: Exception) { 0.5f }
    } else { currentBrightness }
  }
  private var isFullscreenMode = false
  private val hideOverlayHandler = Handler(Looper.getMainLooper())
  private val hideOverlayRunnable = Runnable { vbOverlay.visibility = View.GONE }

  private var onFullscreenButtonListener: View.OnClickListener
  private var onMenuButtonClickListener: View.OnClickListener
  private var onArrowDownButtonClickListener: View.OnClickListener
  private var onLikeButtonClickListener: View.OnClickListener? = null
  private var onDislikeButtonClickListener: View.OnClickListener? = null
  private var onSaveButtonClickListener: View.OnClickListener? = null
  private var onShareButtonClickListener: View.OnClickListener? = null
  private var onMoreVideoButtonClickListener: View.OnClickListener? = null
  private var onCcButtonClickListener: View.OnClickListener? = null
  private var onSettingsButtonClickListener: View.OnClickListener? = null

  private var isPlaying = false
  private var showBottomActions = false
  private var isPlayPauseButtonEnabled = true
  private var isCustomActionLeftEnabled = false
  private var isCustomActionRightEnabled = false

  private var isMatchParent = false

  var isLocked = false

  private var currentSecond: Float = 0f

  private val youTubePlayerStateListener = object : AbstractYouTubePlayerListener() {
    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
      updateState(state)

      if (state === PlayerConstants.PlayerState.PLAYING || state === PlayerConstants.PlayerState.PAUSED || state === PlayerConstants.PlayerState.VIDEO_CUED) {
        panel.setBackgroundColor(ContextCompat.getColor(panel.context, android.R.color.transparent))
        progressBar.visibility = View.GONE

        if (isPlayPauseButtonEnabled) playPauseButton.visibility = View.VISIBLE
        if (isCustomActionLeftEnabled) customActionLeft.visibility = View.VISIBLE
        if (isCustomActionRightEnabled) customActionRight.visibility = View.VISIBLE

        updatePlayPauseButtonIcon(state === PlayerConstants.PlayerState.PLAYING)

      } else {
        updatePlayPauseButtonIcon(false)

        if (state === PlayerConstants.PlayerState.BUFFERING) {
          progressBar.visibility = View.VISIBLE
          panel.setBackgroundColor(
            ContextCompat.getColor(
              panel.context,
              android.R.color.transparent
            )
          )
          if (isPlayPauseButtonEnabled) playPauseButton.visibility = View.INVISIBLE

          customActionLeft.visibility = View.GONE
          customActionRight.visibility = View.GONE
        }

        if (state === PlayerConstants.PlayerState.UNSTARTED) {
          progressBar.visibility = View.GONE
          if (isPlayPauseButtonEnabled) playPauseButton.visibility = View.VISIBLE
        }
      }
    }

    override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {
      youTubeButton.setOnClickListener {
        val intent = Intent(
          Intent.ACTION_VIEW,
          Uri.parse("https://www.youtube.com/watch?v=" + videoId + "#t=" + youtubePlayerSeekBar.seekBar.progress)
        )
        try {
          youTubeButton.context.startActivity(intent)
        } catch (e: Exception) {
          Log.e(javaClass.simpleName, e.message ?: "Can't open url to YouTube")
        }
      }
    }

    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
      currentSecond = second
    }
  }

  init {
    onFullscreenButtonListener = View.OnClickListener {
      isMatchParent = !isMatchParent
      when (isMatchParent) {
        true -> youTubePlayerView.matchParent()
        false -> youTubePlayerView.wrapContent()
      }
    }

    onMenuButtonClickListener = View.OnClickListener { }
    onArrowDownButtonClickListener = View.OnClickListener {
    }

    initClickListeners()
    bottomActionsRow.visibility = View.GONE

    (fullscreenButton.parent as? android.view.ViewGroup)?.removeView(fullscreenButton)
    youtubePlayerSeekBar.addViewToHeader(fullscreenButton)
  }

  private fun initClickListeners() {
    youTubePlayer.addListener(youtubePlayerSeekBar)
    youTubePlayer.addListener(fadeControlsContainer)
    youTubePlayer.addListener(youTubePlayerStateListener)

    youtubePlayerSeekBar.youtubePlayerSeekBarListener = object : YouTubePlayerSeekBarListener {
      override fun seekTo(time: Float) = youTubePlayer.seekTo(time)
    }

    val gestureDetector = android.view.GestureDetector(panel.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
      private var startX = 0f
      private var isDragging = false

      override fun onDown(e: MotionEvent): Boolean {
        startX = e.x
        isDragging = false
        return true
      }

      override fun onDoubleTap(e: MotionEvent): Boolean {
        if (isLocked) return false
        val viewWidth = panel.width
        val touchX = e.x

        if (touchX < viewWidth * 0.35) {
          // Seek backward 10 seconds
          youTubePlayer.seekTo(currentSecond - 10f)
          animateFeedback(rewindFeedback)
        } else if (touchX > viewWidth * 0.65) {
          // Seek forward 10 seconds
          youTubePlayer.seekTo(currentSecond + 10f)
          animateFeedback(forwardFeedback)
        }
        return true
      }

      override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        fadeControlsContainer.toggleVisibility()
        return true
      }

      override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (isLocked) return false
        if (!isFullscreenMode) return false
        if (Math.abs(distanceY) < Math.abs(distanceX)) return false

        isDragging = true
        val sensitivity = 0.005f
        val isLeftHalf = startX < (panel.width / 2)

        if (isLeftHalf) {
          // Brightness
          brightnessLevel = (brightnessLevel + (distanceY * sensitivity)).coerceIn(0.01f, 1f)
          val activity = findActivity(panel.context)
          activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = brightnessLevel
          }
          showVbOverlay(isBrightness = true, level = brightnessLevel)
        } else {
          // Volume
          volumeLevel = (volumeLevel + (distanceY * sensitivity)).coerceIn(0f, 1f)
          val newVolume = (volumeLevel * maxVolume).toInt()
          audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
          showVbOverlay(isBrightness = false, level = volumeLevel)
        }
        return true
      }
    })

    panel.setOnTouchListener { _, event ->
      gestureDetector.onTouchEvent(event)
      true
    }

    playPauseButton.setOnClickListener { onPlayButtonPressed() }
    fullscreenButton.setOnClickListener { onFullscreenButtonListener.onClick(fullscreenButton) }
    arrowDownButton.setOnClickListener { onArrowDownButtonClickListener.onClick(arrowDownButton) }
    moreVideoContainer.setOnClickListener { onMoreVideoButtonClickListener?.onClick(moreVideoButton) }
    ccButton.setOnClickListener { onCcButtonClickListener?.onClick(ccButton) }
    settingsButton.setOnClickListener { onSettingsButtonClickListener?.onClick(settingsButton) }
  }

  private fun animateFeedback(view: View) {
      view.visibility = View.VISIBLE
      view.alpha = 0f
      view.animate()
          .alpha(1f)
          .setDuration(200)
          .setListener(null)
          .withEndAction {
              view.postDelayed({
                  view.animate()
                      .alpha(0f)
                      .setDuration(200)
                      .withEndAction { view.visibility = View.GONE }
                      .start()
              }, 400)
          }
          .start()
  }

  override fun showVideoTitle(show: Boolean): PlayerUiController {
    videoTitle.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun setVideoTitle(videoTitle: String): PlayerUiController {
    this.videoTitle.text = videoTitle
    return this
  }

  override fun showUi(show: Boolean): PlayerUiController {
    fadeControlsContainer.isDisabled = !show
    controlsContainer.visibility = if (show) View.VISIBLE else View.INVISIBLE
    return this
  }

  override fun showPlayPauseButton(show: Boolean): PlayerUiController {
    playPauseButton.visibility = if (show) View.VISIBLE else View.GONE

    isPlayPauseButtonEnabled = show
    return this
  }

  override fun enableLiveVideoUi(enable: Boolean): PlayerUiController {
    youtubePlayerSeekBar.visibility = if (enable) View.INVISIBLE else View.VISIBLE
    liveVideoIndicator.visibility = if (enable) View.VISIBLE else View.GONE
    return this
  }

  override fun setCustomAction1(
    icon: Drawable,
    clickListener: View.OnClickListener?
  ): PlayerUiController {
    customActionLeft.setImageDrawable(icon)
    customActionLeft.setOnClickListener(clickListener)
    showCustomAction1(true)
    return this
  }

  override fun setCustomAction2(
    icon: Drawable,
    clickListener: View.OnClickListener?
  ): PlayerUiController {
    customActionRight.setImageDrawable(icon)
    customActionRight.setOnClickListener(clickListener)
    showCustomAction2(true)
    return this
  }

  override fun showCustomAction1(show: Boolean): PlayerUiController {
    isCustomActionLeftEnabled = show
    customActionLeft.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun showCustomAction2(show: Boolean): PlayerUiController {
    isCustomActionRightEnabled = show
    customActionRight.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  private var isMenuButtonEnabled = false

  override fun showMenuButton(show: Boolean): PlayerUiController {
    isMenuButtonEnabled = show
    extraViewsContainer.visibility = if (show) View.VISIBLE else View.GONE
    settingsButton.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun setMenuButtonClickListener(customMenuButtonClickListener: View.OnClickListener): PlayerUiController {
    onMenuButtonClickListener = customMenuButtonClickListener
    return this
  }

  override fun showArrowDownButton(show: Boolean): PlayerUiController {
    arrowDownButton.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun setArrowDownButtonClickListener(customArrowDownButtonClickListener: View.OnClickListener): PlayerUiController {
    onArrowDownButtonClickListener = customArrowDownButtonClickListener
    return this
  }

  fun setLikeButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onLikeButtonClickListener = listener
    return this
  }

  fun setDislikeButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onDislikeButtonClickListener = listener
    return this
  }

  fun setSaveButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onSaveButtonClickListener = listener
    return this
  }

  fun setShareButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onShareButtonClickListener = listener
    return this
  }

  fun setMoreVideoButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onMoreVideoButtonClickListener = listener
    return this
  }

  private var isLockButtonEnabled = false

  override fun showLockButton(show: Boolean): PlayerUiController {
    isLockButtonEnabled = show
    lockButton.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun setLockButton(
    icon: Drawable,
    clickListener: View.OnClickListener?
  ): PlayerUiController {
    lockButton.setImageDrawable(icon)
    lockButton.setOnClickListener(clickListener)
    lockButton.visibility = if (isLockButtonEnabled) View.VISIBLE else View.GONE
    return this
  }

  fun hideAllControlsExceptLock() {
    videoTitle.visibility = View.GONE
    playPauseButton.visibility = View.GONE
    youTubeButton.visibility = View.GONE
    fullscreenButton.visibility = View.GONE
    customActionLeft.visibility = View.GONE
    customActionRight.visibility = View.GONE
    arrowDownButton.visibility = View.GONE
    youtubePlayerSeekBar.visibility = View.GONE
    liveVideoIndicator.visibility = View.GONE
    extraViewsContainer.visibility = View.GONE
    lockButton.visibility = View.VISIBLE
    bottomActionsRow.visibility = View.GONE
  }

  fun setShowBottomActions(show: Boolean) {
    showBottomActions = show
    if (controlsContainer.visibility == View.VISIBLE) {
        bottomActionsRow.visibility = if (show) View.VISIBLE else View.GONE
    }
  }

  fun showAllControls() {
    if (isPlayPauseButtonEnabled) playPauseButton.visibility = View.VISIBLE
    if (isCustomActionLeftEnabled) customActionLeft.visibility = View.VISIBLE
    if (isCustomActionRightEnabled) customActionRight.visibility = View.VISIBLE
    videoTitle.visibility = View.VISIBLE
    fullscreenButton.visibility = View.VISIBLE
    arrowDownButton.visibility = View.VISIBLE
    youtubePlayerSeekBar.visibility = View.VISIBLE
    bottomActionsRow.visibility = if (showBottomActions) View.VISIBLE else View.GONE
    extraViewsContainer.visibility = if (isMenuButtonEnabled) View.VISIBLE else View.GONE
    lockButton.visibility = if (isLockButtonEnabled) View.VISIBLE else View.GONE
  }

  override fun showCurrentTime(show: Boolean): PlayerUiController {
    youtubePlayerSeekBar.videoCurrentTimeTextView.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun showDuration(show: Boolean): PlayerUiController {
    youtubePlayerSeekBar.videoDurationTextView.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun showSeekBar(show: Boolean): PlayerUiController {
    youtubePlayerSeekBar.seekBar.visibility = if (show) View.VISIBLE else View.INVISIBLE
    return this
  }

  override fun showBufferingProgress(show: Boolean): PlayerUiController {
    youtubePlayerSeekBar.showBufferingProgress = show
    return this
  }

  override fun showYouTubeButton(show: Boolean): PlayerUiController {
    youTubeButton.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun addView(view: View): PlayerUiController {
    extraViewsContainer.addView(view, 0)
    return this
  }

  override fun removeView(view: View): PlayerUiController {
    extraViewsContainer.removeView(view)
    return this
  }

  override fun getMenu(): YouTubePlayerMenu = youTubePlayerMenu

  override fun showFullscreenButton(show: Boolean): PlayerUiController {
    fullscreenButton.visibility = if (show) View.VISIBLE else View.GONE
    return this
  }

  override fun setFullscreenButtonClickListener(customFullscreenButtonClickListener: View.OnClickListener): PlayerUiController {

    onFullscreenButtonListener = customFullscreenButtonClickListener
    return this
  }

  private fun onPlayButtonPressed() {
    if (isPlaying)
      youTubePlayer.pause()
    else
      youTubePlayer.play()
  }

  private fun updateState(state: PlayerConstants.PlayerState) {
    when (state) {
      PlayerConstants.PlayerState.ENDED -> isPlaying = false
      PlayerConstants.PlayerState.PAUSED -> isPlaying = false
      PlayerConstants.PlayerState.PLAYING -> isPlaying = true
      else -> {}
    }

    updatePlayPauseButtonIcon(!isPlaying)
  }

  private fun updatePlayPauseButtonIcon(playing: Boolean) {
    val drawable = if (playing) R.drawable.ayp_ic_pause_36dp else R.drawable.ayp_ic_play_36dp
    playPauseButton.setImageResource(drawable)
  }

  fun setCcButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onCcButtonClickListener = listener
    return this
  }

  fun setSettingsButtonClickListener(listener: View.OnClickListener): PlayerUiController {
    onSettingsButtonClickListener = listener
    return this
  }

  fun setCcEnabled(isEnabled: Boolean) {
    val drawable = if (isEnabled) R.drawable.ayp_ic_cc_on else R.drawable.ayp_ic_cc_off
    ccButton.setImageResource(drawable)
    ccButton.clearColorFilter()
  }

  fun getMoreVideoThumbnail(): ImageView = moreVideoButton

  // --- Volume/Brightness helpers ---

  fun setFullscreenMode(fullscreen: Boolean) {
    isFullscreenMode = fullscreen
  }

  private fun showVbOverlay(isBrightness: Boolean, level: Float) {
    val percent = (level * 100).toInt()

    // Set icon
    val iconRes = if (isBrightness) {
      if (level > 0.5f) R.drawable.ayp_ic_brightness_high else R.drawable.ayp_ic_brightness_low
    } else {
      when {
        level <= 0f -> R.drawable.ayp_ic_volume_off
        level < 0.5f -> R.drawable.ayp_ic_volume_down
        else -> R.drawable.ayp_ic_volume_up
      }
    }
    vbOverlayIcon.setImageResource(iconRes)
    vbOverlayProgress.progress = percent
    vbOverlayText.text = "$percent%"

    vbOverlay.visibility = View.VISIBLE

    // Auto-hide after 1 second
    hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
    hideOverlayHandler.postDelayed(hideOverlayRunnable, 1000)
  }

  private fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
      if (ctx is android.app.Activity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }
}
