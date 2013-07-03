/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

class Dispatcher {
  private static final int RETRY_DELAY = 500;
  static final int REQUEST_SUBMIT = 1;
  static final int REQUEST_COMPLETE = 2;
  static final int REQUEST_RETRY = 3;
  static final int REQUEST_FAILED = 4;
  static final int REQUEST_DECODE_FAILED = 5;

  private static final String DISPATCHER_THREAD_NAME = "Dispatcher";

  final Context context;
  final ExecutorService service;
  final Downloader downloader;
  final Map<String, BitmapHunter> hunterMap;
  final Handler handler;
  final Handler mainThreadHandler;
  final Cache cache;

  Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler,
      Downloader downloader, Cache cache) {
    DispatcherThread thread = new DispatcherThread();
    thread.start();
    this.context = context;
    this.service = service;
    this.hunterMap = new LinkedHashMap<String, BitmapHunter>();
    this.handler = new DispatcherHandler(thread.getLooper());
    this.mainThreadHandler = mainThreadHandler;
    this.downloader = downloader;
    this.cache = cache;
  }

  void dispatchSubmit(Request request) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, request));
  }

  void dispatchComplete(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, hunter));
  }

  void dispatchRetry(BitmapHunter hunter) {
    handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, hunter), RETRY_DELAY);
  }

  void dispatchFailed(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(REQUEST_DECODE_FAILED, hunter));
  }

  void performSubmit(Request request) {
    BitmapHunter hunter = hunterMap.get(request.getKey());
    if (hunter == null) {
      hunter = BitmapHunter.forRequest(context, this, request, downloader);
      hunter.attach(request);

      Bitmap cache = loadFromCache(request);
      if (cache != null) {
        performComplete(hunter);
        return;
      }

      hunterMap.put(request.getKey(), hunter);
      service.submit(hunter);
    } else {
      hunter.attach(request);
    }
  }

  void performRetry(BitmapHunter hunter) {
    if (hunter.retryCount > 0) {
      hunter.retryCount--;
      service.submit(hunter);
    } else {
      performError(hunter);
    }
  }

  void performComplete(BitmapHunter hunter) {
    if (!hunter.shouldSkipCache()) {
      cache.set(hunter.getKey(), hunter.getResult());
    }
    hunterMap.remove(hunter.getKey());
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(REQUEST_COMPLETE, hunter));
  }

  void performError(BitmapHunter hunter) {
    hunterMap.remove(hunter.getKey());
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(REQUEST_FAILED, hunter));
  }

  private Bitmap loadFromCache(Request request) {
    if (request.skipCache) return null;

    Bitmap cached = cache.get(request.getKey());
    if (cached != null) {
      request.loadedFrom = Request.LoadedFrom.MEMORY;
    }
    return cached;
  }

  private class DispatcherHandler extends Handler {

    public DispatcherHandler(Looper looper) {
      super(looper);
    }

    @Override public void handleMessage(Message msg) {
      switch (msg.what) {
        case REQUEST_SUBMIT:
          Request request = (Request) msg.obj;
          performSubmit(request);
          break;
        case REQUEST_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performComplete(hunter);
          break;
        }
        case REQUEST_RETRY: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performRetry(hunter);
          break;
        }
        case REQUEST_DECODE_FAILED: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performError(hunter);
          break;
        }
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  }

  static class DispatcherThread extends HandlerThread {

    DispatcherThread() {
      super(Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    }
  }
}
