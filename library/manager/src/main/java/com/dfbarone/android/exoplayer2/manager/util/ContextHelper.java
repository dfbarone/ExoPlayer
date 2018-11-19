package com.dfbarone.android.exoplayer2.manager.util;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;

public final class ContextHelper {
  public static boolean isActivity(Context context) {
    return context instanceof Activity;
  }

  public static boolean isService(Context context) {
    return context instanceof Service;
  }

  public static Activity getActivity(Context context) {
    if (isActivity(context)) {
      return ((Activity) context);
    }
    throw new RuntimeException("Context is not instanceof " + Activity.class.getSimpleName());
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
