package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.IOException;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.BitmapHunter.transformResult;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.FILE_1_URL;
import static com.squareup.picasso.TestUtils.FILE_KEY_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_KEY_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.mockCanceledRequest;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BitmapHunterTest {

  @Mock Context context;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void runWithResultDispatchComplete() {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request, BITMAP_1);
    hunter.run();
    verify(dispatcher).dispatchComplete(hunter);
  }

  @Test public void runWithNoResultDispatchFailed() {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void runWithIoExceptionDispatchRetry() {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request, null, true);
    hunter.run();
    verify(dispatcher).dispatchRetry(hunter);
  }

  @Test public void attachRequest() {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request1);
    hunter.attach(request1);
    assertThat(hunter.joined).hasSize(1);
    hunter.attach(request2);
    assertThat(hunter.joined).hasSize(2);
  }

  @Test public void completeInvokesAllNonCanceledRequests() {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request1, BITMAP_1);
    hunter.attach(request1);
    hunter.attach(request2);
    hunter.run();
    hunter.complete(new HashMap<Object, Request>()); // TODO
    verify(request1).complete(BITMAP_1);
    verify(request2, never()).complete(BITMAP_1);
  }

  @Test public void errorInvokesAllNonCanceledRequests() {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    BitmapHunter hunter = new TestableBitmapHunter(dispatcher, request1);
    hunter.attach(request1);
    hunter.attach(request2);
    hunter.run();
    hunter.error(new HashMap<Object, Request>()); // TODO
    verify(request1).error();
    verify(request2, never()).error();
  }

  // ---------------------------------------

  @Test public void forNetworkRequest() {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(context, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(NetworkBitmapHunter.class);
  }

  @Test public void forFileWithAuthorityRequest() {
    Request request = mockRequest(FILE_KEY_1, FILE_1_URL);
    BitmapHunter hunter = forRequest(context, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(FileBitmapHunter.class);
  }

  @Test public void forAndroidResourceRequest() {
    Request request = mockRequest(RESOURCE_ID_KEY_1, null, null, RESOURCE_ID_1);
    BitmapHunter hunter = forRequest(context, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(ResourceBitmapHunter.class);
  }

  // TODO mor estatic forTests

  @Test public void exifRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(null, source, 90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
  }

  @Test public void exifRotationWithManualRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = transformResult(options, source, 90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void rotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void pivotRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;
    options.targetPivotX = 10;
    options.targetPivotY = 10;
    options.hasRotationPivot = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0 10.0 10.0"));
  }

  @Test public void scale() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetScaleX = -0.5f;
    options.targetScaleY = 2;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("scale", "-0.5 2.0"));
  }

  @Test public void resize() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 20;
    options.targetHeight = 15;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.0 1.5");
  }

  @Test public void centerCropTallTooSmall() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropTallTooLarge() {
    Bitmap source = Bitmap.createBitmap(100, 200, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerCropWideTooSmall() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropWideTooLarge() {
    Bitmap source = Bitmap.createBitmap(200, 100, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideTallTooSmall() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideTallTooLarge() {
    Bitmap source = Bitmap.createBitmap(100, 50, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideWideTooSmall() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideWideTooLarge() {
    Bitmap source = Bitmap.createBitmap(50, 100, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void reusedBitmapIsNotRecycled() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(null, source, 0);
    assertThat(result).isSameAs(source).isNotRecycled();
  }

  private static class TestableBitmapHunter extends BitmapHunter {
    private final Bitmap result;
    private final boolean throwException;

    TestableBitmapHunter(Dispatcher dispatcher, Request request) {
      this(dispatcher, request, null);
    }

    TestableBitmapHunter(Dispatcher dispatcher, Request request, Bitmap result) {
      this(dispatcher, request, result, false);
    }

    TestableBitmapHunter(Dispatcher dispatcher, Request request, Bitmap result,
        boolean throwException) {
      super(dispatcher, request);
      this.result = result;
      this.throwException = throwException;
    }

    @Override Bitmap load(Uri uri, PicassoBitmapOptions options) throws IOException {
      if (throwException) {
        throw new IOException("Failed.");
      }
      return result;
    }
  }
}
