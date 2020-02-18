package com.dfbarone.android.exoplayer2.manager.util;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

public final class ContextHelper {
  public static boolean isActivity(Context context) {
    return context instanceof Activity;
  }

  public static boolean isAppCompatActivity(Context context) {
    return context instanceof AppCompatActivity;
  }

  public static boolean isService(Context context) {
    return context instanceof Service;
  }

  public static Activity getActivity(Context context) {
    if (isActivity(context)) {
      return ((AppCompatActivity) context);
    }
    throw new RuntimeException("Context is not instanceof " + AppCompatActivity.class.getSimpleName());
  }

  public static AppCompatActivity getAppCompatActivity(Context context) {
    if (isActivity(context)) {
      return ((AppCompatActivity) context);
    }
    throw new RuntimeException("Context is not instanceof " + AppCompatActivity.class.getSimpleName());
  }

  public static Service getService(Context context) {
    if (isService(context)) {
      return ((Service) context);
    }
    throw new RuntimeException("Context is not instanceof " + Service.class.getSimpleName());
  }

  public static Application getApplication(Context context) {
    return isActivity(context) ? getActivity(context).getApplication() : getService(context).getApplication();
  }
}
