package pl.bubson.notepadjw.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.documentfile.provider.DocumentFile;

/**
 * Created by Kuba on 2018-07-01.
 */

public class FilesCopier {
    private static final String TAG = "FilesCopier";
    private Context context;
    private String targetDirectoryPath;
    private DocumentFile targetDirectoryDocumentFile;
    private boolean override = false;
    private Type type;
    private AssetManager assetManager;

    public enum Type {ASSETS, EXTERNAL_STORAGE}

    public FilesCopier(Context context, Type type) {
        this.context = context;
        this.type = type;
        if (type.equals(Type.ASSETS)) assetManager = context.getAssets();
    }

    public void copy(DocumentFile documentFileFrom, DocumentFile documentFileTo) {
        targetDirectoryDocumentFile = documentFileTo;
        copyFileOrDir(documentFileFrom, null);
    }

    public void copy(DocumentFile documentFileFrom, DocumentFile documentFileTo, boolean override) {
        this.override = override;
        targetDirectoryDocumentFile = documentFileTo;
        copyFileOrDir(documentFileFrom, null);
    }

    public void copy(String pathFrom, String pathTo) {
        targetDirectoryPath = pathTo;
        copyFileOrDir(pathFrom, "");
    }

    public void copy(String pathFrom, String pathTo, boolean override) {
        targetDirectoryPath = pathTo;
        this.override = override;
        copyFileOrDir(pathFrom, "");
    }

    private void copyFileOrDir(String sourcePath, String destPath) {
        try {
            String files[];
            if (type.equals(Type.ASSETS)) {
                files = assetManager.list(sourcePath);
            } else {
                files = new File(sourcePath).list();
            }

            if (files == null || files.length == 0) { // listing file/asset instead of directory gives null/length = 0
                String destFilePath = targetDirectoryPath + "/" + destPath;
                if (!new File(destFilePath).exists() || override)
                    copyFile(sourcePath, destFilePath);
            } else {
                String fullPath = targetDirectoryPath + destPath;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdirs();
                for (String file : files) {
                    copyFileOrDir(sourcePath + "/" + file, destPath + "/" + file);
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "I/O Exception", ex);
        }
    }

    private void copyFile(String sourceFilePath, String destFilePath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            if (type.equals(Type.ASSETS)) {
                in = assetManager.open(sourceFilePath);
            } else {
                in = new FileInputStream(sourceFilePath);
            }
            out = new FileOutputStream(destFilePath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void copyFileOrDir(DocumentFile fromFile, DocumentFile toFolder) {
        if (toFolder == null) toFolder = targetDirectoryDocumentFile;
        Log.d(TAG, "From: " + fromFile.getName() + ", To folder: " + toFolder.getName());

        if (!fromFile.isDirectory()) { // it means that fromFile is a single file
            DocumentFile existingFile = toFolder.findFile(fromFile.getName());
            if (existingFile == null) {
                Log.i(TAG, "File DON'T exist. From: " + fromFile.getName() + ", To folder: " + toFolder.getName());
                DocumentFile toNewFile = toFolder.createFile("text/html", fromFile.getName().replaceAll(".html", ""));
                copyFile(fromFile, toNewFile);
            } else if (override) {
                Log.i(TAG, "Overriding. From: " + fromFile.getName() + ", To folder: " + toFolder.getName());
                existingFile.delete();
                DocumentFile toNewFile = toFolder.createFile("text/html", fromFile.getName());
                copyFile(fromFile, toNewFile);
            } else {
                Log.i(TAG, "Skipping. From: " + fromFile.getName() + ", To folder: " + toFolder.getName());
            }
        } else { // it means that fromFile is a directory
            DocumentFile existingFolder = toFolder.findFile(fromFile.getName());
            DocumentFile toSubDir = existingFolder == null ? toFolder.createDirectory(fromFile.getName()) : existingFolder;
            for (DocumentFile file : fromFile.listFiles()) {
                copyFileOrDir(file, toSubDir);
            }
        }
    }

    public void copyFile(DocumentFile fromFile, DocumentFile toFolder) {
        try {
            InputStream in = context.getContentResolver().openInputStream(fromFile.getUri());
            OutputStream out = context.getContentResolver().openOutputStream(toFolder.getUri());

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
            Log.d(TAG, "Copied file: " + fromFile.getName());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}
