package pl.bubson.notepadjw.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.fileManagerHelpers.FileListAdapter;
import pl.bubson.notepadjw.fileManagerHelpers.Item;
import pl.bubson.notepadjw.services.InstallLanguageService;
import pl.bubson.notepadjw.utils.Language;
import pl.bubson.notepadjw.utils.Permissions;
import pl.bubson.notepadjw.utils.WhatsNewScreen;

public class FileManagerActivity extends AppCompatActivity {

    public static final String NOTE_FILE_EXTENSION = "html";
    public static final String OLD_FILE_EXTENSION = "txt";
    private static final String appFolderName = "NotepadJW"; // don't change it, as user have their notes there from some time
    private static File mainDirectory;
    private final Context activityContext = this;
    FileListAdapter adapter;
    private MenuItem newFolder, removeFiles, shareFiles, renameFile, cutFiles, copyFiles, pasteFiles, sortFilesMenuItem;
    private File currentDirectory;
    private File[] currentFilesAndDirectories;
    private List<Item> selectedItemList = new ArrayList<>();
    private List<Item> clipboardItemList = new ArrayList<Item>();
    private boolean isClipboardToCopy, isCurrentSortingByDate;
    private RecyclerView recyclerView;
    private SharedPreferences sharedPref;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preparePreferences();
        prepareViewAndToolbar();

        askForPermissionsIfNotYetAnswered(this);
        prepareMainDirectoryAndFillList();
        prepareDatabase(); // with preload example verse
//        prepareSearchTable(); // TODO

