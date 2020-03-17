package com.dfbarone.android.exoplayer2.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import android.view.View;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import java.util.UUID;

/**
 * Created by dfbarone on 5/17/2018.
 * <p>
 * A class to enforce common and hopefully useful ExoPlayer methods. This class attempts to avoid ui
 * or state methods.
 */
public abstract class PlayerManager<D> implements Player.EventListener {

  // Injected interfaces
  private EventListener eventListener;

  // Context and root View of player
  private final Context mContext;
  private final View itemView;

  // 1) Optional place to store playback information. In many cases this is probably the simplest
  // way to store playback information.
  private Intent mIntent = new Intent();

  // 2) Optional place to store playback information. In some cases that may be easier than using
  // an intent.
  private D mData = null;

  /*** Default constructor*/
  protected PlayerManager(Context context, View itemView) {
    if (context == null) {
      throw new IllegalArgumentException("context may not be null");
    }
    this.mContext = context;
    this.itemView = itemView;
  }

  /*** Common player methods */
  protected abstract <P extends Player> P getPlayer();

  protected abstract <V extends PlayerView> V getPlayerView();

  protected abstract void initializePlayer();

  protected abstract void releasePlayer();

  protected abstract void releaseAdsLoader();

  /*** Getters/Setters */
  public Context getContext() {
    return mContext;
  }

  public View getView() {
    return itemView;
  }

  // Intent methods
  public Intent getIntent() {
    return mIntent;
  }

  public void setIntent(Intent intent) {
    mIntent = intent;
  }

  // Data methods
  public D getData() {
    return mData;
  }

  public void setData(D data) {
    mData = data;
  }

  public String getString(int resId) {
    return getContext().getString(resId);
  }

  public String getString(int resId, Object... formatArgs) {
    return getContext().getString(resId, formatArgs);
  }

  // Listener for internal need to finish
  public void setEventListener(EventListener listener) {
    eventListener = listener;
  }

  // Event listener methods

  /*** Notify parent of non-fatal error */
  protected void showToast(int resId) {
    showToast(getString(resId), null);
  }

  protected void showToast(String message) {
    showToast(message, null);
  }

  /*** Notify parent of potentially fatal error. IllegalStateException is a fatal error in initializePlayer(). */
  protected void showToast(String message, Throwable e) {
    if (eventListener != null) {
      eventListener.onShowToast(message, null);
    }
  }

  protected void finish() {
    if (eventListener != null) {
      eventListener.onFinish();
    }
  }

  /*** PlayerManager Dependencies*/
  public interface EventListener {

    /***
     * Notify parent of potentially fatal error. IllegalStateException is a fatal error in
     * initializePlayer().
     */
    void onShowToast(String message, Throwable e);

    void onFinish();
  }

  /*** MediaSource builder methods */
  public interface MediaSourceBuilder {

    MediaSource createLeafMediaSource(Sample.UriSample parameters);

    MediaSource createLeafMediaSource(Uri uri, String extension, DrmSessionManager<ExoMediaCrypto> drmSessionManager);
  }

  /*** DataSource.Factory builder methods */
  public interface DataSourceBuilder {

    /*** Returns a {@link DataSource.Factory}.*/
    DataSource.Factory buildDataSourceFactory();

    /*** Returns a {@link HttpDataSource.Factory}.*/
    HttpDataSource.Factory buildHttpDataSourceFactory();
  }

  /*** Drm builder methods */
  public interface MedaiDrmCallbackBuilder {
    MediaDrmCallback createMediaDrmCallback(String licenseUrl, String[] keyRequestPropertyArray);
  }

  /*** Ads builder methods */
  public interface AdsMediaSourceBuilder {

    MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri);

    void releaseAdsLoader();
  }
}
