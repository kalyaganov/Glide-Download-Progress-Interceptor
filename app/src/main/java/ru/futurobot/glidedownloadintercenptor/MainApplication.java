package ru.futurobot.glidedownloadintercenptor;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.bumptech.glide.Glide;
import com.bumptech.glide.integration.okhttp.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;

import java.io.InputStream;

import ru.futurobot.glidedownloadintercenptor.listener.GlideProgressListener;

/**
 * Created by Alexey on 06.12.15.
 */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if(bundle.getString("ru.futurobot.glidedownloadintercenptor.MyGlideModule") == null) {
                installDownloadInterceptor();
            }
        } catch (PackageManager.NameNotFoundException e) {
            installDownloadInterceptor();
        }
    }

    private void installDownloadInterceptor(){
        Glide.get(this)
                .register(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(GlideProgressListener.getGlideOkHttpClient()));
    }
}
