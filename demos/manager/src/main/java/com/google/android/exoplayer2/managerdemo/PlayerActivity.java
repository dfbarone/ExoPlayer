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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.dfbarone.android.exoplayer2.manager.PlayerManager;
import com.google.android.exoplayer2.util.Util;

/** An activity that plays media using {@link DemoPlayerManager}. */
public class PlayerActivity extends AppCompatActivity
    implements PlayerManager.EventListener {

  private static final String TAG = PlayerActivity.class.getSimpleName();

  private DemoPlayerManager playerManager;

  // Activity lifecycle
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.player_activity);

    playerManager = new DemoPlayerManager(this, findViewById(R.id.root));
    playerManager.onRestoreInstanceState(savedInstanceState);
    playerManager.setEventListener(this);
    playerManager.setIntent(getIntent());
    playerManager.setDebug(BuildConfig.DEBUG);
  }

  @Override
  public void onNewIntent(Intent intent) {
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
    playerManager.onSaveInstanceState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return playerManager.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  // ExoPlayerWrapper.EventListener
  @Override
  public void onError(String message, Exception e) {
    Log.d(TAG, "onError() " + message);
    Toast.makeText(this, message, Toast.LENGTH_LONG);

    // Initialization error. finish.
    if (e instanceof IllegalStateException) {
      finish();
    }
  }

  @Override
  public void onFinish() {
    Log.d(TAG, "onFinish()");
    finish();
  }

}
