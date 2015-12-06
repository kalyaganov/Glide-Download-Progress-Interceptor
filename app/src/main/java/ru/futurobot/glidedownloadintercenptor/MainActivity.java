package ru.futurobot.glidedownloadintercenptor;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;

import java.util.concurrent.ExecutionException;

import ru.futurobot.glidedownloadintercenptor.listener.GlideProgressListener;
import ru.futurobot.glidedownloadintercenptor.listener.ProgressListener;

public class MainActivity extends AppCompatActivity {

    ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        findViewById(R.id.download_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AsyncDownloadTask().execute();
            }
        });
    }

    private class AsyncDownloadTask extends AsyncTask<Void, Long, Void> {


        @Override
        protected Void doInBackground(Void... params) {
            ProgressListener listener = new ProgressListener() {
                @Override
                public void update(long bytesRead, long contentLength, boolean done) {
                    publishProgress(bytesRead, contentLength);
                }
            };
            GlideProgressListener.addGlideProgressListener(listener);
            try {
                Glide.with(MainActivity.this)
                        .load("http://cs5.pikabu.ru/post_img/2015/12/05/11/1449343244168942586.gif")
                        .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            GlideProgressListener.removeGlideProgressListener(listener);
            Glide.get(MainActivity.this).clearDiskCache();
            return null;
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            if (mProgressBar != null) {
                mProgressBar.setProgress((int) ((100L * values[0]) / values[1]));
                mProgressBar.setMax(100);
            }
        }
    }
}
