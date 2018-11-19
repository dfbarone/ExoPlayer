package com.google.android.exoplayer2.managerdemo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.dfbarone.android.exoplayer2.manager.util.ContextHelper;
import com.dfbarone.android.exoplayer2.manager.SimpleExoPlayerManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;

import java.lang.reflect.Constructor;
import java.util.UUID;

public class DemoPlayerManager extends SimpleExoPlayerManager {

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  // For backwards compatibility only.
  private static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

  public DemoPlayerManager(Context context, View root) {
    super(context, root);

    /* Customizations in intializePlayer */
    setPlayerDependencies(
        new CustomPlayerDependencies.Builder(
            new DemoDataSourceBuilder(),
            new DefaultMediaSourceBuilder()
        )
            .setErrorMessageProvider(new PlayerErrorMessageProvider())
            .setDrmSessionManagerBuilder(new DemoDrmSessionManagerBuilder())
            .setAdsMediaSourceBuilder(new DemoAdsMediaSourceBuilder())
            .build()
    );
  }

  // Activity lifecycle
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
      onError(getContext().getString(R.string.storage_permission_denied));
      //finish(getContext().getString(R.string.storage_permission_denied));
    }
  }

  private class DemoDataSourceBuilder implements DataSourceBuilder {
    @Override
    public DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
      // Optional
      TransferListener<? super DataSource> listener = useBandwidthMeter ? BANDWIDTH_METER : null;
      return ((DemoApplication) ContextHelper.getApplication(getContext())).buildDataSourceFactory(listener);
    }

    /**
     * Returns a {@link HttpDataSource.Factory}.
     */
    @Override
    public HttpDataSource.Factory buildHttpDataSourceFactory(
        TransferListener<? super DataSource> listener) {
      return ((DemoApplication) ContextHelper.getApplication(getContext())).buildHttpDataSourceFactory(listener);
    }
  }

  private class DemoDrmSessionManagerBuilder implements DrmSessionManagerBuilder {
    @Override
    public DefaultDrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager() throws UnsupportedDrmException {
      Intent intent = getIntent();
      String drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL_EXTRA);
      String[] keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA);
      boolean multiSession = intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA, false);
      String drmSchemeExtra = intent.hasExtra(DRM_SCHEME_EXTRA) ? DRM_SCHEME_EXTRA : DRM_SCHEME_UUID_EXTRA;
      UUID drmSchemeUuid = Util.getDrmUuid(intent.getStringExtra(drmSchemeExtra));
      if (drmSchemeUuid != null) {
        return buildDrmSessionManagerV18(drmSchemeUuid, drmLicenseUrl, keyRequestPropertiesArray, multiSession);
      } else {
        return null;
      }
    }

    private DefaultDrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManagerV18(
        UUID uuid, String licenseUrl, String[] keyRequestPropertiesArray, boolean multiSession)
        throws UnsupportedDrmException {
      HttpDataSource.Factory licenseDataSourceFactory =
          ((DemoApplication) ContextHelper.getApplication(getContext())).buildHttpDataSourceFactory(/* listener= */ null);
      HttpMediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(licenseUrl, licenseDataSourceFactory);
      if (keyRequestPropertiesArray != null) {
        for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
          drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
              keyRequestPropertiesArray[i + 1]);
        }
      }
      return new DefaultDrmSessionManager<>(
          uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback, null, multiSession);
    }
  }

  private class DemoAdsMediaSourceBuilder implements AdsMediaSourceBuilder {
    // Fields used only for ad playback. The ads loader is loaded via reflection.
    protected AdsLoader adsLoader;
    protected ViewGroup adUiViewGroup;

    /**
     * Returns an ads media source, reusing the ads loader if one exists.
     */
    @Override
    public @Nullable
    MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
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
                  .getConstructor(Context.class, Uri.class);
          // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
          adsLoader = loaderConstructor.newInstance(this, adTagUri);
          adUiViewGroup = new FrameLayout(/*this*/getContext());
          // The demo app has a non-null overlay frame layout.
          if (playerView != null) {
            playerView.getOverlayFrameLayout().addView(adUiViewGroup);
          }
        }
        AdsMediaSource.MediaSourceFactory adMediaSourceFactory =
            new AdsMediaSource.MediaSourceFactory() {
              @Override
              public MediaSource createMediaSource(Uri uri) {
                return playerDependencies().mediaSourceBuilder().buildMediaSource(uri);
              }

              @Override
              public int[] getSupportedTypes() {
                return new int[]{C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
              }
            };
        return new AdsMediaSource(mediaSource, adMediaSourceFactory, adsLoader, adUiViewGroup);
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
        if (playerView != null) {
          playerView.getOverlayFrameLayout().removeAllViews();
        }
      }
    }
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

    @Override
    public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
      String errorString = getContext().getString(R.string.error_generic);
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

}
