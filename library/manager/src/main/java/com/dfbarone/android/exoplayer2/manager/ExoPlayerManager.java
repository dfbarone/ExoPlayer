package com.dfbarone.android.exoplayer2.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.dfbarone.android.exoplayer2.manager.util.PlayerUtils;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;

/**
 * This class attempts to abstract basic state and non ui functionality.
 */
public abstract class ExoPlayerManager<D> extends PlayerManager<D>
    implements PlaybackPreparer, PlayerControlView.VisibilityListener {

  // Saved instance state keys.
  public static final String KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters";
  public static final String KEY_WINDOW = "window";
  public static final String KEY_POSITION = "position";
  public static final String KEY_AUTO_PLAY = "auto_play";

  // State variables
  protected boolean startAutoPlay = false;
  protected int startWindow = C.INDEX_UNSET;
  protected long startPosition = C.TIME_UNSET;
  private boolean mDebug = false;

  // Track selector
  protected DefaultTrackSelector trackSelector;
  protected DefaultTrackSelector.Parameters trackSelectorParameters;
  protected TrackGroupArray lastSeenTrackGroupArray;

  public ExoPlayerManager(Context context, View root) {
    super(context, root);
  }

  public void setDebug(boolean debug) {
    mDebug = debug;
  }

  public boolean debug() {
    return mDebug;
  }

  // Lifecycle methods
  public void onNewIntent(Intent intent) {
    releasePlayer();
    clearStartPosition();
    setIntent(intent);
  }

  public void onSaveInstanceState(Bundle outState) {
    updateTrackSelectorParameters();
    updateStartPosition();
    outState.putParcelable(KEY_TRACK_SELECTOR_PARAMETERS, trackSelectorParameters);
    outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
    outState.putInt(KEY_WINDOW, startWindow);
    outState.putLong(KEY_POSITION, startPosition);
  }

  public void onRestoreInstanceState(Bundle savedInstanceState) {
    // Restore instance state
    if (savedInstanceState != null) {
      trackSelectorParameters = savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS);
      startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
      startWindow = savedInstanceState.getInt(KEY_WINDOW);
      startPosition = savedInstanceState.getLong(KEY_POSITION);
    } else {
      trackSelectorParameters = new DefaultTrackSelector.ParametersBuilder().build();
      clearStartPosition();
    }
  }

  // State methods
  protected void updateStartPosition() {
    if (getPlayer() != null) {
      startAutoPlay = getPlayer().getPlayWhenReady();
      startWindow = getPlayer().getCurrentWindowIndex();
      startPosition = Math.max(0, getPlayer().getContentPosition());
    }
  }

  protected void clearStartPosition() {
    startAutoPlay = true;
    startWindow = C.INDEX_UNSET;
    startPosition = C.TIME_UNSET;
  }

  protected void updateTrackSelectorParameters() {
    if (trackSelector != null) {
      trackSelectorParameters = trackSelector.getParameters();
    }
  }

  // UI methods
  protected abstract void updateButtonVisibilities();

  protected abstract void showControls();

  // PlaybackControlView.PlaybackPreparer implementation
  @Override
  public void preparePlayback() {
    initializePlayer();
  }

  // PlayerControlView.VisibilityListener implementation
  @Override
  public abstract void onVisibilityChange(int visibility);

  // Player.DefaultEventListener
  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == Player.STATE_ENDED) {
      showControls();
    }
    updateButtonVisibilities();
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    if (getPlayer().getPlaybackError() != null) {
      // The user has performed a seek whilst in the error state. Update the resume position so
      // that if the user then retries, playback resumes from the position to which they seeked.
      updateStartPosition();
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    if (PlayerUtils.isBehindLiveWindow(e)) {
      clearStartPosition();
      initializePlayer();
    } else {
      updateStartPosition();
      updateButtonVisibilities();
      showControls();
    }
    onError("onPlayerError", e);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    updateButtonVisibilities();
  }
}
