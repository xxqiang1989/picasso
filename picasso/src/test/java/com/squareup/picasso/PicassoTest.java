package com.squareup.picasso;

import android.content.Context;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.squareup.picasso.Picasso.Listener;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class PicassoTest {

  @Mock Context context;
  @Mock Downloader downloader;
  @Mock Dispatcher dispatcher;
  @Mock Cache cache;
  @Mock Listener listener;
  @Mock Stats stats;

  private Picasso picasso;

  @Before public void setUp() {
    initMocks(this);
    picasso = new Picasso(context, downloader, dispatcher, cache, listener, stats, false);
  }

  @Test public void submitWithNullTargetSkips() {
    Request request = mockRequest(URI_KEY_1, URI_1, null);
    picasso.submit(request);
    verifyZeroInteractions(dispatcher);
  }

  @Test public void submitWithTargetInvokesDispatcher() {
    Request request = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    picasso.submit(request);
    verify(dispatcher).dispatchSubmit(request);
  }

  @Test public void loadThrowsWithInvalidInput() throws IOException {
    try {
      picasso.load("");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load("      ");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load(0);
      fail("Zero resourceId should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void builderInvalidListener() throws Exception {
    try {
      new Picasso.Builder(context).listener(null);
      fail("Null listener should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).listener(listener).listener(listener);
      fail("Setting Listener twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidLoader() throws Exception {
    try {
      new Picasso.Builder(context).downloader(null);
      fail("Null Downloader should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).downloader(downloader).downloader(downloader);
      fail("Setting Downloader twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidExecutor() throws Exception {
    try {
      new Picasso.Builder(context).executor(null);
      fail("Null Executor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      ExecutorService executor = mock(ExecutorService.class);
      new Picasso.Builder(context).executor(executor).executor(executor);
      fail("Setting Executor twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidCache() throws Exception {
    try {
      new Picasso.Builder(context).memoryCache(null);
      fail("Null Cache should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).memoryCache(cache).memoryCache(cache);
      fail("Setting Cache twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }
}
