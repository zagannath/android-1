package org.owntracks.android;

import org.owntracks.android.injection.components.AppComponent;
import org.owntracks.android.injection.components.DaggerAppComponent;
import org.owntracks.android.injection.modules.AppModule;
import org.owntracks.android.services.BackgroundService;
import org.owntracks.android.support.ContactImageProvider;
import org.owntracks.android.support.GeocodingProvider;
import org.owntracks.android.support.Parser;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.ui.map.MapActivity;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import timber.log.Timber;

public class App extends Application  {
    private Activity currentActivity;
    private static boolean inForeground;
    private int runningActivities = 0;

    private static AppComponent sAppComponent = null;


    @Override
	public void onCreate() {
		super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree() {
                @Override
                protected String createStackElementTag(StackTraceElement element) {
                    return super.createStackElementTag(element) + "/" + element.getMethodName() + "/" + element.getLineNumber();
                }
            });
        }
        sAppComponent = DaggerAppComponent.builder().appModule(new AppModule(this)).build();
        sAppComponent.preferences().checkFirstStart();

        enableForegroundBackgroundDetection();

        // Running this on a background thread will deadlock FirebaseJobDispatcher.
        // Initialize will call Scheduler to connect off the main thread anyway.
        sAppComponent.runner().postOnMainHandlerDelayed (new Runnable() {
            @Override
            public void run() {
                sAppComponent.messageProcessor().initialize();
            }
        }, 510);

    }

    public static AppComponent getAppComponent() { return sAppComponent; }

    @Deprecated
    public static GeocodingProvider getGeocodingProvider() { return sAppComponent.geocodingProvider(); }

    @Deprecated
    public static ContactImageProvider getContactImageProvider() { return sAppComponent.contactImageProvider(); }

    @Deprecated
    public static Parser getParser() { return sAppComponent.parser(); }

    @Deprecated
    public static Preferences getPreferences() { return sAppComponent.preferences(); }

    private void enableForegroundBackgroundDetection() {
        registerActivityLifecycleCallbacks(new LifecycleCallbacks());
        registerScreenOnReceiver();
    }

    public static Context getContext() {
		return sAppComponent.context();
	}

    private void onEnterForeground() {
        Timber.v("entering foreground");
	    Timber.v("entering background git");
	    Timber.v("entering background git1");
	    Timber.v("entering background git2");
        inForeground = true;
        sAppComponent.runner().postOnBackgroundHandler(new Runnable() {
            @Override
            public void run() {
                startBackgroundServiceCompat(getContext(), BackgroundService.INTENT_ACTION_CHANGE_BG);
                sAppComponent.messageProcessor().onEnterForeground();
            }
        });
    }

    private void onEnterBackground() {
        Timber.v("entering background");
	    Timber.v("entering background git");
        Timber.v("entering background stash1");

        inForeground = false;
        sAppComponent.runner().postOnBackgroundHandler(new Runnable() {
            @Override
            public void run() {
                startBackgroundServiceCompat(getContext(), BackgroundService.INTENT_ACTION_CHANGE_BG);
                sAppComponent.messageProcessor().onEnterBackground();
            }
        });
    }

    public static boolean isInForeground() {
        return inForeground;
    }

    public static void restart() {
        Intent intent = new Intent(getContext(), MapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        Runtime.getRuntime().exit(0);
    }

    public static void startBackgroundServiceCompat(final @NonNull Context context) {
        startBackgroundServiceCompat(context, (new Intent(context, BackgroundService.class)));
    }
    public static void startBackgroundServiceCompat(final @NonNull Context context, final @Nullable String action) {
        startBackgroundServiceCompat(context, (new Intent(context, BackgroundService.class)).setAction(action));
    }

    public static void startBackgroundServiceCompat(final @NonNull Context context, @NonNull final Intent intent) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            }
        };
        sAppComponent.runner().postOnBackgroundHandlerDelayed(r, 1000);
    }


    /*
     * Keeps track of running activities and if the app is in running in the foreground or background
     */
    private final class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        public void onActivityStarted(Activity activity) {

            runningActivities++;
            currentActivity = activity;
            if (runningActivities == 1) onEnterForeground();
        }

        public void onActivityStopped(Activity activity) {
            runningActivities--;
            if(currentActivity == activity)  currentActivity = null;
            if (runningActivities == 0) onEnterBackground();
        }

        public void onActivityResumed(Activity activity) {  }
        public void onActivityPaused(Activity activity) {  }
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
        public void onActivityDestroyed(Activity activity) { }
    }
    private void registerScreenOnReceiver() {
        final IntentFilter theFilter = new IntentFilter();

        // System Defined Broadcast
        theFilter.addAction(Intent.ACTION_SCREEN_ON);
        theFilter.addAction(Intent.ACTION_SCREEN_OFF);

        // Sets foreground and background modes based on device lock and unlock if the app is active
        BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String strAction = intent.getAction();
                Timber.v("screenOnOffReceiver intent received");
                if ((Intent.ACTION_SCREEN_OFF.equals(strAction) || Intent.ACTION_SCREEN_ON.equals(strAction)) && isInForeground())
                {
                        onEnterBackground();
                }
            }
        };

        registerReceiver(screenOnOffReceiver, theFilter);

    }

    private void requestLocationUpdates() {

    }
}
