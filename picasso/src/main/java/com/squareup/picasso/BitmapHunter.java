package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.provider.ContactsContract.Contacts.CONTENT_URI;
import static android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY;

abstract class BitmapHunter implements Runnable {

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

  static final int DEFAULT_RETRY_COUNT = 2;

  final Dispatcher dispatcher;
  final String key;
  final Uri uri;
  final List<Transformation> transformations;
  final PicassoBitmapOptions options;
  final boolean skipCache;

  Bitmap result;
  List<Request> joined;

  int retryCount = DEFAULT_RETRY_COUNT;

  BitmapHunter(Dispatcher dispatcher, Request request) {
    this.dispatcher = dispatcher;
    this.key = request.key;
    this.uri = request.uri;
    this.transformations = request.transformations;
    this.options = request.options;
    this.skipCache = request.skipCache;
    this.joined = new ArrayList<Request>(4);
  }

  @Override public void run() {
    try {
      Bitmap bitmap = load(uri, options);

      if (options != null) {
        bitmap = transformResult(options, bitmap, options.exifRotation);
      }

      if (transformations != null) {
        bitmap = applyCustomTransformations(transformations, bitmap);
      }

      result = bitmap;

      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (IOException e) {
      dispatcher.dispatchRetry(this);
    }
  }

  abstract Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException;

  void attach(Request request) {
    joined.add(request);
  }

  void complete(Map<Object, Request> targetsToRequests) {
    for (Request join : joined) {
      if (!join.isCancelled()) {
        targetsToRequests.remove(join.getTarget());
        join.complete(result);
      }
    }
  }

  void error(Map<Object, Request> targetsToRequests) {
    for (Request join : joined) {
      if (!join.isCancelled()) {
        targetsToRequests.remove(join.getTarget());
        join.error();
      }
    }
  }

  boolean shouldSkipCache() {
    return skipCache;
  }

  Bitmap getResult() {
    return result;
  }

  String getKey() {
    return key;
  }

  static BitmapHunter forRequest(Context context, Dispatcher dispatcher, Request request,
      Downloader downloader) {
    if (request.getResourceId() != 0) {
      return new ResourceBitmapHunter(context, dispatcher, request);
    }
    Uri uri = request.getUri();
    String scheme = uri.getScheme();
    if (SCHEME_CONTENT.equals(scheme)) {
      if (CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(CONTENT_DIRECTORY)) {
        return new ContactsPhotoBitmapHunter(context, dispatcher, request);
      } else {
        return new ContentProviderBitmapHunter(context, dispatcher, request);
      }
    } else if (SCHEME_FILE.equals(scheme)) {
      return new FileBitmapHunter(dispatcher, request, context);
    } else if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
      return new ResourceBitmapHunter(context, dispatcher, request);
    } else {
      return new NetworkBitmapHunter(dispatcher, request, downloader);
    }
  }

  static Bitmap applyCustomTransformations(List<Transformation> transformations, Bitmap result) {
    for (int i = 0, count = transformations.size(); i < count; i++) {
      Transformation transformation = transformations.get(i);
      Bitmap newResult = transformation.transform(result);

      if (newResult == null) {
        StringBuilder builder = new StringBuilder() //
            .append("Transformation ")
            .append(transformation.key())
            .append(" returned null after ")
            .append(i)
            .append(" previous transformation(s).\n\nTransformation list:\n");
        for (Transformation t : transformations) {
          builder.append(t.key()).append('\n');
        }
        throw new NullPointerException(builder.toString());
      }

      if (newResult == result && result.isRecycled()) {
        throw new IllegalStateException(
            "Transformation " + transformation.key() + " returned input Bitmap but recycled it.");
      }

      // If the transformation returned a new bitmap ensure they recycled the original.
      if (newResult != result && !result.isRecycled()) {
        throw new IllegalStateException("Transformation "
            + transformation.key()
            + " mutated input Bitmap but failed to recycle the original.");
      }
      result = newResult;
    }
    return result;
  }

  static Bitmap transformResult(PicassoBitmapOptions options, Bitmap result, int exifRotation) {
    int inWidth = result.getWidth();
    int inHeight = result.getHeight();

    int drawX = 0;
    int drawY = 0;
    int drawWidth = inWidth;
    int drawHeight = inHeight;

    Matrix matrix = new Matrix();

    if (options != null) {
      int targetWidth = options.targetWidth;
      int targetHeight = options.targetHeight;

      float targetRotation = options.targetRotation;
      if (targetRotation != 0) {
        if (options.hasRotationPivot) {
          matrix.setRotate(targetRotation, options.targetPivotX, options.targetPivotY);
        } else {
          matrix.setRotate(targetRotation);
        }
      }

      if (options.centerCrop) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale;
        if (widthRatio > heightRatio) {
          scale = widthRatio;
          int newSize = (int) Math.ceil(inHeight * (heightRatio / widthRatio));
          drawY = (inHeight - newSize) / 2;
          drawHeight = newSize;
        } else {
          scale = heightRatio;
          int newSize = (int) Math.ceil(inWidth * (widthRatio / heightRatio));
          drawX = (inWidth - newSize) / 2;
          drawWidth = newSize;
        }
        matrix.preScale(scale, scale);
      } else if (options.centerInside) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale = widthRatio < heightRatio ? widthRatio : heightRatio;
        matrix.preScale(scale, scale);
      } else if (targetWidth != 0 && targetHeight != 0 //
          && (targetWidth != inWidth || targetHeight != inHeight)) {
        // If an explicit target size has been specified and they do not match the results bounds,
        // pre-scale the existing matrix appropriately.
        float sx = targetWidth / (float) inWidth;
        float sy = targetHeight / (float) inHeight;
        matrix.preScale(sx, sy);
      }

      float targetScaleX = options.targetScaleX;
      float targetScaleY = options.targetScaleY;
      if (targetScaleX != 0 || targetScaleY != 0) {
        matrix.setScale(targetScaleX, targetScaleY);
      }
    }

    if (exifRotation != 0) {
      matrix.preRotate(exifRotation);
    }

    synchronized (DECODE_LOCK) {
      Bitmap newResult =
          Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, false);
      if (newResult != result) {
        result.recycle();
        result = newResult;
      }
    }

    return result;
  }
}
