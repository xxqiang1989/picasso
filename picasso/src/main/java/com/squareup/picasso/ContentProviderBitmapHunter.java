package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.IOException;

class ContentProviderBitmapHunter extends ContentStreamBitmapHunter {

  ContentProviderBitmapHunter(Context context, Dispatcher dispatcher, Request request) {
    super(context, dispatcher, request);
  }

  @Override Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException {
    options.exifRotation = Utils.getContentProviderExifRotation(context.getContentResolver(), uri);
    return super.load(uri, options);
  }
}
