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
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.util.Util;

/**
 * This class attempts to abstract basic state and non ui functionality.
 */
public abstract class ExoPlayerManager<D> extends PlayerManager<D>
    implements PlaybackPreparer, PlayerControlView.VisibilityListener {

  // Media item configuration extras.

  public static final String TUNNELING_EXTRA = "tunneling";

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

  @Override
  protected abstract SimpleExoPlayer getPlayer();

  public void setDebug(boolean debug) {
    mDebug = debug;
  }

  public boolean debug() {
    return mDebug;
  }

  // Lifecycle methods
  public void onNewIntent(Intent intent) {
    releasePlayer();
    releaseAdsLoader();
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
    DefaultTrackSelector.ParametersBuilder builder =
        new DefaultTrackSelector.ParametersBuilder(/* context= */ getContext());
    boolean tunneling = getIntent().getBooleanExtra(TUNNELING_EXTRA, false);
    if (Util.SDK_INT >= 21 && tunneling) {
      builder.setTunnelingAudioSessionId(C.generateAudioSessionIdV21(/* context= */ getContext()));
    }
    trackSelectorParameters = builder.build();
    clearStartPosition();
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
  protected abstract void updateButtonVisibility();

  protected abstract void showControls();

  // PlaybackControlView.PlaybackPreparer implementation
  @Override
  public void preparePlayback() {
    if (getPlayer() != null) {
      getPlayer().retry();
    }
  }

  // PlayerControlView.VisibilityListener implementation
  @Override
  public abstract void onVisibilityChange(int visibility);

  // Player.DefaultEventListener
  @Override
  public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    if (playbackState == Player.STATE_ENDED) {
      showControls();
    }
    updateButtonVisibility();
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    if (PlayerUtils.isBehindLiveWindow(e)) {
      clearStartPosition();
      initializePlayer();
    } else {
      updateButtonVisibility();
      showControls();
    }
    showToast(e.getMessage(), e);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    updateButtonVisibility();
  }
}
