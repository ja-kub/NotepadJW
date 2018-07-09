package pl.bubson.notepadjw.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Kuba on 2018-07-01.
 */

public class AssetsFilesCopier {
    private static final String TAG = "AssetsFilesCopier";
    private Context context;
    private String targetDirectoryPath;

    public AssetsFilesCopier(Context context) {
        this.context = context;
    }

    public void copyAssets(String pathFrom, String pathTo) {
        targetDirectoryPath = pathTo;
        copyFileOrDir(pathFrom, "");
    }

    private void copyFileOrDir(String assetsPath, String destPath) {
        AssetManager assetManager = context.getAssets();
        try {
            String assets[] = assetManager.list(assetsPath);
            if (assets.length == 0) {
                if (!new File(destPath).exists()) copyFile(assetsPath, destPath);
            } else {
                String fullPath = targetDirectoryPath + destPath;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdirs();
                for (String asset : assets) {
                    copyFileOrDir(assetsPath + "/" + asset, destPath + "/" + asset);
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "I/O Exception", ex);
        }
    }

    private void copyFile(String assetFilePath, String destFilePath) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(assetFilePath);
            String newFileName = targetDirectoryPath + "/" + destFilePath;
            out = new FileOutputStream(newFileName);

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
