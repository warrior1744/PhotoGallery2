package com.bignerdranch.photogallery2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;//Challenge:Preloading and Caching

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    private ConcurrentHashMap<T, String> mRequestMap = new ConcurrentHashMap<>();

    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;

    public LruCache<String, Bitmap> mLruCache = new LruCache<>(50);//Challenge:Preloading and Caching

    public interface ThumbnailDownloadListener<T>{
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener){
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler){
        super(TAG);
        mResponseHandler = responseHandler;
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {

        Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            //1.Build the message by calling Handler.obtainMessage(...)
            //2.Pass the other message fields into the method.
            //3.Automatically sets the target to the Handler object

            //4.call sendToTarget() to send the Message to its Handler.(the Handler now has the message)
            //5.The Handler will then put the Message on the end of Loop's message queue.
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target)
                    .sendToTarget();
        }
    }//end queueThumbnail


    /*************Challenge: Preloading and Caching************/
    public void preloadPhoto(String url){
        Log.i(TAG, "Preload from URL: "+url);
        mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget();
    }
    /**************************************************************/

    //Handling the message:
    //You obtained a message directly from mRequestHandler, which automatically sets
    //the new Message object's target field to mRequestHandler.
    //That means mRequestHandler will be in charge of processing the message when it is
    //pulled off the message queue.
    //The message's what field is MESSAGE_DOWNLOAD
    //The message's obj field is T target Value(PhotoHolder in that case)
    //The message's target field is the Handler (mRequestHandler by itself)
    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                    /*************Challenge: Preloading and Caching************/
                }else if (msg.what == MESSAGE_PRELOAD){
                    String url = (String) msg.obj;
                    Log.i(TAG, "Preload request: "+url);
                    try{
                        byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0 ,bitmapBytes.length);
                    }catch (IOException ioe){
                        ioe.printStackTrace();
                    }
                }
                /**************************************************************/
            }
        };
    }//end onLooperPrepared()

    //1.check for the existence of a URL
    //2.pass the URL to FlickrFetchr's method getUrlBytes(...)
    //3.use BitmapFactory to construct a bitmap with the array of bytes(the url of items.geturl
    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);

            if (url == null) {
                return;
            }
            final Bitmap bitmap;
            /*************Challenge: Preloading and Caching****************/
            if(mLruCache.get(url) == null) {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                bitmap = BitmapFactory
                        .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                mLruCache.put(url, bitmap);
                Log.i(TAG, "Bitmap created");
            }else{
                bitmap = mLruCache.get(url);
                Log.i(TAG, "Bitmap used again");
            }
            /**************************************************************/
            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if ((mRequestMap.get(target) != url) || mHasQuit){
                        return;
                    }

                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }//end handleRequest()

    public void clearQueue(){
        mResponseHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }

}
