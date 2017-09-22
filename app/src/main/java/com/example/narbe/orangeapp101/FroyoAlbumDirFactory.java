package com.example.narbe.orangeapp101;

import android.os.Environment;

import java.io.File;

/**
 * Created by Narbe on 19.09.2017.
 */

public final class FroyoAlbumDirFactory extends AlbumStorageDirFactory {

    @Override
    public File getAlbumStorageDir(String albumName) {
        // TODO Auto-generated method stub
        return new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                ),
                albumName
        );
    }
}
