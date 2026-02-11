package com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.utils.TimeUtilities

class YouTubePlayerSeekBar(context: Context, attrs: AttributeSet? = null) :
  LinearLayout(context, attrs), SeekBar.OnSeekBarChangeListener, YouTubePlayerListener {

  private var seekBarTouchStarted = false

  // I need this variable because onCurrentSecond gets called every 100 mils, so without the proper checks on this variable in onCurrentSeconds the seek bar glitches when touched.
  private var newSeekBarProgress = -1

  private var isPlaying = false

  var showBufferingProgress = true
  var youtubePlayerSeekBarListener: YouTubePlayerSeekBarListener? = null

  val videoCurrentTimeTextView = TextView(context)
  val videoDurationTextView = TextView(context)
  val seekBar = SeekBar(context)
  private val rightContainer = LinearLayout(context)

  init {
    val typedArray =
      context.theme.obtainStyledAttributes(attrs, R.styleable.YouTubePlayerSeekBar, 0, 0)

    val fontSize = typedArray.getDimensionPixelSize(
      R.styleable.YouTubePlayerSeekBar_fontSize,
      resources.getDimensionPixelSize(R.dimen.ayp_12sp)
    )
    val color = typedArray.getColor(
      R.styleable.YouTubePlayerSeekBar_color,
      ContextCompat.getColor(context, R.color.ayp_red)
    )

    typedArray.recycle()

    val padding = resources.getDimensionPixelSize(R.dimen.ayp_8dp)

    videoCurrentTimeTextView.text = resources.getString(R.string.ayp_null_time)
    videoCurrentTimeTextView.setPadding(padding, padding, 0, padding)
    videoCurrentTimeTextView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
    videoCurrentTimeTextView.gravity = Gravity.CENTER_VERTICAL

    videoDurationTextView.text = resources.getString(R.string.ayp_null_time)
    videoDurationTextView.setPadding(0, padding, padding, padding)
    videoDurationTextView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
    videoDurationTextView.gravity = Gravity.CENTER_VERTICAL

    setFontSize(fontSize.toFloat())

    seekBar.setPadding(padding * 2, padding, padding * 2, padding)
    setColor(color)

    val timeContainer = LinearLayout(context)
    timeContainer.orientation = HORIZONTAL
    timeContainer.gravity = Gravity.CENTER_VERTICAL
    timeContainer.setPadding(padding, 0, padding, 0)

    videoCurrentTimeTextView.setPadding(0, 0, 0, 0)
    videoDurationTextView.setPadding(0, 0, 0, 0)

    val separatorTextView = TextView(context)
    separatorTextView.text = " / "
    separatorTextView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
    separatorTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize.toFloat())

    timeContainer.addView(videoCurrentTimeTextView)
    timeContainer.addView(separatorTextView)
    timeContainer.addView(videoDurationTextView)

    val headerContainer = LinearLayout(context)
    headerContainer.orientation = HORIZONTAL
    headerContainer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    headerContainer.gravity = Gravity.CENTER_VERTICAL
    
    // Config time container background
    timeContainer.setBackgroundResource(R.drawable.ayp_text_background)
    val bgPadding = resources.getDimensionPixelSize(R.dimen.ayp_8dp)
    timeContainer.setPadding(bgPadding, bgPadding / 4, bgPadding, bgPadding / 4)

    rightContainer.orientation = HORIZONTAL
    rightContainer.gravity = Gravity.CENTER_VERTICAL
    rightContainer.setPadding(padding, 0, padding, 0)

    val space = android.view.View(context)
    space.layoutParams = LinearLayout.LayoutParams(0, 0, 1f)

    headerContainer.addView(timeContainer)
    headerContainer.addView(space)
    headerContainer.addView(rightContainer)

    orientation = VERTICAL
    addView(headerContainer)

    val seekBarParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    seekBarParams.topMargin = -padding
    addView(seekBar, seekBarParams)
    
    seekBar.setOnSeekBarChangeListener(this)
  }

  /**
   * @param fontSize in pixels.
   */
  fun setFontSize(fontSize: Float) {
    videoCurrentTimeTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
    videoDurationTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
  }

  fun setColor(@ColorInt color: Int) {
    DrawableCompat.setTint(seekBar.thumb, color)
    DrawableCompat.setTint(seekBar.progressDrawable, color)
  }

  private fun updateState(state: PlayerConstants.PlayerState) {
    when (state) {
      PlayerConstants.PlayerState.ENDED -> isPlaying = false
      PlayerConstants.PlayerState.PAUSED -> isPlaying = false
      PlayerConstants.PlayerState.PLAYING -> isPlaying = true
      PlayerConstants.PlayerState.UNSTARTED -> resetUi()
      else -> {}
    }
  }

  private fun resetUi() {
    seekBar.progress = 0
    seekBar.max = 0
    videoDurationTextView.post { videoDurationTextView.text = "" }
  }

  // Seekbar

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    videoCurrentTimeTextView.text = TimeUtilities.formatTime(progress.toFloat())
  }

  override fun onStartTrackingTouch(seekBar: SeekBar) {
    seekBarTouchStarted = true
  }

  override fun onStopTrackingTouch(seekBar: SeekBar) {
    if (isPlaying)
      newSeekBarProgress = seekBar.progress

    youtubePlayerSeekBarListener?.seekTo(seekBar.progress.toFloat())
    seekBarTouchStarted = false
  }

  // YouTubePlayerListener

  override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
    newSeekBarProgress = -1
    updateState(state)
  }

  override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
    // ignore if the user is currently moving the SeekBar
    if (seekBarTouchStarted)
      return
    // ignore if the current time is older than what the user selected with the SeekBar
    if (newSeekBarProgress > 0 && TimeUtilities.formatTime(second) != TimeUtilities.formatTime(
        newSeekBarProgress.toFloat()
      )
    )
      return

    newSeekBarProgress = -1

    seekBar.progress = second.toInt()
  }

  override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
    videoDurationTextView.text = TimeUtilities.formatTime(duration)
    seekBar.max = duration.toInt()
  }

  override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {
    if (showBufferingProgress)
      seekBar.secondaryProgress = (loadedFraction * seekBar.max).toInt()
    else
      seekBar.secondaryProgress = 0
  }

  override fun onReady(youTubePlayer: YouTubePlayer) {}
  override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {}
  override fun onApiChange(youTubePlayer: YouTubePlayer) {}
  override fun onPlaybackQualityChange(
    youTubePlayer: YouTubePlayer,
    playbackQuality: PlayerConstants.PlaybackQuality
  ) {
  }

  override fun onPlaybackRateChange(
    youTubePlayer: YouTubePlayer,
    playbackRate: PlayerConstants.PlaybackRate
  ) {
  }

  override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {}

  fun addViewToHeader(view: android.view.View) {
    rightContainer.addView(view)
  }
}

interface YouTubePlayerSeekBarListener {
  fun seekTo(time: Float)
}