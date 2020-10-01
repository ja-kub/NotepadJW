package pl.bubson.notepadjw.services;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import net.lingala.zip4j.ZipFile;

import java.io.File;

import androidx.core.app.NotificationCompat;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.activities.FileManagerActivity;
import pl.bubson.notepadjw.databases.BiblesDatabase;
import pl.bubson.notepadjw.utils.Language;

import static pl.bubson.notepadjw.activities.FileManagerActivity.deleteRecursive;

/**
 * Created by Kuba on 2016-12-31.
 * <p/>
 * IntentService to install given language.
 */
public class InstallLanguageService extends IntentService {
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public InstallLanguageService(String name) {
        super(name);
    }

    public InstallLanguageService() {
        super(null);
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        BiblesDatabase dbh = new BiblesDatabase(this);
        if (workIntent.getData() == null) {
            Log.v("InstallLanguageService", "pre-install start");
            dbh.installDbFromAssetsIfNeeded();
            Log.v("InstallLanguageService", "pre-install end");
        } else {
            try {
                Uri uri = workIntent.getData();
                File downloadedFile = new File(uri.getPath());
                Language language = (Language) workIntent.getSerializableExtra("Language");
                if (!dbh.isBibleInDatabase(language)) {
                    int notificationId = language.name().hashCode();
                    NotificationCompat.Builder notificationBuilder = prepareNotificationBuilder(language.name());

                    notifyUserAboutProgress(notificationBuilder, notificationId, 10);

                    File unpackedFolder = unpackFileToFolder(downloadedFile);
                    notifyUserAboutProgress(notificationBuilder, notificationId, 40);

                    dbh.deleteLanguage(language);
                    notifyUserAboutProgress(notificationBuilder, notificationId, 50);

                    File innerFolder = new File(unpackedFolder.getPath() + "/OEBPS");
                    dbh.insertFilesFromFolder(innerFolder, language);
                    notifyUserAboutProgress(notificationBuilder, notificationId, 75);

//                  dbh.exportDb(); // use it for debug if you need
                    clean(unpackedFolder, downloadedFile);
                    notifyUserAboutProgress(notificationBuilder, notificationId, 90);

                    if (dbh.isBibleInDatabase(language)) {
                        saveChosenLanguagePreference(language);
                        notifyUserAboutFinish(notificationBuilder, notificationId, language.name(), true);
                    } else {
                        notifyUserAboutFinish(notificationBuilder, notificationId, language.name(), false);
                    }
                } else {
                    downloadedFile.delete(); // try to remove not needed downloaded file
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static File unpackFileToFolder(File downloadedFile) {
        String targetDirectoryPath = FileManagerActivity.fileWithoutExtension(downloadedFile.getPath());
        Log.v("InstallLanguageService", "Start unpacking File To Folder: " + targetDirectoryPath);
        File targetDirectory = new File(targetDirectoryPath);
        boolean result = targetDirectory.mkdirs();
        Log.v("InstallLanguageService", "mkdirs(): " + result);
        try {
            new ZipFile(downloadedFile).extractAll(targetDirectoryPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v("InstallLanguageService", "End of unpacking File To Folder");
        return targetDirectory;
    }

    // old method, can be removed some time after 11.07.2020
    // produces "java.util.zip.ZipException: only DEFLATED entries can have EXT descriptor" for new Romanian Bible
//    private static File unpackFileToFolder(File downloadedFile) {
//        Log.v("InstallLanguageService", "unpackFileToFolder start");
//        String targetDirectoryPath = FileManagerActivity.fileWithoutExtension(downloadedFile.getPath());
//        File targetDirectory = new File(targetDirectoryPath);
//        targetDirectory.mkdirs();
//        ZipInputStream zis;
//        try {
//            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(downloadedFile)));
//            ZipEntry ze;
//            int count;
//            byte[] buffer = new byte[8192];
//            while ((ze = zis.getNextEntry()) != null) {
//                File file = new File(targetDirectory, ze.getName());
//                File dir = ze.isDirectory() ? file : file.getParentFile();
//                if (!dir.isDirectory() && !dir.mkdirs())
//                    throw new FileNotFoundException("Failed to ensure directory: " +
//                            dir.getAbsolutePath());
//                if (ze.isDirectory())
//                    continue;
//                FileOutputStream fout = new FileOutputStream(file);
//                try {
//                    while ((count = zis.read(buffer)) != -1)
//                        fout.write(buffer, 0, count);
//                } finally {
//                    fout.close();
//                }
//            }
//            zis.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        Log.v("InstallLanguageService", "unpackFileToFolder end");
//        return targetDirectory;
//    }

    private void clean(File unpackedFolder, File downloadedFile) {
        downloadedFile.delete();
        deleteRecursive(unpackedFolder);
    }

    private NotificationCompat.Builder prepareNotificationBuilder(String bibleLanguage) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContentTitle(getString(R.string.install_language) + ": " + bibleLanguage)
                        .setContentText(getString(R.string.in_progress))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true);

        PendingIntent notifyPIntent =
                PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), 0);
        mBuilder.setContentIntent(notifyPIntent);

        return mBuilder;
    }

    private void notifyUserAboutProgress(NotificationCompat.Builder builder, int id, int progressInPercentage) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        builder.setProgress(100, progressInPercentage, false);
        mNotificationManager.notify(id, builder.build());
    }

    private void notifyUserAboutFinish(NotificationCompat.Builder builder, int id, String bibleLanguage, boolean wasSuccessful) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (wasSuccessful) {
            builder.setProgress(0, 0, false)
                    .setContentText(getString(R.string.finished));
        } else {
            builder.setProgress(0, 0, false)
                    .setContentText(getString(R.string.unsuccessful));
        }

        mNotificationManager.notify(id, builder.build());
    }

    private void saveChosenLanguagePreference(Language language) {
        // It's impossible to pass "return true" to onPreferenceChange() to save edited preference
        // so instead of this, that setting is saved manually
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(getString(R.string.verse_language_key), language.name());
        editor.apply();
    }
}
