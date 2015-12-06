package ru.futurobot.glidedownloadintercenptor.listener;

import android.util.Log;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Alexey on 06.12.15.
 */
public class GlideProgressListener {

    private GlideProgressListener() {
    }

    private static OkHttpClient glideOkHttpClient;

    /**
     * List of listeners
     */
    private static final List<WeakReference<ProgressListener>> glideProgressListeners = Collections.synchronizedList(new ArrayList<WeakReference<ProgressListener>>());

    /**
     * Interceptor listener
     */
    private static final ProgressListener glideDownloadProgressListener = new ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
            for (int i = 0, j = glideProgressListeners.size(); i < j; i++) {
                WeakReference<ProgressListener> weakListener = glideProgressListeners.get(i);
                ProgressListener listener = weakListener.get();
                if (listener == null) {
                    glideProgressListeners.remove(i);
                    i--;
                } else {
                    listener.update(bytesRead, contentLength, done);
                }
            }
        }
    };

    /**
     * Get singleton
     * @return
     */
    public static OkHttpClient getGlideOkHttpClient(){
        if(glideOkHttpClient == null){
            glideOkHttpClient = new OkHttpClient();
            glideOkHttpClient.networkInterceptors().add(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody(originalResponse.body(), glideDownloadProgressListener))
                            .build();
                }
            });
        }
        return glideOkHttpClient;
    }

    /**
     * Add new progress listener
     * @param listener
     */
    public static void addGlideProgressListener(ProgressListener listener) {
        if (listener != null) {
            if (findGlideProgressListener(listener) != null) {
                return;
            } else {
                glideProgressListeners.add(new WeakReference<ProgressListener>(listener));
                Log.d(GlideProgressListener.class.getSimpleName(), "Glide download listener add");
            }
        }
    }

    /**
     * Remove progress listener
     * @param listener
     */
    public static void removeGlideProgressListener(ProgressListener listener) {
        if (listener != null) {
            WeakReference<ProgressListener> founded = findGlideProgressListener(listener);
            if (founded != null) {
                glideProgressListeners.remove(founded);
                Log.d(GlideProgressListener.class.getSimpleName(), "Glide download listener remove");
            }
        }
    }

    private static WeakReference<ProgressListener> findGlideProgressListener(ProgressListener listener) {
        List<WeakReference<ProgressListener>> listeners = glideProgressListeners;
        for (int i = 0, j = listeners.size(); i < j; i++) {
            WeakReference<ProgressListener> wpl = listeners.get(i);
            if (wpl.get() == listener) {
                return wpl;
            }
        }
        return null;
    }
}
