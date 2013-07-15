package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

abstract class StreamBitmapHunter extends BitmapHunter {

  public StreamBitmapHunter(Dispatcher dispatcher, Request request) {
    super(dispatcher, request);
  }

  @Override final Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException {
    return decodeStream(getInputStream(), options);
  }

  abstract InputStream getInputStream() throws IOException;

  abstract Bitmap decodeStream(InputStream stream, PicassoBitmapOptions options) throws IOException;
}
