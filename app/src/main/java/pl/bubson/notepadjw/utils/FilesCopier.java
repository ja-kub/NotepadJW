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

/**
 * Created by Kuba on 2018-07-01.
 */

public class FilesCopier {
    private static final String TAG = "FilesCopier";
    private Context context;
    private String targetDirectoryPath;
    private boolean override = false;
    private Type type;
    private AssetManager assetManager;

    public enum Type {ASSETS, EXTERNAL_STORAGE}

    public FilesCopier(Context context, Type type) {
        this.context = context;
        this.type = type;
        if (type.equals(Type.ASSETS)) assetManager = context.getAssets();
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

            if (files==null || files.length == 0) { // listing file/asset instead of directory gives null/length = 0
                String destFilePath = targetDirectoryPath + "/" + destPath;
                if (!new File(destFilePath).exists() || override) copyFile(sourcePath, destFilePath);
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
}
