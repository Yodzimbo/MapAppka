package com.example.narbe.orangeapp101;

import java.io.File;

/**
 * Created by Narbe on 19.09.2017.
 */

abstract class AlbumStorageDirFactory {
    public abstract File getAlbumStorageDir(String albumName);
}