        // Show the "What's New" screen once for each new release of the application
        new WhatsNewScreen(this).show();
    }

    private void prepareDatabase() {
        Intent installLanguageServiceIntent = new Intent(this, InstallLanguageService.class);
        startService(installLanguageServiceIntent); // Intent without data will pre-install db if needed
    }

    private void prepareViewAndToolbar() {
        setContentView(R.layout.activity_file_manager);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    createNewFile();
                }
            });
        }
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
        fillListWithItemsFromDir(currentDirectory); // this is also to reload file bytes after back from editor
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_file_manager, menu);
        newFolder = menu.findItem(R.id.action_new_folder);
        removeFiles = menu.findItem(R.id.action_remove);
        shareFiles = menu.findItem(R.id.action_share);
        renameFile = menu.findItem(R.id.action_rename);
        cutFiles = menu.findItem(R.id.action_cut);
        copyFiles = menu.findItem(R.id.action_copy);
        pasteFiles = menu.findItem(R.id.action_paste);
        sortFilesMenuItem = menu.findItem(R.id.action_sort);
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
            case R.id.action_new_folder:
                createNewFolder();
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
                newFolder.setVisible(true);
                sortFilesMenuItem.setVisible(true);
                renameFile.setVisible(false);
                removeFiles.setVisible(false);
                shareFiles.setVisible(false);
                cutFiles.setVisible(false);
                copyFiles.setVisible(false);
                pasteFiles.setVisible(!clipboardItemList.isEmpty());
                break;
            case 1:
                newFolder.setVisible(false);
                sortFilesMenuItem.setVisible(false);
                renameFile.setVisible(true);
                removeFiles.setVisible(true);
                shareFiles.setVisible(true);
                cutFiles.setVisible(true);
                copyFiles.setVisible(true);
                pasteFiles.setVisible(false);
                break;
            default:
                newFolder.setVisible(false);
                sortFilesMenuItem.setVisible(false);
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
            moveUpOneLevel();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Checks if external storage is available for read and write
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void prepareMainDirectoryAndFillList() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
        int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
        if (isExternalStorageWritable() && isStoragePermissionGranted(this) && userAnswer==Permissions.ACCEPTED) {
            File publicDirectory;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            } else {
                publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }
            mainDirectory = new File(publicDirectory, appFolderName);
        } else if (!isStoragePermissionGranted(this) && userAnswer==Permissions.ACCEPTED) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
            editor.commit();
            askForPermissionsIfNotYetAnswered(this);
            mainDirectory = new File(getFilesDir(), appFolderName);
        } else {
            mainDirectory = new File(getFilesDir(), appFolderName);
        }

        if (mainDirectory.mkdirs() || mainDirectory.isDirectory()) {
            currentDirectory = mainDirectory;
        } else {
            Toast.makeText(this, R.string.storage_not_writable, Toast.LENGTH_SHORT).show();
            finish();
        }
        Log.v("Renaming", "rename start");
        updateFilesExtensionsRecursive(mainDirectory);
        Log.v("Renaming", "rename end");
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
                            || fileExtension(pathname.getName()).equalsIgnoreCase(NOTE_FILE_EXTENSION)
                            || fileExtension(pathname.getName()).equalsIgnoreCase(OLD_FILE_EXTENSION));
                }
            });
            setTitle(directory.getName());

            List<Item> items = getItems(currentFilesAndDirectories);

            prepareFileListAdapter(items);
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
                                    || fileExtension(pathname.getName()).equalsIgnoreCase(NOTE_FILE_EXTENSION)
                                    || fileExtension(pathname.getName()).equalsIgnoreCase(OLD_FILE_EXTENSION));
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
        sortFiles(files);
        directories.addAll(files);

        if (!currentDirectory.getName().equalsIgnoreCase(mainDirectory.getName())) {
            directories.add(0, new Item("..", activityContext.getResources().getString(R.string.parent_directory), null, currentDirectory.getParent(), Item.Type.UP));
        }
        return directories;
    }

    private void prepareFileListAdapter(List<Item> directories) {
        adapter = new FileListAdapter(FileManagerActivity.this, directories);
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

    private void sortFiles(List<Item> files) {
        if (isCurrentSortingByDate) {
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
                    if (file.isDirectory()) {
                        newFileName = input.getText().toString();
                    } else {
                        newFileName = input.getText().toString() + "." + NOTE_FILE_EXTENSION;
                    }
                    if (input.getText().toString().equals("")) {
                        isFileNameCorrect = false;
                        Toast.makeText(activityContext, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    } else {
                        for (File file : currentFilesAndDirectories) {
                            if (file.getName().equalsIgnoreCase(newFileName)) {
                                isFileNameCorrect = false;
                                Toast.makeText(activityContext, R.string.file_name_exists, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    if (isFileNameCorrect) {
                        File newFile = new File(currentDirectory, newFileName);
                        if (file.renameTo(newFile)) {
                            Toast.makeText(activityContext, R.string.file_renamed, Toast.LENGTH_SHORT).show();
                            if (newFile.isDirectory()) {
                                if ((newFile.listFiles().length > 0)) {
                                    MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath(), newFile.listFiles()[0].getAbsolutePath()}, null, null);
                                }
                            } else {
                                MediaScannerConnection.scanFile(activityContext, new String[]{file.getAbsolutePath(), newFile.getAbsolutePath()}, null, null);
                            }
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
        Snackbar.make(recyclerView, R.string.cut_to_clipboard, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show();
    }

    private void copyCurrentlySelectedFiles() {
        clipboardItemList = new ArrayList<>(selectedItemList);
        isClipboardToCopy = true;
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
            Intent shareIntent = new Intent();

            // should be "text/plain", but this way is workaround to remove sms, clipboard etc. from
            // the list of available actions
            shareIntent.setType("text/html");

            // this is to get back here immediately after closing sender activity
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            shareIntent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(getString(R.string.sent_by_notepadjw))); // commercial ;)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (selectedItemList.size() == 1) {
                File file = new File(selectedItemList.get(0).getPath());
                shareIntent.putExtra(Intent.EXTRA_STREAM, getUri(file));
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, selectedItemList.get(0).getName());
                shareIntent.setAction(Intent.ACTION_SEND);
            } else {
                ArrayList<Uri> fileUris = new ArrayList<>();
                for (Item item : selectedItemList) {
                    File file = new File(item.getPath());
                    fileUris.add(getUri(file));
                }
                shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
            }

            // Verify that the intent will resolve to an activity
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(shareIntent);
            }

        }
    }

    private Uri getUri(File file) {
        try {
            return FileProvider.getUriForFile(
                    activityContext,
                    "pl.bubson.notepadjw.fileprovider",
                    file);
        } catch (IllegalArgumentException e) {
            Log.e("File Selector",
                    "The selected file can't be shared: " +
                            file.getName());
        }
        return null;
    }

    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        return fileOrDirectory.delete();
    }

    public void updateFilesExtensionsRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) updateFilesExtensionsRecursive(child);
        } else {
            String newFilePath = fileOrDirectory.getAbsolutePath().replaceFirst(OLD_FILE_EXTENSION + "$", NOTE_FILE_EXTENSION);
            File newFile = new File(newFilePath);
            fileOrDirectory.renameTo(newFile);
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

    public void askForPermissionsIfNotYetAnswered(final Activity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int userAnswer = prefs.getInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.NOT_ANSWERED_YET);
        if (userAnswer==Permissions.NOT_ANSWERED_YET && !isStoragePermissionGranted(this)) {
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
        } else if (userAnswer==Permissions.NOT_ANSWERED_YET && isStoragePermissionGranted(this)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.ACCEPTED);
            editor.commit();
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
                    if (userAnswer==Permissions.NOT_ANSWERED_YET) {
                        editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.ACCEPTED);
                        editor.commit();
                        prepareMainDirectoryAndFillList();
                    }
                } else {
                    Toast.makeText(this, R.string.permission_storage_not_granted, Toast.LENGTH_LONG).show();
                    if (userAnswer==Permissions.NOT_ANSWERED_YET) {
                        editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.DENIED);
                        editor.commit();
                        prepareMainDirectoryAndFillList();
                    }
                }
            }
        }
    }

}
