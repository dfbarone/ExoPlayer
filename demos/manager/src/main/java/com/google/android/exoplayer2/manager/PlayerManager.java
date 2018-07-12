package com.google.android.exoplayer2.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Created by dfbarone on 5/17/2018.
 * <p>
 * A class to enforce common and hopefully useful ExoPlayer methods.
 * This class attempts to avoid ui or state methods.
 */
public abstract class PlayerManager extends Player.DefaultEventListener {

  // Injected variables
  private EventListener eventListener;
  private InitializePlayer dependencies;

  // Context and root View of player
  private final Context mContext;
  private final View itemView;

  // Optional place to store playback information here
  private Intent mIntent = new Intent();

  /** Default constructor*/
  protected PlayerManager(Context context, View itemView) {
    if (context == null) {
      throw new IllegalArgumentException("context may not be null");
    }
    this.mContext = context;
    this.itemView = itemView;
  }

  // Common player methods
  protected abstract <T extends Player> T getPlayer();

  protected abstract void initializePlayer();

  protected abstract void releasePlayer();

  protected void releaseAdsLoader() {
    if (dependencies().adsMediaSourceBuilder() != null) {
      dependencies().adsMediaSourceBuilder().releaseAdsLoader();
    }
  }

  // Getters/Setters
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

  // Listener for internal need to finish
  public void setEventListener(EventListener listener) {
    eventListener = listener;
  }

  // Event listener methods
  protected void onError(String message) {
    if (eventListener != null) {
      eventListener.onError(message, null);
    }
  }

  protected void onError(String message, Exception e) {
    if (eventListener != null) {
      eventListener.onError(message, e);
    }
  }

  protected void finish(String reason) {
    if (eventListener != null) {
      eventListener.onFinish(reason);
    }
  }

  public InitializePlayer dependencies() {
    return dependencies;
  }

  public void setDependencies(InitializePlayer dependencies) {
    this.dependencies = dependencies;
  }

  /**
   *  PlayerManager Dependencies
   */
  public interface EventListener {

    /**
     * Initialization errors for output
     *
     * @param message non player related error
     * @param e       ExoPlayerException, if valid will be a player related error
     */
    void onError(String message, Exception e);

    /**
     * Programmatic attempt to close player
     *
     * @param reason empty or null reason means user initiated close
     */
    void onFinish(String reason);
  }


  /** MediaSource builder methods*/
  public interface MediaSourceBuilder {
    MediaSource buildMediaSource(Uri uri);

    MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension);
  }


  /** DataSource.Factory builder methods*/
  public interface DataSourceBuilder {
    /*** Returns a {@link DataSource.Factory}.*/
    DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter);

    /*** Returns a {@link HttpDataSource.Factory}.*/
    HttpDataSource.Factory buildHttpDataSourceFactory(
        TransferListener<? super DataSource> listener);
  }

  /** Drm builder methods*/
  public interface DrmSessionManagerBuilder {
    DefaultDrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager() throws UnsupportedDrmException;
  }

  /** Ads builder methods*/
  public interface AdsMediaSourceBuilder {
    MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri);

    void releaseAdsLoader();
  }

  /** Main initializer builder class*/
  public static class InitializePlayer {

    private DataSourceBuilder dataSourceBuilder;
    private MediaSourceBuilder mediaSourceBuilder;
    private DrmSessionManagerBuilder drmSessionManagerBuilder;
    private AdsMediaSourceBuilder adsMediaSourceBuilder;
    private LoadControl loadControl;

    /*** Required dependency*/
    public DataSourceBuilder dataSourceBuilder() {
      return dataSourceBuilder;
    }

    /*** Required dependency*/
    public MediaSourceBuilder mediaSourceBuilder() {
      return mediaSourceBuilder;
    }

    /**
     * Optional dependency
     */
    public DrmSessionManagerBuilder drmSessionManagerBuilder() {
      return drmSessionManagerBuilder;
    }

    /**
     * Optional dependency
     */
    public AdsMediaSourceBuilder adsMediaSourceBuilder() {
      return adsMediaSourceBuilder;
    }

    public LoadControl loadControl() {
      return loadControl;
    }

    public static class Builder {
      private DataSourceBuilder dataSourceBuilder;
      private MediaSourceBuilder mediaSourceBuilder;
      private DrmSessionManagerBuilder drmSessionManagerBuilder;
      private AdsMediaSourceBuilder adsMediaSourceBuilder;
      private LoadControl loadControl;

      public Builder(DataSourceBuilder dataSourceBuilder, MediaSourceBuilder mediaSourceBuilder) {
        setDataSourceBuilder(dataSourceBuilder);
        setMediaSourceBuilder(mediaSourceBuilder);
      }

      public Builder setDataSourceBuilder(DataSourceBuilder dataSourceBuilder) {
        if (dataSourceBuilder == null) {
          throw new IllegalArgumentException("DataSourceBuilder may not be null");
        }
        this.dataSourceBuilder = dataSourceBuilder;
        return this;
      }

      public Builder setMediaSourceBuilder(MediaSourceBuilder mediaSourceBuilder) {
        if (mediaSourceBuilder == null) {
          throw new IllegalArgumentException("MediaSourceBuilder may not be null");
        }
        this.mediaSourceBuilder = mediaSourceBuilder;
        return this;
      }

      public Builder setDrmSessionManagerBuilder(DrmSessionManagerBuilder drmSessionManagerBuilder) {
        this.drmSessionManagerBuilder = drmSessionManagerBuilder;
        return this;
      }

      public Builder setAdsMediaSourceBuilder(AdsMediaSourceBuilder adsMediaSourceBuilder) {
        this.adsMediaSourceBuilder = adsMediaSourceBuilder;
        return this;
      }

      public Builder setLoadControl(LoadControl loadControl) {
        this.loadControl = loadControl;
        return this;
      }

      public InitializePlayer build() {
        InitializePlayer dep = new InitializePlayer();
        dep.dataSourceBuilder = this.dataSourceBuilder;
        dep.mediaSourceBuilder = this.mediaSourceBuilder;
        dep.drmSessionManagerBuilder = this.drmSessionManagerBuilder;
        dep.adsMediaSourceBuilder = this.adsMediaSourceBuilder;
        dep.loadControl = this.loadControl;
        return dep;
      }
    }
  }

}
