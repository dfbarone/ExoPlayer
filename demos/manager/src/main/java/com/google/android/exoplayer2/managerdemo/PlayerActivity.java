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
package com.google.android.exoplayer2.managerdemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.dfbarone.android.exoplayer2.manager.PlayerManager;
import com.dfbarone.android.exoplayer2.manager.Sample;
import com.dfbarone.android.exoplayer2.manager.SimpleExoPlayerManager;
import com.dfbarone.android.exoplayer2.manager.util.ContextHelper;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;
import java.lang.reflect.Constructor;

/** An activity that plays media using {@link SimpleExoPlayerManager}. */
public class PlayerActivity extends AppCompatActivity
    implements PlayerManager.EventListener {

  private static final String TAG = PlayerActivity.class.getSimpleName();

  private SimpleExoPlayerManager playerManager;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    String sphericalStereoMode = getIntent().getStringExtra(SimpleExoPlayerManager.SPHERICAL_STEREO_MODE_EXTRA);
    if (sphericalStereoMode != null) {
      setTheme(R.style.PlayerTheme_Spherical);
    }
    super.onCreate(savedInstanceState);
    setContentView(R.layout.player_activity);

    playerManager = new SimpleExoPlayerManager(this, findViewById(R.id.root)) {
      @Override
      public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
        return ((DemoApplication) ContextHelper.getApplication(getContext())).buildRenderersFactory(preferExtensionRenderer);
      }

      @Override
      public DataSource.Factory buildDataSourceFactory() {
        return ((DemoApplication) ContextHelper.getApplication(getContext())).buildDataSourceFactory();
      }

      @Override
      public HttpDataSource.Factory buildHttpDataSourceFactory() {
        return ((DemoApplication) ContextHelper.getApplication(getContext())).buildHttpDataSourceFactory();
      }

      @Override
      public MediaSource createLeafMediaSource(Sample.UriSample paramters) {
        DownloadRequest downloadRequest =
            ((DemoApplication) ContextHelper.getApplication(getContext())).getDownloadTracker().getDownloadRequest(paramters.uri);
        if (downloadRequest != null) {
          return DownloadHelper.createMediaSource(downloadRequest, dataSourceFactory);
        }
        return super.createLeafMediaSource(paramters);
      }

      @Override
      protected ErrorMessageProvider<ExoPlaybackException> getErrorMessageProvider() {
        return new PlayerErrorMessageProvider();
      }
    };

    playerManager.onRestoreInstanceState(savedInstanceState);
    playerManager.setEventListener(this);
    playerManager.setIntent(getIntent());
    playerManager.setDebug(BuildConfig.DEBUG);
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    playerManager.onNewIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      playerManager.initializePlayer();
      if (playerManager.getPlayerView() != null) {
        playerManager.getPlayerView().onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || playerManager.getPlayer() == null) {
      playerManager.initializePlayer();
      if (playerManager.getPlayerView() != null) {
        playerManager.getPlayerView().onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerManager.getPlayerView() != null) {
        playerManager.getPlayerView().onPause();
      }
      playerManager.releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerManager.getPlayerView() != null) {
        playerManager.getPlayerView().onPause();
      }
      playerManager.releasePlayer();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    playerManager.releaseAdsLoader();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    playerManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    playerManager.onSaveInstanceState(outState);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return playerManager.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  // ExoPlayerWrapper.EventListener
  @Override
  public void onShowToast(String message, Throwable e) {
    Log.d(TAG, "onError() " + message);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onFinish() {
    Log.d(TAG, "onFinish()");
    finish();
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {
    @Override
    public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
      String errorString = getString(R.string.error_generic);
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = e.getRendererException();
        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
          // Special case for decoder initialization failures.
          MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
              (MediaCodecRenderer.DecoderInitializationException) cause;
          if (decoderInitializationException.codecInfo == null) {
            if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
              errorString = getString(R.string.error_querying_decoders);
            } else if (decoderInitializationException.secureDecoderRequired) {
              errorString =
                  getString(
                      R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
            } else {
              errorString =
                  getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
            }
          } else {
            errorString =
                getString(
                    R.string.error_instantiating_decoder,
                    decoderInitializationException.codecInfo.name);
          }
        }
      }
      return Pair.create(0, errorString);
    }
  }
}
