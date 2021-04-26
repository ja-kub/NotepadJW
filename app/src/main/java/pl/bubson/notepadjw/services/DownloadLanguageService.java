package pl.bubson.notepadjw.services;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;

import androidx.annotation.Nullable;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.utils.Language;

import static pl.bubson.notepadjw.core.BookNamesMapping.languageUriMap;

/**
 * Created by Kuba on 2016-12-31.
 * <p/>
 * Service to download new Bible in given language using DownloadManager.
 * After successful download, it starts InstallLanguageService and stops itself.
 */
public class DownloadLanguageService extends Service {

    private static final String APP_TEMP_FOLDER_NAME = Environment.DIRECTORY_DOWNLOADS; // previously it was "Notepad JW temporary files", but:
    // "For applications targeting Build.VERSION_CODES.Q or above, WRITE_EXTERNAL_STORAGE permission is not needed and the dirType must be one of the known public directories like Environment#DIRECTORY_DOWNLOADS"
    // It also requires using requestLegacyExternalStorage in the manifest. Unfortunately, this is just a temporary solution - see this: https://stackoverflow.com/questions/56821095/android-q-file-mkdirs-returns-false
    // Google sucks so much!
    BroadcastReceiver broadcastReceiver;
    private long enqueue;
    private DownloadManager downloadManager;

    @Override
    public void onCreate() {
        Log.v("DownloadLanguageService", "onCreate() executed");
        registerBroadcastReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v("DownloadLanguageService", "onStartCommand() executed");
        try {
            Language language = (Language) intent.getSerializableExtra("Language");
            downloadLanguage(language);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        try {
            unregisterBroadcastReceiver();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void downloadLanguage(Language language) {
        Uri uri = Uri.parse(languageUriMap.get(language));
        File downloadedFile = Environment.getExternalStoragePublicDirectory(APP_TEMP_FOLDER_NAME + "/" + uri.getLastPathSegment());
        if (downloadedFile.exists()) {
            try {
                Intent installLanguageServiceIntent = new Intent(this, InstallLanguageService.class);
                installLanguageServiceIntent.setData(Uri.fromFile(downloadedFile));
                installLanguageServiceIntent.putExtra("Language", language);
                startService(installLanguageServiceIntent);
            } finally {
                stopSelf();
            }
        } else {
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setDescription(getString(R.string.download_description));
            request.setTitle(getString(R.string.download_language) + ": " + language.name());
            request.allowScanningByMediaScanner();
//            request.setDestinationInExternalPublicDir(APP_TEMP_FOLDER_NAME, uri.getLastPathSegment()); // that was previously, but it requires WRITE_EXTERNAL_STORAGE which is not available from Android 11
            request.setDestinationInExternalFilesDir(this, APP_TEMP_FOLDER_NAME, uri.getLastPathSegment()); // This requires no permissions on Android 4.4 or newer! I don't know why I didn't use from the beginning this method instead above one...

            downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            enqueue = downloadManager.enqueue(request);
        }
    }

    private void registerBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(enqueue);
                    Cursor cursor = downloadManager.query(query);
                    if (cursor.moveToFirst()) {
                        int statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int uriColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                        int localUriColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusColumnIndex)) {
                            try {
                                String localUri = cursor.getString(localUriColumnIndex);
                                String uri = cursor.getString(uriColumnIndex);
                                Language language = languageUriMap.getKey(uri);

                                Intent installLanguageServiceIntent = new Intent(context, InstallLanguageService.class);
                                installLanguageServiceIntent.setData(Uri.parse(localUri));
                                installLanguageServiceIntent.putExtra("Language", language);
                                context.startService(installLanguageServiceIntent);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                cursor.close();
                                stopSelf();
                            }
                        }
                    }
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void unregisterBroadcastReceiver() {
        unregisterReceiver(broadcastReceiver);
    }
}
