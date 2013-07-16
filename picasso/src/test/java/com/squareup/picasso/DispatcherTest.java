package com.squareup.picasso;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class DispatcherTest {

  @Mock Context context;
  @Mock ExecutorService service;
  @Mock Handler mainThreadHandler;
  @Mock Downloader downloader;
  @Mock Cache cache;
  private Dispatcher dispatcher;

  @Before public void setUp() throws Exception {
    initMocks(this);
    dispatcher = new Dispatcher(context, service, downloader, cache);
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1);
    Request request2 = mockRequest(URI_KEY_2, URI_2);
    dispatcher.performSubmit(request1);
    dispatcher.performSubmit(request2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    verify(service, times(2)).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1);
    Request request2 = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request1);
    dispatcher.performSubmit(request2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithCachedPerformsComplete() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    when(cache.get(request.getKey())).thenReturn(BITMAP_1);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).isEmpty();
    verifyZeroInteractions(service);
    verify(mainThreadHandler).sendMessage(any(Message.class));
  }

  @Test public void performCompleteSetsResultInCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verify(cache).set(hunter.getKey(), hunter.getResult());
    verify(mainThreadHandler).sendMessage(any(Message.class));
  }

  @Test public void performCompleteWithSkipCacheDoesNotCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, true);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verifyZeroInteractions(cache);
    verify(mainThreadHandler).sendMessage(any(Message.class));
  }

  @Test public void performErrorCleansUp() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).hasSize(1);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test public void performRetryTwoTimesBeforeError() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performRetry(hunter);
    verify(service).submit(hunter);
    dispatcher.performRetry(hunter);
    verify(service, times(2)).submit(hunter);
    dispatcher.performRetry(hunter);
    verifyNoMoreInteractions(service);
  }
}
