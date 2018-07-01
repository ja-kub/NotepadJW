package pl.bubson.notepadjw.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Locale;

import pl.bubson.notepadjw.R;

/**
 * Created by Kuba on 2018-07-01.
 */

public class LanguageUtils {
    private static final String TAG = "LanguageUtils";

    public static Language getCurrentVersesLanguage(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String langKey = context.getResources().getString(R.string.verse_language_key);
        String currentDeviceLanguage = Locale.getDefault().getLanguage();
        try {
            // if language wasn't previously set, try to set verse language as currentDeviceLanguage
            return Language.valueOf(sharedPref.getString(langKey, currentDeviceLanguage));
        } catch (IllegalArgumentException e) {
            // if currentDeviceLanguage is not on the list of available verse languages, set english
            Log.v(TAG, currentDeviceLanguage + " is not on the list of available verse languages, versesLanguage set to english");
            return Language.en;
        }
    }
}
