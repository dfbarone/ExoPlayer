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
package com.dfbarone.android.exoplayer2.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.dfbarone.android.exoplayer2.manager.util.ContextHelper;
import com.dfbarone.android.exoplayer2.manager.util.PlayerUtils;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.spherical.SphericalGLSurfaceView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.lang.reflect.Constructor;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * An class that plays media using {@link SimpleExoPlayer}.
 */
public class SimpleExoPlayerManager<D> extends ExoPlayerManager<D>
    implements PlayerManager.DataSourceBuilder, PlayerManager.MediaSourceBuilder,
    PlayerManager.MedaiDrmCallbackBuilder, PlayerManager.AdsMediaSourceBuilder, OnClickListener {

// Activity extras.

  public static final String SPHERICAL_STEREO_MODE_EXTRA = "spherical_stereo_mode";
  public static final String SPHERICAL_STEREO_MODE_MONO = "mono";
  public static final String SPHERICAL_STEREO_MODE_TOP_BOTTOM = "top_bottom";
  public static final String SPHERICAL_STEREO_MODE_LEFT_RIGHT = "left_right";

  // Actions.

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";

  // Player configuration extras.

  public static final String ABR_ALGORITHM_EXTRA = "abr_algorithm";
  public static final String ABR_ALGORITHM_DEFAULT = "default";
  public static final String ABR_ALGORITHM_RANDOM = "random";

  // Media item configuration extras.

  public static final String URI_EXTRA = "uri";
  public static final String EXTENSION_EXTRA = "extension";
  public static final String IS_LIVE_EXTRA = "is_live";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";
  public static final String TUNNELING_EXTRA = "tunneling";
  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";
  public static final String SUBTITLE_URI_EXTRA = "subtitle_uri";
  public static final String SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type";
  public static final String SUBTITLE_LANGUAGE_EXTRA = "subtitle_language";
  // For backwards compatibility only.
  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

  protected static final CookieManager DEFAULT_COOKIE_MANAGER;
  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  // ui
  protected PlayerView playerView;
  protected LinearLayout debugRootView;
  protected Button selectTracksButton;
  protected TextView debugTextView;
  protected boolean isShowingTrackSelectionDialog;

  // core
  protected DataSource.Factory dataSourceFactory;
  protected SimpleExoPlayer player;
  protected MediaSource mediaSource;
  protected DebugTextViewHelper debugViewHelper;

  // Fields used only for ad playback. The ads loader is loaded via reflection.

  protected AdsLoader adsLoader;
  protected Uri loadedAdTagUri;

  // Activity lifecycle

  public SimpleExoPlayerManager(Context context, View view) {
    super(context, view);
    Intent intent = getIntent();
    String sphericalStereoMode = intent.getStringExtra(SPHERICAL_STEREO_MODE_EXTRA);
    /*if (sphericalStereoMode != null && ContextHelper.isAppCompatActivity(getContext())) {
      ContextHelper.getAppCompatActivity(getContext()).setTheme(R.style.PlayerTheme_Spherical);
    }*/

    dataSourceFactory = buildDataSourceFactory();
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    if (getView() != null) {
      // Find views
      playerView = getView().findViewById(R.id.player_view);
      if (playerView == null) {
        throw new IllegalStateException(
            "Your view must contain a PlayerView with an id of R.id.player_view");
      }
      debugRootView = getView().findViewById(R.id.controls_root);
      debugTextView = getView().findViewById(R.id.debug_text_view);
      selectTracksButton = getView().findViewById(R.id.select_tracks_button);
      if (selectTracksButton != null) {
        selectTracksButton.setOnClickListener(this);
      }
      setDebugTextVisibility(View.VISIBLE);
      setDebugRootVisibility(View.GONE);

      // Initialize player view
      playerView.setControllerVisibilityListener(this);
      if (getErrorMessageProvider() != null) {
        playerView.setErrorMessageProvider(getErrorMessageProvider());
      }
      playerView.requestFocus();
      if (sphericalStereoMode != null) {
        int stereoMode;
        if (SPHERICAL_STEREO_MODE_MONO.equals(sphericalStereoMode)) {
          stereoMode = C.STEREO_MODE_MONO;
        } else if (SPHERICAL_STEREO_MODE_TOP_BOTTOM.equals(sphericalStereoMode)) {
          stereoMode = C.STEREO_MODE_TOP_BOTTOM;
        } else if (SPHERICAL_STEREO_MODE_LEFT_RIGHT.equals(sphericalStereoMode)) {
          stereoMode = C.STEREO_MODE_LEFT_RIGHT;
        } else {
          showToast(R.string.error_unrecognized_stereo_mode);
          finish();
          return;
        }
        ((SphericalGLSurfaceView) playerView.getVideoSurfaceView()).setDefaultStereoMode(stereoMode);
      }
    }

    // Restore instance state
    onRestoreInstanceState(null);
  }

  @Override
  public SimpleExoPlayer getPlayer() {
    return player;
  }

  @Override
  public PlayerView getPlayerView() {
    return playerView;
  }

  // Activity lifecycle
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return (playerView != null && playerView.dispatchKeyEvent(event)) ||
        (getView() != null && getView().dispatchKeyEvent(event));
  }

  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // OnClickListener methods
  @Override
  public void onClick(View view) {
    if (view == selectTracksButton
        && !isShowingTrackSelectionDialog
        && TrackSelectionDialog.willHaveContent(trackSelector)) {
      isShowingTrackSelectionDialog = true;
      TrackSelectionDialog trackSelectionDialog =
          TrackSelectionDialog.createForTrackSelector(
              trackSelector,
              /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);

      if (ContextHelper.isAppCompatActivity(getContext())) {
        trackSelectionDialog.show(
            ContextHelper.getAppCompatActivity(getContext()).getSupportFragmentManager(), /* tag= */
            null);
      }
    }
  }

  // PlaybackControlView.VisibilityListener implementation
  @Override
  public void onVisibilityChange(int visibility) {
    setDebugTextVisibility(View.VISIBLE);
    setDebugRootVisibility(visibility);
  }

  // Internal methods
  @Override
  public void initializePlayer() {
    if (player == null) {
      buildPlayer();
    }
    if (player != null) {
      boolean haveStartPosition = startWindow != C.INDEX_UNSET;
      if (haveStartPosition) {
        player.seekTo(startWindow, startPosition);
      }
      player.prepare(mediaSource, !haveStartPosition, false);
    }
    updateButtonVisibility();
  }

  protected void buildPlayer() {
    Intent intent = getIntent();

    mediaSource = createTopLevelMediaSource(intent);
    if (mediaSource == null) {
      return;
    }

    // initialize track selection
    TrackSelection.Factory trackSelectionFactory;
    String abrAlgorithm = intent.getStringExtra(ABR_ALGORITHM_EXTRA);
    if (abrAlgorithm == null || ABR_ALGORITHM_DEFAULT.equals(abrAlgorithm)) {
      trackSelectionFactory = new AdaptiveTrackSelection.Factory();
    } else if (ABR_ALGORITHM_RANDOM.equals(abrAlgorithm)) {
      trackSelectionFactory = new RandomTrackSelection.Factory();
    } else {
      showToast(R.string.error_unrecognized_abr_algorithm);
      finish();
      return;
    }

    boolean preferExtensionDecoders =
        intent.getBooleanExtra(PREFER_EXTENSION_DECODERS_EXTRA, false);
    RenderersFactory renderersFactory = buildRenderersFactory(preferExtensionDecoders);

    trackSelector = new DefaultTrackSelector(getContext(), trackSelectionFactory);
    trackSelector.setParameters(trackSelectorParameters);
    lastSeenTrackGroupArray = null;

    player =
        new SimpleExoPlayer.Builder(/* context= */ getContext(), renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(getLoadControl())
            .build();
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
    if (adsLoader != null) {
      adsLoader.setPlayer(player);
    }
  }

  @Nullable
  protected MediaSource createTopLevelMediaSource(Intent intent) {
    // initialize arguments
    String action = intent.getAction();
    boolean actionIsListView = ACTION_VIEW_LIST.equals(action);
    if (!actionIsListView && !ACTION_VIEW.equals(action)) {
      showToast(getString(R.string.unexpected_intent_action, action));
      finish();
      return null;
    }

    Sample intentAsSample = Sample.createFromIntent(intent);
    Sample.UriSample[] samples =
        intentAsSample instanceof Sample.PlaylistSample
            ? ((Sample.PlaylistSample) intentAsSample).children
            : new Sample.UriSample[] {(Sample.UriSample) intentAsSample};

    boolean seenAdsTagUri = false;
    for (Sample.UriSample sample : samples) {
      seenAdsTagUri |= sample.adTagUri != null;
      if (!Util.checkCleartextTrafficPermitted(sample.uri)) {
        showToast(R.string.error_cleartext_not_permitted);
        return null;
      }
      if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ ContextHelper.getActivity(getContext()), sample.uri)) {
        // The player will be reinitialized if the permission is granted.
        return null;
      }
    }

    MediaSource[] mediaSources = new MediaSource[samples.length];
    for (int i = 0; i < samples.length; i++) {
      mediaSources[i] = createLeafMediaSource(samples[i]);
      Sample.SubtitleInfo subtitleInfo = samples[i].subtitleInfo;
      if (subtitleInfo != null) {
        Format subtitleFormat =
            Format.createTextSampleFormat(
                /* id= */ null,
                subtitleInfo.mimeType,
                C.SELECTION_FLAG_DEFAULT,
                subtitleInfo.language);
        MediaSource subtitleMediaSource =
            new SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subtitleInfo.uri, subtitleFormat, C.TIME_UNSET);
        mediaSources[i] = new MergingMediaSource(mediaSources[i], subtitleMediaSource);
      }
    }
    MediaSource mediaSource =
        mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources);

    if (seenAdsTagUri) {
      Uri adTagUri = samples[0].adTagUri;
      if (actionIsListView) {
        showToast(R.string.unsupported_ads_in_concatenation);
      } else {
        if (!adTagUri.equals(loadedAdTagUri)) {
          releaseAdsLoader();
          loadedAdTagUri = adTagUri;
        }
        MediaSource adsMediaSource = createAdsMediaSource(mediaSource, adTagUri);
        if (adsMediaSource != null) {
          mediaSource = adsMediaSource;
        } else {
          showToast(R.string.ima_not_loaded);
        }
      }
    } else {
      releaseAdsLoader();
    }

    return mediaSource;
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
    if (adsLoader != null) {
      adsLoader.setPlayer(null);
    }
  }

  // User controls
  @Override
  protected void updateButtonVisibility() {
    if (selectTracksButton != null) {
      selectTracksButton.setEnabled(
          player != null && TrackSelectionDialog.willHaveContent(trackSelector));
    }
  }

  @Override
  protected void showControls() {
    setDebugRootVisibility(View.VISIBLE);
  }

  private void setDebugRootVisibility(int visibility) {
    PlayerUtils.setDebugVisibility(debugRootView, debug(), visibility);
  }

  private void setDebugTextVisibility(int visibility) {
    PlayerUtils.setDebugVisibility(debugTextView, debug(), visibility);
  }

  // Player.DefaultEventListener
  @Override
  @SuppressWarnings("ReferenceEquality")
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    super.onTracksChanged(trackGroups, trackSelections);
    if (trackGroups != lastSeenTrackGroupArray) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          showToast(R.string.error_unsupported_video);
        }
        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          showToast(R.string.error_unsupported_audio);
        }
      }
      lastSeenTrackGroupArray = trackGroups;
    }
  }

  protected LoadControl getLoadControl() {
    return new DefaultLoadControl();
  }

  protected ErrorMessageProvider<ExoPlaybackException> getErrorMessageProvider() {
    return null;
  }

  protected String getApplicationName() {
      return SimpleExoPlayerManager.class.getSimpleName();
  }

  public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
    return new DefaultRenderersFactory(getContext())
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
  }

  /*** Returns a new DataSource factory.*/
  @Override
  public DataSource.Factory buildDataSourceFactory() {
    DefaultDataSourceFactory upstreamFactory =
        new DefaultDataSourceFactory(getContext(), buildHttpDataSourceFactory());
    return upstreamFactory;
  }

  /*** Returns a {@link HttpDataSource.Factory}.*/
  @Override
  public HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSourceFactory(Util.getUserAgent(getContext(), getApplicationName()));
  }

  @Override
  public MediaSource createLeafMediaSource(Sample.UriSample parameters) {
    Sample.DrmInfo drmInfo = parameters.drmInfo;
    int errorStringId = R.string.error_drm_unknown;
    DrmSessionManager<ExoMediaCrypto> drmSessionManager = null;
    if (drmInfo == null) {
      drmSessionManager = DrmSessionManager.getDummyDrmSessionManager();
    } else if (Util.SDK_INT < 18) {
      errorStringId = R.string.error_drm_unsupported_before_api_18;
    } else if (!MediaDrm.isCryptoSchemeSupported(drmInfo.drmScheme)) {
      errorStringId = R.string.error_drm_unsupported_scheme;
    } else {
      MediaDrmCallback mediaDrmCallback =
          createMediaDrmCallback(drmInfo.drmLicenseUrl, drmInfo.drmKeyRequestProperties);
      drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setUuidAndExoMediaDrmProvider(drmInfo.drmScheme, FrameworkMediaDrm.DEFAULT_PROVIDER)
              .setMultiSession(drmInfo.drmMultiSession)
              .build(mediaDrmCallback);
    }

    if (drmSessionManager == null) {
      showToast(errorStringId);
      finish();
      return null;
    }

    return createLeafMediaSource(parameters.uri, parameters.extension, drmSessionManager);
  }

  @Override
  public MediaSource createLeafMediaSource(
      Uri uri, String extension, DrmSessionManager<ExoMediaCrypto> drmSessionManager) {
    return PlayerUtils.createLeafMediaSource(uri, extension, dataSourceFactory, drmSessionManager);
  }

  /*** DRM Dependency methods */
  @Override
  public HttpMediaDrmCallback createMediaDrmCallback(
      String licenseUrl, String[] keyRequestPropertiesArray) {
    HttpDataSource.Factory licenseDataSourceFactory = buildHttpDataSourceFactory();
    HttpMediaDrmCallback drmCallback =
        new HttpMediaDrmCallback(licenseUrl, licenseDataSourceFactory);
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
        drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
            keyRequestPropertiesArray[i + 1]);
      }
    }
    return drmCallback;
  }

  /*** Returns an ads media source, reusing the ads loader if one exists.*/
  @Override
  @Nullable
  public MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
    // Load the extension source using reflection so the demo app doesn't have to depend on it.
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    try {
      Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
      if (adsLoader == null) {
        // Full class names used so the LINT.IfChange rule triggers should any of the classes move.
        // LINT.IfChange
        Constructor<? extends AdsLoader> loaderConstructor =
            loaderClass
                .asSubclass(AdsLoader.class)
                .getConstructor(android.content.Context.class, android.net.Uri.class);
        // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
        adsLoader = loaderConstructor.newInstance(this, adTagUri);
      }
      MediaSourceFactory adMediaSourceFactory =
          new MediaSourceFactory() {
            @Override
            public MediaSource createMediaSource(Uri uri) {
              return SimpleExoPlayerManager.this.createLeafMediaSource(
                  uri, /* extension=*/ null, DrmSessionManager.getDummyDrmSessionManager());
            }

            @Override
            public int[] getSupportedTypes() {
              return new int[] {C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
            }
          };
      return new AdsMediaSource(mediaSource, adMediaSourceFactory, adsLoader, playerView);
    } catch (ClassNotFoundException e) {
      // IMA extension not loaded.
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void releaseAdsLoader() {
    if (adsLoader != null) {
      adsLoader.release();
      adsLoader = null;
      loadedAdTagUri = null;
      playerView.getOverlayFrameLayout().removeAllViews();
    }
  }
}
