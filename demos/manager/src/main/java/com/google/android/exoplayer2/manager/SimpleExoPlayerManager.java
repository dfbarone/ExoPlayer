/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.manager.util.ContextHelper;
import com.google.android.exoplayer2.manager.util.PlayerUtils;
import com.google.android.exoplayer2.managerdemo.R;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * An class that plays media using {@link SimpleExoPlayer}.
 */
public class SimpleExoPlayerManager extends ExoPlayerManager
    implements OnClickListener {

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String EXTENSION_EXTRA = "extension";

  public static final String ACTION_VIEW_LIST = "com.google.android.exoplayer.demo.action.VIEW_LIST";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String EXTENSION_LIST_EXTRA = "extension_list";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";

  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";

  public static final String ABR_ALGORITHM_EXTRA = "abr_algorithm";
  public static final String ABR_ALGORITHM_DEFAULT = "default";
  public static final String ABR_ALGORITHM_RANDOM = "random";

  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";

  // ui
  protected PlayerView playerView;
  protected LinearLayout debugRootView;
  protected TextView debugTextView;

  // core
  protected SimpleExoPlayer player;
  protected MediaSource mediaSource;
  protected DebugTextViewHelper debugViewHelper;

  // Fields used only for ad playback. The ads loader is loaded via reflection.
  protected Uri loadedAdTagUri;

  // HTTP and DataSource variables
  protected final static String USER_AGENT = SimpleExoPlayerManager.class.getSimpleName();
  protected static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  protected static final CookieManager DEFAULT_COOKIE_MANAGER;
  protected DataSource.Factory mediaDataSourceFactory;

  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  public SimpleExoPlayerManager(Context context, View view) {
    super(context, view);

    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    if (getView() != null) {
      // Find views
      playerView = getView().findViewById(R.id.player_view);
      debugRootView = getView().findViewById(R.id.controls_root);
      debugTextView = getView().findViewById(R.id.debug_text_view);
      setDebugTextVisibility(View.GONE);
      setDebugRootVisibility(View.GONE);

      // Initialize player view
      playerView.setControllerVisibilityListener(this);
      playerView.setErrorMessageProvider(new DefaultPlayerErrorMessageProvider(getContext()));
      playerView.requestFocus();

      // Set root on click listener
      getView().setOnClickListener(this);
    }

    // Restore instance state
    onRestoreInstanceState(null);

    setDependencies(new InitializePlayer.Builder(new DefaultDataSourceBuilder(),
        new DefaultMediaSourceBuilder()).build());
  }

  @Override
  public SimpleExoPlayer getPlayer() {
    return player;
  }

  @Override
  public void setDependencies(InitializePlayer dependencies) {
    super.setDependencies(dependencies);
    mediaDataSourceFactory = dependencies.dataSourceBuilder().buildDataSourceFactory(true);
  }

  // Activity lifecycle
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return (playerView != null && playerView.dispatchKeyEvent(event)) ||
        (getView() != null && getView().dispatchKeyEvent(event));
  }

  // OnClickListener methods
  @Override
  public void onClick(View view) {
    if (debugRootView != null &&
        view.getParent() == debugRootView) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        CharSequence title = ((Button) view).getText();
        int rendererIndex = (int) view.getTag();
        int rendererType = mappedTrackInfo.getRendererType(rendererIndex);
        boolean allowAdaptiveSelections =
            rendererType == C.TRACK_TYPE_VIDEO
                || (rendererType == C.TRACK_TYPE_AUDIO
                && mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                == MappedTrackInfo.RENDERER_SUPPORT_NO_TRACKS);
        Pair<AlertDialog, TrackSelectionView> dialogPair =
            TrackSelectionView.getDialog(ContextHelper.getActivity(getContext()), title, trackSelector, rendererIndex);
        dialogPair.second.setShowDisableOption(true);
        dialogPair.second.setAllowAdaptiveSelections(allowAdaptiveSelections);
        dialogPair.first.show();
      }
    }
  }

  // PlaybackControlView.VisibilityListener implementation
  @Override
  public void onVisibilityChange(int visibility) {
    setDebugTextVisibility(View.VISIBLE == visibility ? View.GONE : View.VISIBLE);
    setDebugRootVisibility(visibility);
  }

  // Internal methods
  @Override
  public void initializePlayer() {
    if (player == null) {
      Intent intent = getIntent();

      // initialize arguments
      String action = intent.getAction();
      Uri[] uris;
      String[] extensions;
      if (ACTION_VIEW.equals(action)) {
        uris = new Uri[]{intent.getData()};
        extensions = new String[]{intent.getStringExtra(EXTENSION_EXTRA)};
      } else if (ACTION_VIEW_LIST.equals(action)) {
        String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
        uris = new Uri[uriStrings.length];
        for (int i = 0; i < uriStrings.length; i++) {
          uris[i] = Uri.parse(uriStrings[i]);
        }
        extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);
        if (extensions == null) {
          extensions = new String[uriStrings.length];
        }
      } else {
        onError(getContext().getString(R.string.unexpected_intent_action, action));
        finish(getContext().getString(R.string.unexpected_intent_action, action));
        return;
      }

      // initialize drm
      DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if ((intent.hasExtra(DRM_SCHEME_EXTRA) &&
          !TextUtils.isEmpty(intent.getStringExtra(DRM_SCHEME_EXTRA)))) {
        int errorStringId = R.string.error_drm_unknown;
        if (Util.SDK_INT < 18) {
          errorStringId = R.string.error_drm_not_supported;
        } else {
          try {
            drmSessionManager = dependencies().drmSessionManagerBuilder().buildDrmSessionManager();
          } catch (UnsupportedDrmException e) {
            errorStringId = e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown;
          } catch (Exception e) {

          }
        }
        if (drmSessionManager == null) {
          onError(getContext().getString(errorStringId));
          finish(getContext().getString(errorStringId));
          return;
        }
      }

      // initialize track selection
      TrackSelection.Factory trackSelectionFactory;
      String abrAlgorithm = intent.getStringExtra(ABR_ALGORITHM_EXTRA);
      if (abrAlgorithm == null || ABR_ALGORITHM_DEFAULT.equals(abrAlgorithm)) {
        trackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
      } else if (ABR_ALGORITHM_RANDOM.equals(abrAlgorithm)) {
        trackSelectionFactory = new RandomTrackSelection.Factory();
      } else {
        onError(getContext().getString(R.string.error_unrecognized_abr_algorithm));
        finish(getContext().getString(R.string.error_unrecognized_abr_algorithm));
        return;
      }

      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
      if (intent.hasExtra(PREFER_EXTENSION_DECODERS_EXTRA)) {
        boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS_EXTRA, false);
        extensionRendererMode = preferExtensionDecoders ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
      }

      DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(getContext(), extensionRendererMode);

      trackSelector = new DefaultTrackSelector(trackSelectionFactory);
      trackSelector.setParameters(trackSelectorParameters);
      lastSeenTrackGroupArray = null;

      player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, getLoadControl(), drmSessionManager);
      player.addListener(this);
      player.setPlayWhenReady(startAutoPlay);
      player.addAnalyticsListener(new EventLogger(trackSelector));
      if (playerView != null) {
        playerView.setPlayer(player);
        playerView.setPlaybackPreparer(this);
      }
      if (debugTextView != null) {
        debugViewHelper = new DebugTextViewHelper(player, debugTextView);
        debugViewHelper.start();
      }

      MediaSource[] mediaSources = new MediaSource[uris.length];
      for (int i = 0; i < uris.length; i++) {
        mediaSources[i] = dependencies().mediaSourceBuilder().buildMediaSource(uris[i], extensions[i]);
      }
      mediaSource =
          mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources);

      // initialize AdsLoader
      String adTagUriString = intent.getStringExtra(AD_TAG_URI_EXTRA);
      if (adTagUriString != null && dependencies().adsMediaSourceBuilder() != null) {
        Uri adTagUri = Uri.parse(adTagUriString);
        if (!adTagUri.equals(loadedAdTagUri)) {
          releaseAdsLoader();
          loadedAdTagUri = adTagUri;
        }
        MediaSource adsMediaSource = dependencies().adsMediaSourceBuilder().createAdsMediaSource(mediaSource, Uri.parse(adTagUriString));
        if (adsMediaSource != null) {
          mediaSource = adsMediaSource;
        } else {
          onError(getContext().getString(R.string.ima_not_loaded));
        }
      } else {
        releaseAdsLoader();
      }
    }
    boolean haveStartPosition = startWindow != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startWindow, startPosition);
    }
    player.prepare(mediaSource, !haveStartPosition, false);
    updateButtonVisibilities();
  }

  @Override
  public void releasePlayer() {
    if (player != null) {
      updateTrackSelectorParameters();
      updateStartPosition();
      if (debugViewHelper != null) {
        debugViewHelper.stop();
      }
      debugViewHelper = null;
      player.release();
      player = null;
      mediaSource = null;
      trackSelector = null;
    }
  }

  @Override
  public void releaseAdsLoader() {
    loadedAdTagUri = null;
    super.releaseAdsLoader();
  }

  // User controls
  @Override
  protected void updateButtonVisibilities() {
    if (debugRootView != null) {
      debugRootView.removeAllViews();
    }

    if (player == null) {
      return;
    }

    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);
      if (trackGroups.length != 0) {
        Button button = new Button(getContext());
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.exo_track_selection_title_audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.exo_track_selection_title_video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.exo_track_selection_title_text;
            break;
          default:
            continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        if (debugRootView != null) {
          debugRootView.addView(button);
        }
      }
    }
  }

  @Override
  protected void showControls() {
    setDebugTextVisibility(View.GONE);
    setDebugRootVisibility(View.VISIBLE);
  }

  private void setDebugRootVisibility(int visibility) {
    PlayerUtils.setDebugVisibility(debugRootView, debug(), visibility);
  }

  private void setDebugTextVisibility(int visibility) {
    PlayerUtils.setDebugVisibility(debugTextView, debug(), visibility);
  }

  private static class DefaultPlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

    private Context mContext;

    public DefaultPlayerErrorMessageProvider(Context context) {
      mContext = context;
    }

    private Context getContext() {
      return mContext;
    }

    @Override
    public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
      String errorString = "";
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = e.getRendererException();
        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
          // Special case for decoder initialization failures.
          MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
              (MediaCodecRenderer.DecoderInitializationException) cause;
          if (decoderInitializationException.decoderName == null) {
            if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
              errorString = getContext().getString(R.string.error_querying_decoders);
            } else if (decoderInitializationException.secureDecoderRequired) {
              errorString =
                  getContext().getString(
                      R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
            } else {
              errorString =
                  getContext().getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
            }
          } else {
            errorString =
                getContext().getString(
                    R.string.error_instantiating_decoder,
                    decoderInitializationException.decoderName);
          }
        }
      }
      return Pair.create(0, errorString);
    }
  }

  public LoadControl getLoadControl() {
    return (dependencies().loadControl() != null ? dependencies().loadControl() : new DefaultLoadControl());
  }

  public class DefaultDataSourceBuilder implements DataSourceBuilder {
    /*** Returns a new DataSource factory.*/
    @Override
    public DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
      // Optional
      TransferListener<? super DataSource> listener = useBandwidthMeter ? BANDWIDTH_METER : null;
      DefaultDataSourceFactory upstreamFactory =
          new DefaultDataSourceFactory(getContext(), listener, buildHttpDataSourceFactory(listener));
      return upstreamFactory;
    }

    /*** Returns a {@link HttpDataSource.Factory}.*/
    @Override
    public HttpDataSource.Factory buildHttpDataSourceFactory(
        TransferListener<? super DataSource> listener) {
      return new DefaultHttpDataSourceFactory(Util.getUserAgent(getContext(), USER_AGENT), listener);
    }
  }

  public class DefaultMediaSourceBuilder implements MediaSourceBuilder {
    @Override
    public MediaSource buildMediaSource(Uri uri) {
      return buildMediaSource(uri, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
      return PlayerUtils.buildSimpleMediaSource(
          dependencies().dataSourceBuilder().buildDataSourceFactory(false),
          mediaDataSourceFactory, uri, overrideExtension);
    }
  }

}