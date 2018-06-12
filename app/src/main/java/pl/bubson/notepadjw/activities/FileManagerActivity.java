package pl.bubson.notepadjw.activities;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionsMenu;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.databases.FilesDatabase;
import pl.bubson.notepadjw.fileManagerHelpers.FileListAdapter;
import pl.bubson.notepadjw.fileManagerHelpers.Item;
import pl.bubson.notepadjw.services.InstallLanguageService;
import pl.bubson.notepadjw.utils.Language;
import pl.bubson.notepadjw.utils.Permissions;
import pl.bubson.notepadjw.utils.SpanToHtmlConverter;
import pl.bubson.notepadjw.utils.WhatsNewScreen;

public class FileManagerActivity extends AppCompatActivity {

    public static final String NOTE_FILE_EXTENSION = "html";
    public static final int SUCCESSFUL = 1;
    public static final int FAILED = 0;
    public static final String MOVED_FILES_KEY = "movedFiles";
    private static final String TAG = "FileManagerActivity";
    private static final String appFolderName = "NotepadJW"; // don't change it, as user have their notes there from some time
    private static File mainDirectory;
    private static FilesDatabase filesDatabase;
    private final Context activityContext = this;
    FileListAdapter adapter;
    private MenuItem removeFiles, shareFiles, renameFile, cutFiles, copyFiles, pasteFiles, sortFilesMenuItem, helpMenuItem, settingsMenuItem, searchMenuItem;
    private SearchView searchView;
    private File currentDirectory;
    private File[] currentFilesAndDirectories;
    private List<Item> selectedItemList = new ArrayList<>();
    private List<Item> clipboardItemList = new ArrayList<Item>();
    private boolean isClipboardToCopy, isCurrentSortingByDate, isSearchMenuExpanded, isSearchResultsListed;
    private RecyclerView recyclerView;
    private SharedPreferences sharedPref;
    private FloatingActionButton floatingActionButton;
    private FloatingActionsMenu famCreateNew;
    private com.getbase.floatingactionbutton.FloatingActionButton fabNewFolder, fabNewNote;

    public static FilesDatabase getFilesDatabase() {
        return filesDatabase;
    }

    public static String fileExtension(String name) {
        if (name == null || name.equals("")) {
            return "";
        }
        String suffix = "";
        int index = name.lastIndexOf(".");
        if (index != -1) {
            suffix = name.substring(index + 1);
        }
        return suffix;
    }

    public static String fileWithoutExtension(String name) {
        if (name == null || name.equals("")) {
            return "";
        }
        String prefix = name;
        int index = name.lastIndexOf(".");
        if (index != -1) {
            prefix = name.substring(0, index);
        }
        return prefix;
    }

