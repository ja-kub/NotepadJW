package pl.bubson.notepadjw.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import pl.bubson.notepadjw.R;

/**
 * Created by Kuba on 2016-09-17.
 */
public class WhatsNewScreen {
    private static final String LOG_TAG                 = "WhatsNewScreen";
    private static final String LAST_VERSION_CODE_KEY   = "last_version_code";

    private Activity activityContext;

    public WhatsNewScreen(Activity context) {
        activityContext = context;
    }

    // Show the dialog only if not already shown for this version of the application
    public void show() {
        try {
            // Get the versionCode of the Package, which must be different (incremented) in each release on the Google Play in the AndroidManifest.xml
            final PackageInfo packageInfo = activityContext.getPackageManager().getPackageInfo(activityContext.getPackageName(), PackageManager.GET_ACTIVITIES);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
            final long lastVersionCode = prefs.getLong(LAST_VERSION_CODE_KEY, 0);

            if (packageInfo.versionCode > lastVersionCode) {
                Log.i(LOG_TAG, "versionCode " + packageInfo.versionCode + " is different from the last known version " + lastVersionCode);
                final String title = activityContext.getString(R.string.whats_new);
                String message = "";
                if (packageInfo.versionCode > (lastVersionCode + 1)) {
                    message = message + activityContext.getString(R.string.whats_new_message_previous);
                }
                message = message + activityContext.getString(R.string.whats_new_message_last);

                // Show the News since last version
                AlertDialog.Builder builder = new AlertDialog.Builder(activityContext)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {

                            public void onClick(DialogInterface dialogInterface, int i) {
                                // Mark this version as read
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putLong(LAST_VERSION_CODE_KEY, packageInfo.versionCode);
                                editor.commit();
                                dialogInterface.dismiss();
                            }
                        });
                builder.create().show();
            } else {
                Log.i(LOG_TAG, "versionCode " + packageInfo.versionCode + " is already known");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
