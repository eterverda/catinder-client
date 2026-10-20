package com.catinder;

import android.app.Application;
import com.catinder.images.CatinderImageLoader;
import com.catinder.logging.MiniLogger;

public class App extends Application
{

    public static final String TAG = "Catinder";


    @Override
    public void onCreate() {
        super.onCreate();
        CatinderImageLoader.init(this);
        MiniLogger.d("App created");
    }
}
