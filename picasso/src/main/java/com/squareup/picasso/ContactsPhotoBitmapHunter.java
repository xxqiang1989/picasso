package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

class ContactsPhotoBitmapHunter extends ContentStreamBitmapHunter {

  ContactsPhotoBitmapHunter(Context context, Dispatcher dispatcher, Request request) {
    super(context, dispatcher, request);
  }

  @Override InputStream getInputStream(ContentResolver contentResolver, Uri uri)
      throws IOException {
    return Utils.getContactPhotoStream(contentResolver, uri);
  }
}
