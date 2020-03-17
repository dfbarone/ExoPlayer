package com.google.android.exoplayer2.managerdemo;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;
import android.view.View;

import androidx.annotation.Nullable;
import com.dfbarone.android.exoplayer2.manager.Sample.UriSample;
import com.dfbarone.android.exoplayer2.manager.SimpleExoPlayerManager;
import com.dfbarone.android.exoplayer2.manager.util.ContextHelper;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;

import java.lang.reflect.Constructor;

public class DemoPlayerManager extends SimpleExoPlayerManager {

  public DemoPlayerManager(Context context, View root) {
    super(context, root);
  }

  @Override
  public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
    return ((DemoApplication) ContextHelper.getApplication(getContext())).buildRenderersFactory(preferExtensionRenderer);
  }

  /** Returns a new DataSource factory. */
  @Override
  public DataSource.Factory buildDataSourceFactory() {
    return ((DemoApplication) ContextHelper.getApplication(getContext())).buildDataSourceFactory();
  }

  @Override
  public HttpDataSource.Factory buildHttpDataSourceFactory() {
    return ((DemoApplication) ContextHelper.getApplication(getContext())).buildHttpDataSourceFactory();
  }

  @Override
  public MediaSource createLeafMediaSource(UriSample paramters) {
    DownloadRequest downloadRequest =
        ((DemoApplication) ContextHelper.getApplication(getContext())).getDownloadTracker().getDownloadRequest(paramters.uri);
    if (downloadRequest != null) {
      return DownloadHelper.createMediaSource(downloadRequest, dataSourceFactory);
    }
    return super.createLeafMediaSource(paramters);
  }

  /** Returns an ads media source, reusing the ads loader if one exists. */
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
              return DemoPlayerManager.this.createLeafMediaSource(
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

  @Override
  protected ErrorMessageProvider<ExoPlaybackException> getErrorMessageProvider() {
    return new PlayerErrorMessageProvider();
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

    @Override
    public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
      String errorString = getString(R.string.error_generic);
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = e.getRendererException();
        if (cause instanceof DecoderInitializationException) {
          // Special case for decoder initialization failures.
          DecoderInitializationException decoderInitializationException =
              (DecoderInitializationException) cause;
          if (decoderInitializationException.codecInfo == null) {
            if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
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
