package com.bignerdranch.photogallery2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private GridLayoutManager mLayoutManager;

    private int mCurrentPage;
    /***Challenge: Paging**/

    private ProgressBar mProgressBar;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        mCurrentPage = 1; /***Challenge: Paging**/
        updateItems();
        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
            @Override
            public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                photoHolder.bindDrawable(drawable);
            }
        });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }//end onCreate() method


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mLayoutManager = new GridLayoutManager(getActivity(), 3);
        mPhotoRecyclerView.setLayoutManager(mLayoutManager);

        /***Challenge: Paging**/
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) {
                    updateItems();
                }
            }
        });

        /****Challenge: Polish your App Some more**********/
        mProgressBar = (ProgressBar) v.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);
        /**************************************************/

        setupAdapter();


        /**Challenge: Dynamically Adjusting the Number of Columns**************************************/
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int numberOfColumns = 1;
                int width = mPhotoRecyclerView.getWidth();
                int widthOfSingleElement = 240;
                if (width > widthOfSingleElement) {
                    numberOfColumns = width / widthOfSingleElement;
                }
                mLayoutManager.setSpanCount(numberOfColumns);
                Log.i(TAG, "numberOfColumns:" + numberOfColumns);

            }
        });
        /**********************************************************************************************/
        return v;
    }//end onCreateView() method

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }//end setupAdapter() method

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }//end onDestroy()

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        // app:actionViewClass="androidx.appcompat.widget.SearchView"
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                Log.d(TAG, "QueryTextSubmit: " + s);
                QueryPreferences.setStoredQuery(getActivity(), s);
                hideKeyboard(getActivity());
                searchView.setQuery("", false);
                searchView.clearFocus();
                searchView.setIconified(true);
                mCurrentPage = 1;/***Challenge: Paging**/
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                Log.d(TAG, "QueryTextChange: " + s);
                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute(mCurrentPage++);
    }

    /*******************************************************************/
    private class PhotoHolder extends RecyclerView.ViewHolder {

        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }//end inner class PhotoHolder
    /*******************************************************************/
    /*******************************************************************/
    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, parent, false);
            return new PhotoHolder(view);

        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);

            /*************Challenge: Preloading and Caching****************/
            Drawable drawable;

            if (mThumbnailDownloader.mLruCache.get(galleryItem.getUrl()) == null) {
                drawable = getResources().getDrawable(R.drawable.megaman);
            } else {
                drawable = new BitmapDrawable(mThumbnailDownloader.mLruCache.get(galleryItem.getUrl()));
            }

            photoHolder.bindDrawable(drawable);
            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
            for (int i = position - 10; i < position + 10; i++) {

                int finalI = i;
                new Runnable() {
                    @Override
                    public void run() {
                        if (finalI >= 0 && finalI < mGalleryItems.size()) {
                            GalleryItem item = mGalleryItems.get(finalI);
                            if (mThumbnailDownloader.mLruCache.get(item.getUrl()) == null) {
                                mThumbnailDownloader.preloadPhoto(item.getUrl());
                            }
                        }
                    }
                };
            }
        }

        /*************Challenge: Preloading and Caching****************/

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }//end inner class PhotoAdapter
    /*******************************************************************/
    /*******************************************************************/
    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {

        private String mQuery;


        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected void onPreExecute() {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected List<GalleryItem> doInBackground(Integer... pageNumber) {
            //   return new FlickrFetchr().fetchItems();
            //   String query = "robot";

            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos(pageNumber[0]);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, pageNumber[0]);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            //mItems.addAll(galleryItems); /***Challenge: Paging**/
            mItems = galleryItems;    /***Challenge: Paging**/
            setupAdapter();
            if (mProgressBar == null) {
                mProgressBar.setVisibility(View.GONE);
            }
        }
    }//end inner class FetchItemsTask

    /*******************************************************************/

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}