    public static File getMainDirectory() {
        return mainDirectory;
    }

    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        return fileOrDirectory.delete();
    }

    public static boolean isStoragePermissionGranted(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return (permission == PackageManager.PERMISSION_GRANTED);
    }

    public static void askForPermissionsIfNotGranted(final Activity activity) {
        if (!isStoragePermissionGranted(activity)) {
            ActivityCompat.requestPermissions(activity, Permissions.PERMISSIONS, Permissions.MY_REQUEST_PERMISSIONS_CODE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preparePreferences();
        prepareViewAndToolbar();
        prepareBiblesDatabase(); // with preload of example verse

        askForPermissionsIfNotYetAnswered(this);
        copyFilesIfNecessary(); // Remove this method some while after version 38 (released 06.06.2018), maybe a year after?
        prepareMainDirectory();
        prepareFilesDatabase(mainDirectory); // to be able to search them

        // Show the "What's New" screen once for each new release of the application
        new WhatsNewScreen(this).show();
    }

    private void prepareSearchView() {
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                buttonsOnStartSearch();
                isSearchMenuExpanded = true;
                searchView.requestFocus();
                return true;  // Return true to expand action view
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                buttonsOnExitSearch();
                isSearchMenuExpanded = false;
                searchView.clearFocus();
                if (isSearchResultsListed) fillListWithItemsFromDir(currentDirectory);
                return true;  // Return true to collapse action view
            }
        });
    }

    private void prepareFilesDatabase(File directory) {
        filesDatabase = new FilesDatabase(this, directory);
        filesDatabase.refreshData(); // to be ready to search
        // it has to be refresh(), not touch(), in case of user in the meantime modified notes form PC
    }

    private File[] searchFiles(String query) {
        List<File> files = new ArrayList<>();
        try {
            Cursor c = filesDatabase.getWordMatches(query, null);
            if (c != null) {
                int fileColumnIndex = c.getColumnIndex(FilesDatabase.COL_FILE_PATH);
                do {
                    String filePath = c.getString(fileColumnIndex);
                    files.add(new File(filePath));
                } while (c.moveToNext());
                c.close();
            } else {
                Log.v(TAG, "cursor is null!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files.toArray(new File[0]);
    }

    private void prepareBiblesDatabase() {
        Intent installLanguageServiceIntent = new Intent(this, InstallLanguageService.class);
        startService(installLanguageServiceIntent); // Intent without data will pre-install db if needed
    }

    private void prepareViewAndToolbar() {
        setContentView(R.layout.activity_file_manager);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        famCreateNew = (FloatingActionsMenu) findViewById(R.id.fam_create_new);
        fabNewFolder = (com.getbase.floatingactionbutton.FloatingActionButton) findViewById(R.id.fab_new_folder);
        if (fabNewFolder != null) fabNewFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                famCreateNew.collapse();
                createNewFolder();
            }
        });
        fabNewNote = (com.getbase.floatingactionbutton.FloatingActionButton) findViewById(R.id.fab_new_note);
        if (fabNewNote != null) fabNewNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                famCreateNew.collapse();
                createNewFile();
            }
        });
    }

    private void preparePreferences() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        isCurrentSortingByDate = sharedPref.getBoolean(getString(R.string.sort_by_date_key), false);
        String langKey = getResources().getString(R.string.verse_language_key);
        String savedVerseLanguage = sharedPref.getString(langKey, "empty");
        if (savedVerseLanguage.equals("empty")) {
            String currentDeviceLanguage = Locale.getDefault().getLanguage();
            Language versesLanguage;
            if (currentDeviceLanguage.equals("pl")) {
                versesLanguage = Language.pl;
            } else {
                versesLanguage = Language.en;
            }
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(getString(R.string.verse_language_key), versesLanguage.name());
            editor.apply();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSearchMenuExpanded)
            fillListWithItemsFromDir(currentDirectory); // this is also to reload file bytes after back from editor
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_file_manager, menu);
        removeFiles = menu.findItem(R.id.action_remove);
        shareFiles = menu.findItem(R.id.action_share);
        renameFile = menu.findItem(R.id.action_rename);
        cutFiles = menu.findItem(R.id.action_cut);
        copyFiles = menu.findItem(R.id.action_copy);
        pasteFiles = menu.findItem(R.id.action_paste);
        sortFilesMenuItem = menu.findItem(R.id.action_sort);
        helpMenuItem = menu.findItem(R.id.action_help);
        settingsMenuItem = menu.findItem(R.id.action_settings);
        searchMenuItem = menu.findItem(R.id.search);
        searchView = (SearchView) searchMenuItem.getActionView();
        prepareSearchView();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_rename:
                renameCurrentlySelectedFile();
                return true;
            case R.id.action_cut:
                cutCurrentlySelectedFiles();
                return true;
            case R.id.action_copy:
                copyCurrentlySelectedFiles();
                return true;
            case R.id.action_paste:
                pasteFilesFromClipboard();
                return true;
            case R.id.action_remove:
                removeCurrentlySelectedFiles();
                return true;
            case R.id.action_share:
                shareCurrentlySelectedFiles();
                return true;
            case R.id.action_sort:
                changeSorting();
                return true;
            case R.id.action_help:
                Intent intentHelp = new Intent(this, HelpActivity.class);
                startActivity(intentHelp);
                return true;
            case R.id.action_settings:
                Intent intentSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentSettings);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        switch (selectedItemList.size()) {
            case 0:
                if (isSearchMenuExpanded) {
                    searchMenuItem.expandActionView();
                    buttonsOnStartSearch();
                    searchView.clearFocus();
                } else {
                    buttonsOnExitSearch();
                }
                break;
            case 1:
                sortFilesMenuItem.setVisible(false);
                searchMenuItem.setVisible(false);
                renameFile.setVisible(true);
                removeFiles.setVisible(true);
                shareFiles.setVisible(true);
                cutFiles.setVisible(true);
                copyFiles.setVisible(true);
                pasteFiles.setVisible(false);
                break;
            default:
                sortFilesMenuItem.setVisible(false);
                searchMenuItem.setVisible(false);
                renameFile.setVisible(false);
                removeFiles.setVisible(true);
                shareFiles.setVisible(true);
                cutFiles.setVisible(true);
                copyFiles.setVisible(true);
                pasteFiles.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!selectedItemList.isEmpty()) {
                deselectAllItems();
            } else {
                if (isSearchMenuExpanded) {
                    searchMenuItem.collapseActionView();
                } else {
                    moveUpOneLevel();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            searchView.setQuery(query, false); // because query is not visible after voice search
            doSearch(query);
        }
    }

    private void buttonsOnStartSearch() {
        famCreateNew.setVisibility(View.INVISIBLE);
        sortFilesMenuItem.setVisible(false);
        renameFile.setVisible(false);
        removeFiles.setVisible(false);
        shareFiles.setVisible(false);
        cutFiles.setVisible(false);
        copyFiles.setVisible(false);
        pasteFiles.setVisible(false);
        settingsMenuItem.setVisible(false);
        helpMenuItem.setVisible(false);
    }

    private void buttonsOnExitSearch() {
        famCreateNew.setVisibility(View.VISIBLE);
        sortFilesMenuItem.setVisible(true);
        searchMenuItem.setVisible(true);
        renameFile.setVisible(false);
        removeFiles.setVisible(false);
        shareFiles.setVisible(false);
        cutFiles.setVisible(false);
        copyFiles.setVisible(false);
        pasteFiles.setVisible(!clipboardItemList.isEmpty() && !isSearchResultsListed);
        settingsMenuItem.setVisible(true);
        helpMenuItem.setVisible(true);
    }

    private void doSearch(String query) {
        List<Item> items = getItems(searchFiles(query));
        List<Item> filteredItems = new ArrayList<>();
        for (Item item : items) {
            if (item.getPath().startsWith(currentDirectory.getPath())) filteredItems.add(item);
        }
        sortItems(filteredItems, true); // sorting by date descending
        prepareFileListAdapter(filteredItems);
        isSearchResultsListed = true;
    }

    // Checks if external storage is available for read and write
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void prepareMainDirectory() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
        int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
        int filesMoved = prefs.getInt(MOVED_FILES_KEY, SUCCESSFUL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && filesMoved == SUCCESSFUL) { // AutoBackup works from Android 6.0...
            mainDirectory = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), appFolderName); // ...and it works only for files in getExternalFilesDir() and getFilesDir(). But that way these files are not accessible from PC
        } else if (isExternalStorageWritable() && isStoragePermissionGranted(this) && userAnswer == Permissions.ACCEPTED) { // for Androids < 6.0 let's stay with old version with access to files from PC
            File publicDirectory;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            } else {
                publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }
            mainDirectory = new File(publicDirectory, appFolderName);
        } else if (!isStoragePermissionGranted(this) && userAnswer == Permissions.ACCEPTED) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
            editor.commit();
            askForPermissionsIfNotYetAnswered(this);
            mainDirectory = new File(getFilesDir(), appFolderName);
        } else {
            mainDirectory = new File(getFilesDir(), appFolderName);
        }
        Log.i(TAG, "mainDirectory = " + mainDirectory);

        if (mainDirectory.mkdirs() || mainDirectory.isDirectory()) {
            currentDirectory = mainDirectory;
        } else {
            Toast.makeText(this, R.string.storage_not_writable, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void fillListWithItemsFromDir(File directory) {
        if ((directory != null) && (directory.mkdirs() || directory.isDirectory())) {
            selectedItemList.clear();
            invalidateOptionsMenu();
            this.currentDirectory = directory;
            currentFilesAndDirectories = directory.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return (pathname.isDirectory()
                            || fileExtension(pathname.getName()).equalsIgnoreCase(NOTE_FILE_EXTENSION));
                }
            });
            setTitle(directory.getName());

            List<Item> items = getItems(currentFilesAndDirectories);
            prepareFileListAdapter(items);
            isSearchResultsListed = false;
        } else {
            Toast.makeText(this, R.string.current_dir_is_null, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private List<Item> getItems(File[] filesAndDirs) {
        List<Item> directories = new ArrayList<>();
        List<Item> files = new ArrayList<>();

        try {
            for (File fileOrDir : filesAndDirs) {
                Date lastModDate = new Date(fileOrDir.lastModified());
                if (fileOrDir.isDirectory()) {
                    File[] fbuf = fileOrDir.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return (pathname.isDirectory()
                                    || fileExtension(pathname.getName()).equalsIgnoreCase(NOTE_FILE_EXTENSION));
                        }
                    });
                    int buf = 0;
                    if (fbuf != null) {
                        buf = fbuf.length;
                    }
                    String numberOfItems = activityContext.getResources().getString(R.string.items) + ": " + String.valueOf(buf);
                    directories.add(new Item(fileOrDir.getName(), numberOfItems, lastModDate,
                            fileOrDir.getAbsolutePath(), Item.Type.DIRECTORY));
                } else {
                    String numberOfBytes = activityContext.getResources().getString(R.string.bytes) + ": " + fileOrDir.length();
                    files.add(new Item(fileWithoutExtension(fileOrDir.getName()), numberOfBytes, lastModDate,
                            fileOrDir.getAbsolutePath(), Item.Type.FILE));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(directories);
        sortItems(files, isCurrentSortingByDate);
        directories.addAll(files);

        if (!currentDirectory.getName().equalsIgnoreCase(mainDirectory.getName())) {
            directories.add(0, new Item("..", activityContext.getResources().getString(R.string.parent_directory), null, currentDirectory.getParent(), Item.Type.UP));
        }
        return directories;
    }

    private void prepareFileListAdapter(List<Item> items) {
        adapter = new FileListAdapter(FileManagerActivity.this, items);
        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        if (mRecyclerView != null) {
            try {
                mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                mRecyclerView.setAdapter(adapter);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), R.string.unexpected_exception, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sortItems(List<Item> files, boolean sortByDate) {
        if (sortByDate) {
            Collections.sort(files, new Comparator<Item>() {
                public int compare(Item o1, Item o2) {
                    if (o1.getDate() == null || o2.getDate() == null)
                        return 0;
//                    return o1.getDate().compareTo(o2.getDate()); // ascending sorting
                    return o2.getDate().compareTo(o1.getDate()); // descending sorting
                }
            });
        } else {
            Collections.sort(files); // default sorting - by file names ascending
        }
    }

    public void openFile(File file) {
        Intent intent = new Intent(this, NotepadEditorActivity.class);
        if (file.length() > 0) {
            intent.setAction(Intent.ACTION_VIEW);
        } else {
            intent.setAction(Intent.ACTION_EDIT);
        }
        Uri fileUri = Uri.fromFile(file);
        if (fileUri != null) {
            intent.setDataAndType(fileUri, "text/plain");
            startActivity(intent);
        } else {
            intent.setDataAndType(null, "");
            Toast.makeText(this, R.string.uri_null, Toast.LENGTH_SHORT).show();
        }
    }

    public void selectItem(Item item) {
        selectedItemList.add(item);
        invalidateOptionsMenu();
    }

    public void deselectItem(Item item) {
        selectedItemList.remove(item);
        invalidateOptionsMenu();
    }

    public void deselectAllItems() {
        adapter.deselectAllItems();
        selectedItemList.clear();
        invalidateOptionsMenu();
    }

    private void createNewFile() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_new_note_dialog_title);

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.requestFocus();
        input.setHint(R.string.note_name);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean isFileNameCorrect = true;
                String newFileName = input.getText().toString();
                if (newFileName.equals("")) {
                    isFileNameCorrect = false;
                    Toast.makeText(activityContext, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                } else {
                    for (File file : currentFilesAndDirectories) {
                        if (fileWithoutExtension(file.getName()).equalsIgnoreCase(newFileName)) {
                            isFileNameCorrect = false;
                            Toast.makeText(activityContext, R.string.file_name_exists, Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                if (isFileNameCorrect) {
                    File file = new File(currentDirectory, newFileName + "." + NOTE_FILE_EXTENSION);
                    try {
                        file.createNewFile();
                        filesDatabase.addFileOrDir(file);
                        MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath()}, null, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(activityContext, R.string.creation_failed, Toast.LENGTH_SHORT).show();
                    }
                    fillListWithItemsFromDir(currentDirectory);
                    openFile(file);
                    dialog.dismiss();
                }
            }

        });

    }

    private void createNewFolder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_new_folder_dialog_title);

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.requestFocus();
        input.setHint(R.string.folder_name);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean isDirectoryNameCorrect = true;
                String newDirectoryName = input.getText().toString();
                if (newDirectoryName.equals("")) {
                    isDirectoryNameCorrect = false;
                    Toast.makeText(activityContext, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                } else {
                    for (File file : currentFilesAndDirectories) {
                        if (file.getName().equalsIgnoreCase(newDirectoryName)) {
                            isDirectoryNameCorrect = false;
                            Toast.makeText(activityContext, R.string.folder_name_exists, Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                if (isDirectoryNameCorrect) {
                    File directory = new File(currentDirectory, newDirectoryName);
                    if (directory.mkdir()) {
                        filesDatabase.addFileOrDir(directory);
                        Snackbar.make(recyclerView, R.string.creation_of_new_folder_succesful, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    } else {
                        Toast.makeText(activityContext, R.string.creation_failed, Toast.LENGTH_SHORT).show();
                    }
                    fillListWithItemsFromDir(currentDirectory);
                    dialog.dismiss();
                }
            }
        });
    }

    private void renameCurrentlySelectedFile() {
        if (selectedItemList.size() == 1) {
            final File file = new File(selectedItemList.get(0).getPath());

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.rename_file);

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
//            input.setText(fileWithoutExtension(selectedItemList.get(0).getName()));
            input.setText(selectedItemList.get(0).getName());
            input.setSelectAllOnFocus(true);
            input.requestFocus();
            builder.setView(input);

            builder.setPositiveButton(R.string.ok, null);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            });

            final AlertDialog dialog = builder.create();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Boolean isFileNameCorrect = true;
                    String newFileName;
                    File fileDirectory = file.getParentFile();
                    if (file.isDirectory()) {
                        newFileName = input.getText().toString();
                    } else {
                        newFileName = input.getText().toString() + "." + NOTE_FILE_EXTENSION;
                    }
                    if (input.getText().toString().equals("")) {
                        isFileNameCorrect = false;
                        Toast.makeText(activityContext, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    } else {
                        for (File file : fileDirectory.listFiles()) {
                            if (file.getName().equalsIgnoreCase(newFileName)) {
                                isFileNameCorrect = false;
                                Toast.makeText(activityContext, R.string.file_name_exists, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    if (isFileNameCorrect) {
                        File newFile = new File(fileDirectory, newFileName);
                        if (file.renameTo(newFile)) {
                            Toast.makeText(activityContext, R.string.file_renamed, Toast.LENGTH_SHORT).show();
                            if (newFile.isDirectory()) {
                                if ((newFile.listFiles().length > 0)) {
                                    MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath(), newFile.listFiles()[0].getAbsolutePath()}, null, null);
                                }
                            } else {
                                MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath(), newFile.getAbsolutePath()}, null, null);
                            }
                            filesDatabase.renameFileOrDir(file, newFile);
                            fillListWithItemsFromDir(currentDirectory);
                        } else {
                            Toast.makeText(activityContext, R.string.file_rename_failed, Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }
                }

            });
        } else {
            Toast.makeText(activityContext, R.string.not_one_file_selected, Toast.LENGTH_SHORT).show();
        }
    }

    private void cutCurrentlySelectedFiles() {
        clipboardItemList = new ArrayList<>(selectedItemList);
        isClipboardToCopy = false;
        deselectAllItems();
        Snackbar.make(recyclerView, R.string.cut_to_clipboard, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show();
    }

    private void copyCurrentlySelectedFiles() {
        clipboardItemList = new ArrayList<>(selectedItemList);
        isClipboardToCopy = true;
        deselectAllItems();
        Snackbar.make(recyclerView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show();
    }

    private void pasteFilesFromClipboard() {
        if (clipboardItemList.size() > 0) {
            try {
                for (Item item : clipboardItemList) {
                    File file = new File(item.getPath());
                    if (isClipboardToCopy) {
                        if (file.isDirectory()) {
                            FileUtils.copyDirectoryToDirectory(file, currentDirectory);
                        } else {
                            FileUtils.copyFileToDirectory(file, currentDirectory);
                        }
                    } else {
                        FileUtils.moveToDirectory(file, currentDirectory, true);
                    }
                    MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath()}, null, null);
                }
                Snackbar.make(recyclerView, R.string.pasted_from_clipboard, Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
            } catch (Exception e) {
                e.printStackTrace();
                Snackbar.make(recyclerView, R.string.not_all_elements_pasted, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        }
        clipboardItemList.clear();
        filesDatabase.refreshData();
        fillListWithItemsFromDir(currentDirectory);
    }

    private void removeCurrentlySelectedFiles() {
        if (selectedItemList.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.on_remove_dialog_title);
            builder.setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User confirmed removing
                    boolean removedSuccessfully = true;
                    for (Item item : selectedItemList) {
                        File file = new File(item.getPath());
                        if (deleteRecursive(file)) {
                            MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath()}, null, null);
                        } else {
                            removedSuccessfully = false;
                        }
                    }
                    if (removedSuccessfully) {
                        Snackbar.make(recyclerView, R.string.files_removed_successfully, Snackbar.LENGTH_SHORT)
                                .setAction("Action", null).show();
                    } else {
                        Snackbar.make(recyclerView, R.string.files_not_removed_successfully, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                    filesDatabase.refreshData();
                    fillListWithItemsFromDir(currentDirectory);
                }
            });
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void shareCurrentlySelectedFiles() {
        if (selectedItemList.size() > 0) {
            // Preparing data for intents
            ArrayList<Uri> fileUris = getUris();
            String text = readHtmlFiles(fileUris);
            Intent mainIntent;

            // Android < 6.0.1 does not support chooser with no options, so we start with creating chooser with only one option - gmail, which should be available on all Android devices; and then we will add more options
            Intent gmailIntent = new Intent();
            gmailIntent = prepareSendFilesIntent(gmailIntent, fileUris, true);
            gmailIntent.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmailExternal");
            mainIntent = Intent.createChooser(gmailIntent, getString(R.string.button_share));

            // Creating list of all apps which can support ACTION_SEND with text/plain
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            PackageManager pm = getPackageManager();
            List<ResolveInfo> resInfo = pm.queryIntentActivities(sendIntent, 0);
            List<LabeledIntent> intentList = new ArrayList<>();

            // Adding only selected apps to chooser
            for (ResolveInfo ri : resInfo) {
                String packageName = ri.activityInfo.packageName;
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
                if (packageName.contains("whatsapp")) { // WhatsApp
                    intent = prepareSendFilesIntent(intent, fileUris, false); // WhatsApp cannot consume both EXTRA_STREAM and EXTRA_TEXT, so we create Intent without EXTRA_TEXT
                    intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                } else if (packageName.contains("android.email") || (packageName.contains("android.apps.docs") && ri.activityInfo.name.contains("UploadMenuActivity"))) { // Native Email, Google Drive
                    intent = prepareSendFilesIntent(intent, fileUris, true);
                    intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                } else if (packageName.contains("mms") || packageName.contains("messaging") || packageName.contains("facebook.orca")) { // SMS, MMS and Messenger - apps which cannot send files, only text
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                }
            }
            LabeledIntent[] extraIntents = intentList.toArray(new LabeledIntent[intentList.size()]);
            mainIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents); // add list of selected apps to chooser

            // Verify that the intent will resolve to an activity
            if (mainIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mainIntent);
            }
        }
    }

    @NonNull
    private ArrayList<Uri> getUris() {
        ArrayList<Uri> fileUris = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (Item item : selectedItemList) {
            files.add(new File(item.getPath()));
        }
        files = getFilesFromFolders(files);
        for (File file : files) {
            fileUris.add(getUri(file));
        }
        return fileUris;
    }

    private List<File> getFilesFromFolders(List<File> fileList) {
        List<File> newFileList = new ArrayList<>(fileList);
        try {
            for (File fileOrDir : fileList) {
                if (fileOrDir.isDirectory()) {
                    List<File> childFiles = new ArrayList<>(Arrays.asList(fileOrDir.listFiles()));
                    newFileList.remove(fileOrDir);
                    newFileList.addAll(getFilesFromFolders(childFiles));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newFileList;
    }

    private Intent prepareSendFilesIntent(Intent intent, ArrayList<Uri> fileUris, boolean addExtraText) {
        intent.setType("text/html");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (selectedItemList.size() == 1)
            intent.putExtra(Intent.EXTRA_SUBJECT, selectedItemList.get(0).getName());
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
        if (addExtraText)
            intent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(getString(R.string.sent_by_notepadjw)));
        return intent;
    }

    String readHtmlFiles(List<Uri> uriList) {
        StringBuilder wholeText = new StringBuilder();
        try {
            for (Uri uri : uriList) {
                StringBuilder fileText = new StringBuilder();

                InputStream is = getContentResolver().openInputStream(uri);
                int maxFileSizeInBytes = 1000000;
                if ((is != null) && is.available() <= maxFileSizeInBytes) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    String line;
                    String lineEnding = "";
                    while ((line = reader.readLine()) != null) {
                        fileText.append(lineEnding).append(line);
                        lineEnding = "<BR>";
                    }
                    is.close();
                } else {
                    Toast.makeText(this, R.string.file_is_too_big, Toast.LENGTH_LONG).show();
                }
                wholeText.append(fileWithoutExtension(uri.getLastPathSegment())).append(": \n");
                wholeText.append(SpanToHtmlConverter.fromHtml(fileText.toString())).append("\n\n");
            }
        } catch (Exception exception) {
            if (exception.getCause().toString().contains("Permission denied")) {
                Log.v(TAG, exception.getCause().toString());
                FileManagerActivity.askForPermissionsIfNotGranted(this);
            } else {
                exception.printStackTrace();
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show();
            }
        }
        String content = wholeText.append(SpanToHtmlConverter.fromHtml(getString(R.string.sent_by_notepadjw))).toString();
        Log.d(TAG, "openFileFromIntent - content:\n" + content);
        return content;
    }

    private Uri getUri(File file) {
        try {
            return FileProvider.getUriForFile(activityContext, "pl.bubson.notepadjw.fileprovider", file);
        } catch (IllegalArgumentException e) {
            Log.e("File Selector", "The selected file can't be shared: " + file.getName());
        }
        return null;
    }

    // Remove this method some while after version 38 (from 06.06.2018), maybe a year after?
    public void copyFilesIfNecessary() {
        final long lastVersionCode = PreferenceManager.getDefaultSharedPreferences(activityContext).getLong(WhatsNewScreen.LAST_VERSION_CODE_KEY, 0);
        if (lastVersionCode > 0 && lastVersionCode < 38 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // do it only once, with first update to version => 38 and only for Androids >= 6.0
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
            int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(MOVED_FILES_KEY, SUCCESSFUL);
            editor.commit();
            Log.i(TAG, "Conditions met, moving files.");
            File fromDir;
            if (isExternalStorageWritable() && isStoragePermissionGranted(this) && userAnswer == Permissions.ACCEPTED) {
                fromDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), appFolderName);
            } else {
                fromDir = new File(getFilesDir(), appFolderName);
            }
            if (fromDir.isDirectory()) {
                try {
                    FileUtils.moveToDirectory(fromDir, new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).getPath()), true);
                } catch (Exception e) {
                    e.printStackTrace();
                    editor.putInt(MOVED_FILES_KEY, FAILED);
                    editor.commit();
                }
            }
        } else {
            Log.i(TAG, "Conditions ARE NOT met, moving files skipped.");
        }
    }

    private void moveUpOneLevel() {
        if (!currentDirectory.getName().equalsIgnoreCase(mainDirectory.getName())) {
            fillListWithItemsFromDir(new File(currentDirectory.getParent()));
        } else {
            finish();
        }
    }

    private void changeSorting() {
        isCurrentSortingByDate = !isCurrentSortingByDate;
        sharedPref.edit().putBoolean(getString(R.string.sort_by_date_key), isCurrentSortingByDate).apply();
        fillListWithItemsFromDir(currentDirectory);
        if (isCurrentSortingByDate) {
            Snackbar.make(recyclerView, R.string.files_sorted_by_date, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
        } else {
            Snackbar.make(recyclerView, R.string.files_sorted_alphabetically, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
        }
    }

    public void askForPermissionsIfNotYetAnswered(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // permissions are not needed in this app from Android 6.0
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
            if (userAnswer == Permissions.NOT_ANSWERED_YET && !isStoragePermissionGranted(this)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage(R.string.permission_explanation_dialog_message)
                        .setCancelable(false)
                        .setTitle(R.string.permission_explanation_dialog_title)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                ActivityCompat.requestPermissions(
                                        activity,
                                        Permissions.PERMISSIONS,
                                        Permissions.MY_REQUEST_PERMISSIONS_CODE
                                );
                            }
                        });
                builder.create().show();
            } else if (userAnswer == Permissions.NOT_ANSWERED_YET && isStoragePermissionGranted(this)) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.ACCEPTED);
                editor.commit();
            }
        }
    }

    // Executed in two cases:
    // 1. Sharing files when permissions were not yet granted
    // 2. When user selected Access/Deny on Permissions dialog after installation
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.MY_REQUEST_PERMISSIONS_CODE: {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
                int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
                SharedPreferences.Editor editor = prefs.edit();
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (userAnswer == Permissions.NOT_ANSWERED_YET) {
                        editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.ACCEPTED);
                        editor.commit();
                        prepareMainDirectory();
                    }
                } else {
                    Toast.makeText(this, R.string.permission_storage_not_granted, Toast.LENGTH_LONG).show();
                    if (userAnswer == Permissions.NOT_ANSWERED_YET) {
                        editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.DENIED);
                        editor.commit();
                        prepareMainDirectory();
                    }
                }
                prepareFilesDatabase(mainDirectory);
            }
        }
    }

}
