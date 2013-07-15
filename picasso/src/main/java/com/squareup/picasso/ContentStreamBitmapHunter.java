package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Utils.calculateInSampleSize;

class ContentStreamBitmapHunter extends BitmapHunter {

  final Context context;

  ContentStreamBitmapHunter(Context context, Dispatcher dispatcher, Request request) {
    super(dispatcher, request);
    this.context = context;
  }

  @Override Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException {
    return decodeContentStream(uri, options);
  }

  Bitmap decodeContentStream(Uri path, PicassoBitmapOptions bitmapOptions) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      InputStream is = null;
      try {
        is = contentResolver.openInputStream(path);
        BitmapFactory.decodeStream(is, null, bitmapOptions);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(bitmapOptions);
    }
    InputStream is = null;
    try {
      is = getInputStream(contentResolver, path);
      return BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, bitmapOptions);
    } finally {
      Utils.closeQuietly(is);
    }
  }

  InputStream getInputStream(ContentResolver contentResolver, Uri uri) throws IOException {
    return contentResolver.openInputStream(uri);
  }
}
