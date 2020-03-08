// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * A WebView subclass that mirrors the same implementation hacks that the system WebView does in
 * order to correctly create an InputConnection.
 *
 * <p>These hacks are only needed in Android versions below N and exist to create an InputConnection
 * on the WebView's dedicated input, or IME, thread. The majority of this proxying logic is in
 * {@link #checkInputConnectionProxy}.
 *
 * <p>See also {@link ThreadedInputConnectionProxyAdapterView}.
 */
@SuppressLint("ViewConstructor")
final class InputAwareWebView extends WebView {
  private static final String TAG = "InputAwareWebView";
  private View threadedInputConnectionProxyView;
  private ThreadedInputConnectionProxyAdapterView proxyAdapterView;
  private View containerView;

  public InputAwareWebView(Context context, View containerView) {
    super(context);
    addedJavascriptInterface = false;
    this.containerView = containerView;
  }

  void setContainerView(View containerView) {
    this.containerView = containerView;

    if (proxyAdapterView == null) {
      return;
    }

    Log.w(TAG, "The containerView has changed while the proxyAdapterView exists.");
    if (containerView != null) {
      setInputConnectionTarget(proxyAdapterView);
    }
  }

  /**
   * Set our proxy adapter view to use its cached input connection instead of creating new ones.
   *
   * <p>This is used to avoid losing our input connection when the virtual display is resized.
   */
  void lockInputConnection() {
    if (proxyAdapterView == null) {
      return;
    }

    proxyAdapterView.setLocked(true);
  }

  /** Sets the proxy adapter view back to its default behavior. */
  void unlockInputConnection() {
    if (proxyAdapterView == null) {
      return;
    }

    proxyAdapterView.setLocked(false);
  }

  /** Restore the original InputConnection, if needed. */
  void dispose() {
    resetInputConnection();
  }

  /**
   * Creates an InputConnection from the IME thread when needed.
   *
   * <p>We only need to create a {@link ThreadedInputConnectionProxyAdapterView} and create an
   * InputConnectionProxy on the IME thread when WebView is doing the same thing. So we rely on the
   * system calling this method for WebView's proxy view in order to know when we need to create our
   * own.
   *
   * <p>This method would normally be called for any View that used the InputMethodManager. We rely
   * on flutter/engine filtering the calls we receive down to the ones in our hierarchy and the
   * system WebView in order to know whether or not the system WebView expects an InputConnection on
   * the IME thread.
   */
  @Override
  public boolean checkInputConnectionProxy(final View view) {
    // Check to see if the view param is WebView's ThreadedInputConnectionProxyView.
    View previousProxy = threadedInputConnectionProxyView;
    threadedInputConnectionProxyView = view;
    if (previousProxy == view) {
      // This isn't a new ThreadedInputConnectionProxyView. Ignore it.
      return super.checkInputConnectionProxy(view);
    }
    if (containerView == null) {
      Log.e(
          TAG,
          "Can't create a proxy view because there's no container view. Text input may not work.");
      return super.checkInputConnectionProxy(view);
    }

    // We've never seen this before, so we make the assumption that this is WebView's
    // ThreadedInputConnectionProxyView. We are making the assumption that the only view that could
    // possibly be interacting with the IMM here is WebView's ThreadedInputConnectionProxyView.
    proxyAdapterView =
        new ThreadedInputConnectionProxyAdapterView(
            /*containerView=*/ containerView,
            /*targetView=*/ view,
            /*imeHandler=*/ view.getHandler());
    setInputConnectionTarget(/*targetView=*/ proxyAdapterView);
    return super.checkInputConnectionProxy(view);
  }

  /**
   * Ensure that input creation happens back on {@link #containerView}'s thread once this view no
   * longer has focus.
   *
   * <p>The logic in {@link #checkInputConnectionProxy} forces input creation to happen on Webview's
   * thread for all connections. We undo it here so users will be able to go back to typing in
   * Flutter UIs as expected.
   */
  @Override
  public void clearFocus() {
    super.clearFocus();
    resetInputConnection();
  }

  /**
   * Ensure that input creation happens back on {@link #containerView}.
   *
   * <p>The logic in {@link #checkInputConnectionProxy} forces input creation to happen on Webview's
   * thread for all connections. We undo it here so users will be able to go back to typing in
   * Flutter UIs as expected.
   */
  private void resetInputConnection() {
    if (proxyAdapterView == null) {
      // No need to reset the InputConnection to the default thread if we've never changed it.
      return;
    }
    if (containerView == null) {
      Log.e(TAG, "Can't reset the input connection to the container view because there is none.");
      return;
    }
    setInputConnectionTarget(/*targetView=*/ containerView);
  }

  /**
   * This is the crucial trick that gets the InputConnection creation to happen on the correct
   * thread pre Android N.
   * https://cs.chromium.org/chromium/src/content/public/android/java/src/org/chromium/content/browser/input/ThreadedInputConnectionFactory.java?l=169&rcl=f0698ee3e4483fad5b0c34159276f71cfaf81f3a
   *
   * <p>{@code targetView} should have a {@link View#getHandler} method with the thread that future
   * InputConnections should be created on.
   */
  private void setInputConnectionTarget(final View targetView) {
    if (containerView == null) {
      Log.e(
          TAG,
          "Can't set the input connection target because there is no containerView to use as a handler.");
      return;
    }

    targetView.requestFocus();
    containerView.post(
        new Runnable() {
          @Override
          public void run() {
            InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
            // This is a hack to make InputMethodManager believe that the target view now has focus.
            // As a result, InputMethodManager will think that targetView is focused, and will call
            // getHandler() of the view when creating input connection.

            // Step 1: Set targetView as InputMethodManager#mNextServedView. This does not affect
            // the real window focus.
            targetView.onWindowFocusChanged(true);

            // Step 2: Have InputMethodManager focus in on targetView. As a result, IMM will call
            // onCreateInputConnection() on targetView on the same thread as
            // targetView.getHandler(). It will also call subsequent InputConnection methods on this
            // thread. This is the IME thread in cases where targetView is our proxyAdapterView.
            imm.isActive(containerView);
          }
        });
  }

  ////// Taken from https://github.com/cprcrack/VideoEnabledWebView
  public class JavascriptInterface
  {
    @android.webkit.JavascriptInterface @SuppressWarnings("unused")
    public void notifyVideoEnd() // Must match Javascript interface method of VideoEnabledWebChromeClient
    {
      Log.d("___", "GOT IT");
      // This code is not executed in the UI thread, so we must force that to happen
      new Handler(Looper.getMainLooper()).post(new Runnable()
      {
        @Override
        public void run()
        {
          if (videoEnabledWebChromeClient != null)
          {
            videoEnabledWebChromeClient.onHideCustomView();
          }
        }
      });
    }
  }

  private VideoEnabledWebChromeClient videoEnabledWebChromeClient;
  private boolean addedJavascriptInterface;

  /**
   * Indicates if the video is being displayed using a custom view (typically full-screen)
   * @return true it the video is being displayed using a custom view (typically full-screen)
   */
  @SuppressWarnings("unused")
  public boolean isVideoFullscreen()
  {
    return videoEnabledWebChromeClient != null && videoEnabledWebChromeClient.isVideoFullscreen();
  }

  /**
   * Pass only a VideoEnabledWebChromeClient instance.
   */
  @Override @SuppressLint("SetJavaScriptEnabled")
  public void setWebChromeClient(WebChromeClient client)
  {
    getSettings().setJavaScriptEnabled(true);

    if (client instanceof VideoEnabledWebChromeClient)
    {
      this.videoEnabledWebChromeClient = (VideoEnabledWebChromeClient) client;
    }

    super.setWebChromeClient(client);
  }

  @Override
  public void loadData(String data, String mimeType, String encoding)
  {
    addJavascriptInterface();
    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl)
  {
    addJavascriptInterface();
    super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
  }

  @Override
  public void loadUrl(String url)
  {
    addJavascriptInterface();
    super.loadUrl(url);
  }

  @Override
  public void loadUrl(String url, Map<String, String> additionalHttpHeaders)
  {
    addJavascriptInterface();
    super.loadUrl(url, additionalHttpHeaders);
  }

  private void addJavascriptInterface()
  {
    if (!addedJavascriptInterface)
    {
      // Add javascript interface to be called when the video ends (must be done before page load)
      //noinspection all
      addJavascriptInterface(new JavascriptInterface(), "_VideoEnabledWebView"); // Must match Javascript interface name of VideoEnabledWebChromeClient

      addedJavascriptInterface = true;
    }
  }
}
