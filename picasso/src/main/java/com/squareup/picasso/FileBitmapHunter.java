package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.IOException;

class FileBitmapHunter extends ContentStreamBitmapHunter {

  FileBitmapHunter(Dispatcher dispatcher, Request request, Context context) {
    super(context, dispatcher, request);
  }

  @Override Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException {
    options.exifRotation = Utils.getFileExifRotation(uri.getPath());
    return super.load(uri, options);
  }
}
