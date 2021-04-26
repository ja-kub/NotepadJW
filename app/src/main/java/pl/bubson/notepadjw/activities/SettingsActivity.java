package pl.bubson.notepadjw.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.databases.BiblesDatabase;
import pl.bubson.notepadjw.services.DownloadLanguageService;
import pl.bubson.notepadjw.utils.Language;
import pl.bubson.notepadjw.utils.Permissions;

public class SettingsActivity extends AppCompatActivity {
    static Language chosenLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            loadPreferences();
            checkIfSavedBibleIsAvailable(); // in case of restore settings after fresh installation (without downloaded Bibles)

            final ListPreference verseSizePreference = (ListPreference)findPreference(getString(R.string.verse_area_size_key));
            final ListPreference versePositionPreference = (ListPreference)findPreference(getString(R.string.verse_position_key));
            if(versePositionPreference.getValue().equals(getString(R.string.verse_position_top))) {
                verseSizePreference.setEnabled(true);
            } else {
                verseSizePreference.setEnabled(false);
            }
        }

        private void checkIfSavedBibleIsAvailable() {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            chosenLanguage = Language.valueOf(sharedPreferences.getString(getString(R.string.verse_language_key), getString(R.string.english_language)));
            downloadBibleIfNeeded();
        }

        @Override
        public void onResume() {
            super.onResume();
            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            // Unregister the listener whenever a key changes
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void loadPreferences() {
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Add preference change listener to language preference - to check if given language need to be downloaded first
            final Preference langPreference = findPreference(getString(R.string.verse_language_key));
            langPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    try {
                        chosenLanguage = Language.valueOf(newValue.toString());
                        if (downloadBibleIfNeeded()) return true; // setting will be saved
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false; // setting will not be saved
                }
            });

            final ListPreference versePositionPreference = (ListPreference)findPreference(getString(R.string.verse_position_key));
            final ListPreference verseSizePreference = (ListPreference)findPreference(getString(R.string.verse_area_size_key));
            versePositionPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    final String val = newValue.toString();
                    int index = versePositionPreference.findIndexOfValue(val);
                    if(index==0) {
                        verseSizePreference.setEnabled(true);
                    } else {
                        verseSizePreference.setEnabled(false);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString(getString(R.string.verse_area_size_key), getString(R.string.verse_area_size_auto));
                        editor.commit();
                    }
                    return true;
                }
            });
        }

        private boolean downloadBibleIfNeeded() {
            BiblesDatabase biblesDatabase = new BiblesDatabase(getActivity());
            if (biblesDatabase.isBibleInDatabase(chosenLanguage)) {
                return true;
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.missing_language_dialog_title);
                builder.setMessage(R.string.missing_language_message);

                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) FileManagerActivity.askForPermissionsIfNotGranted(getActivity()); // Bibles are now saved to External Files for which permission needed only on < KITKAT (Android 4.4)
                        Intent downloadServiceIntent = new Intent(getActivity(), DownloadLanguageService.class);
                        downloadServiceIntent.putExtra("Language",  chosenLanguage);
                        getActivity().startService(downloadServiceIntent);
                    }
                });

                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });

                builder.show();

                return false;
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Refresh language preference view - it's needed to see changes in current PreferenceFragment
            if (key.equals(getString(R.string.verse_language_key))) {
                Preference pref = findPreference(key);
                if (pref instanceof ListPreference) {
                    ListPreference listPreferenceDialog = (ListPreference) pref;
                    String savedLanguage = sharedPreferences.getString(key, getString(R.string.english_language));
                    // english as default value above can stay here, because there is no chance to use it:
                    // onSharedPreferenceChanged() is executed only when this shared preference is set
                    listPreferenceDialog.setValue(savedLanguage);
                    pref.setSummary(listPreferenceDialog.getEntry());
                }
            }
        }
    }

    // Used when user selects Bible to download and accept permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.MY_REQUEST_PERMISSIONS_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent downloadServiceIntent = new Intent(this, DownloadLanguageService.class);
                    downloadServiceIntent.putExtra("Language",  chosenLanguage);
                    this.startService(downloadServiceIntent);
                } else {
                        Toast.makeText(this, R.string.permission_storage_not_granted, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
