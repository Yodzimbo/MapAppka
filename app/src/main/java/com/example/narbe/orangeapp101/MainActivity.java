package com.example.narbe.orangeapp101;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.Camera;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.MyBearingTracking;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.TrackingSettings;
import com.mapbox.mapboxsdk.maps.UiSettings;
import com.mapbox.services.api.ServicesException;
import com.mapbox.services.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.services.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.services.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.services.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.services.commons.models.Position;
import com.mapzen.android.lost.api.LocationListener;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        AdapterView.OnItemSelectedListener,
        LocationListener {

    private static final int TRACKING_NONE_INDEX = 0;
    private static final int TRACKING_FOLLOW_INDEX = 1;
    private static final int BEARING_NONE_INDEX = 0;
    private static final int BEARING_GPS_INDEX = 1;
    private static final int BEARING_COMPASS_INDEX = 2;
    private static final String TAG = "LocationPickerActivity";



    private MapView mapView;
    private MapboxMap mapboxMap;
    private LostApiClient lostApiClient;
    private boolean firstRun = true;
    private Spinner locationSpinner;
    private Spinner bearingSpinner;

    private MenuItem dismissLocationTrackingOnGestureItem;
    private MenuItem dismissBearingTrackingOnGestureItem;
    private MenuItem enableRotateGesturesItem;
    private MenuItem enableScrollGesturesItem;
    private ImageView hoveringMarker;
    private Button selectLocationButton, btnPhoto;
    private Marker droppedMarker;

    private static final int ACTION_TAKE_PHOTO_B = 1;


    private static final String BITMAP_STORAGE_KEY = "viewbitmap";
    private static final String IMAGEVIEW_VISIBILITY_STORAGE_KEY = "imageviewvisibility";
    private ImageView mImageView;
    private Bitmap mImageBitmap;

    private static final String VIDEO_STORAGE_KEY = "viewvideo";
    private static final String VIDEOVIEW_VISIBILITY_STORAGE_KEY = "videoviewvisibility";
    private Uri mVideoUri;
    private String mCurrentPhotoPath;

    private static final String JPEG_FILE_PREFIX = "SLUP";
    private static final String JPEG_FILE_SUFFIX = ".jpg";
    private AlbumStorageDirFactory mAlbumStorageDirFactory = null;
    Camera camera;
    private LatLng latLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        Mapbox.getInstance( this, getString( R.string.access_token ) );
        setContentView( R.layout.activity_main );

        setupToolbar();


        mapView = (MapView) findViewById( R.id.mapView );
        mapView.onCreate( savedInstanceState );
        mapView.getMapAsync( this );


        mImageView = (ImageView) findViewById(R.id.ivPreview);
        mImageBitmap = null;

        final Button picBtn = (Button) findViewById(R.id.btnPhoto);
        setBtnListenerOrDisable(
                picBtn,
                mTakePicOnClickListener,
                MediaStore.ACTION_IMAGE_CAPTURE
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mAlbumStorageDirFactory = new FroyoAlbumDirFactory();
        } else {
            mAlbumStorageDirFactory = new BaseAlbumDirFactory();
        }

        // When user is still picking a location, we hover a marker above the mapboxMap in the center.
        // This is done by using an image view with the default marker found in the SDK. You can
        // swap out for your own marker image, just make sure it matches up with the dropped marker.
        hoveringMarker = new ImageView(this);
        hoveringMarker.setImageResource(R.drawable.mapbox_marker_icon_default);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        hoveringMarker.setLayoutParams(params);
        mapView.addView(hoveringMarker);

        // Button for user to drop marker or to pick marker back up.
        selectLocationButton = (Button) findViewById(R.id.btnMarker);
        selectLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mapboxMap != null) {
                    if (droppedMarker == null) {
                        // We first find where the hovering marker position is relative to the mapboxMap.
                        // Then we set the visibility to gone.
                        float coordinateX = hoveringMarker.getLeft() + (hoveringMarker.getWidth() / 2);
                        float coordinateY = hoveringMarker.getBottom();
                        float[] coords = new float[] {coordinateX, coordinateY};
                        final LatLng latLng = mapboxMap.getProjection().fromScreenLocation(new PointF(coords[0], coords[1]));
                        hoveringMarker.setVisibility( View.GONE);

                        // Transform the appearance of the button to become the cancel button
//                        selectLocationButton.setBackgroundColor(
//                                ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                        selectLocationButton.setText(getString(R.string.location_picker_select_location_button_cancel));

                        // Create the marker icon the dropped marker will be using.
                        Icon icon = IconFactory.getInstance(MainActivity.this).fromResource(R.drawable.mapbox_marker_icon_default);

                        // Placing the marker on the mapboxMap as soon as possible causes the illusion
                        // that the hovering marker and dropped marker are the same.
                        mapboxMap.addMarker(new MarkerViewOptions()
                                .position(latLng)
                                .icon(icon))
                                .setTitle( String.valueOf( latLng ));

                        // Finally we get the geocoding information
                        reverseGeocode(latLng);
                    } else {
                        // When the marker is dropped, the user has clicked the button to cancel.
                        // Therefore, we pick the marker back up.
                        mapboxMap.removeMarker(droppedMarker);

                        // Switch the button apperance back to select a location.
                        selectLocationButton.setBackgroundColor(
                                ContextCompat.getColor(MainActivity.this, R.color.colorPrimary));
                        selectLocationButton.setText(getString(R.string.location_picker_select_location_button_select));

                        // Lastly, set the hovering marker back to visible.
                        hoveringMarker.setVisibility(View.VISIBLE);
                        droppedMarker = null;
                    }
                }
            }
        });

    }

    private void setupMapDragging() {
        final float screenDensity = getResources().getDisplayMetrics().density;

        mapView.setOnTouchListener(new View.OnTouchListener() {
            //This onTouch code is a copy of the AnnotationManager#onTap code, except
            //I'm dragging instead of clicking, and it's being called for every touch event rather than just a tap
            //This code also makes some simplifications to the selection logic

            //If dragging ever stops working, this is the first place to look
            //The onTouch is based on AnnotationManager#onTap
            //Look for any changes in that function, and make those changes here too
            //Also need to look at AnnotationManager#getMarkersInRect, which is how I'm getting close-by markers right now
            //It might end up getting renamed, something about it may change, which won't be apparent since right now it uses reflection to be invoked

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event != null) {
                    if (event.getPointerCount() > 1) {
                        return false; //Don't drag if there are multiple fingers on screen
                    }

                    PointF tapPoint = new PointF(event.getX(), event.getY());
                    float toleranceSides = 1 * screenDensity;
                    float toleranceTopBottom = 1 * screenDensity;
                    float averageIconWidth = 42;
                    float averageIconHeight = 42;
                    RectF tapRect = new RectF(tapPoint.x - averageIconWidth / 2 - toleranceSides,
                            tapPoint.y - averageIconHeight / 2 - toleranceTopBottom,
                            tapPoint.x + averageIconWidth / 2 + toleranceSides,
                            tapPoint.y + averageIconHeight / 2 + toleranceTopBottom);

                    Marker newSelectedMarker = null;
                    List<MarkerView> nearbyMarkers = mapboxMap.getMarkerViewsInRect(tapRect);
                    List<Marker> selectedMarkers = mapboxMap.getSelectedMarkers();

                    if (selectedMarkers.isEmpty() && !nearbyMarkers.isEmpty()) {
                        Collections.sort(nearbyMarkers);
                        for (Marker marker : nearbyMarkers) {
                            if (marker instanceof MarkerView && !((MarkerView) marker).isVisible()) {
                                continue; //Don't let user click on hidden midpoints
                            }

                            newSelectedMarker = marker;
                            break;
                        }
                    } else if (!selectedMarkers.isEmpty()) {
                        newSelectedMarker = selectedMarkers.get(0);
                    }

                    if (newSelectedMarker != null && newSelectedMarker instanceof MarkerView) {
                        boolean doneDragging = event.getAction() == MotionEvent.ACTION_UP;

                        // Drag! Trying to put most logic in the drag() function
                        mapboxMap.selectMarker(newSelectedMarker); //Use the marker selection state to prevent selecting another marker when dragging over it
                        newSelectedMarker.hideInfoWindow();
                        newSelectedMarker.setPosition(mapboxMap.getProjection().fromScreenLocation(tapPoint));

                        if (doneDragging) {
                            mapboxMap.deselectMarker(newSelectedMarker);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void reverseGeocode(final LatLng point) {
        // This method is used to reverse geocode where the user has dropped the marker.
        try {
            MapboxGeocoding client = new MapboxGeocoding.Builder()
                    .setAccessToken(getString(R.string.access_token))
                    .setCoordinates( Position.fromCoordinates(point.getLongitude(), point.getLatitude()))
                    .setGeocodingType( GeocodingCriteria.TYPE_ADDRESS)
                    .build();

            client.enqueueCall(new Callback<GeocodingResponse>() {
                @Override
                public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {

                    List<CarmenFeature> results = response.body().getFeatures();
                    if (results.size() > 0) {
                        CarmenFeature feature = results.get(0);
                        // If the geocoder returns a result, we take the first in the list and update
                        // the dropped marker snippet with the information. Lastly we open the info
                        // window.
                        if (droppedMarker != null) {
                            droppedMarker.setSnippet(feature.getPlaceName());
                            mapboxMap.selectMarker(droppedMarker);
                        }

                    } else {
                        if (droppedMarker != null) {
                            droppedMarker.setSnippet(getString(R.string.location_picker_dropped_marker_snippet_no_results));
                            mapboxMap.selectMarker(droppedMarker);
                        }
                    }
                }

                @Override
                public void onFailure(Call<GeocodingResponse> call, Throwable throwable) {
                    Log.e(TAG, "Geocoding Failure: " + throwable.getMessage());
                }
            });
        } catch (ServicesException servicesException) {
            Log.e(TAG, "Error geocoding: " + servicesException.toString());
            servicesException.printStackTrace();
        }
    } // reverseGeocode

//   Drag and drop marker

    private void setupToolbar() {
        Toolbar toolbar = (Toolbar) findViewById( R.id.main_toolbar );
        setSupportActionBar( toolbar );

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled( false );
            actionBar.setDisplayHomeAsUpEnabled( true );
            actionBar.setDisplayShowHomeEnabled( true );

            locationSpinner = (Spinner) findViewById( R.id.spinner_location );
            ArrayAdapter<CharSequence> locationTrackingAdapter = ArrayAdapter.createFromResource(
                    actionBar.getThemedContext(), R.array.user_tracking_mode, android.R.layout.simple_spinner_item );
            locationTrackingAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
            locationSpinner.setAdapter( locationTrackingAdapter );

            bearingSpinner = (Spinner) findViewById( R.id.spinner_bearing );
            ArrayAdapter<CharSequence> bearingTrackingAdapter = ArrayAdapter.createFromResource(
                    actionBar.getThemedContext(), R.array.user_bearing_mode, android.R.layout.simple_spinner_item );
            bearingTrackingAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
            bearingSpinner.setAdapter( bearingTrackingAdapter );
        }
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        MainActivity.this.mapboxMap = mapboxMap;
        lostApiClient = new LostApiClient.Builder( this ).build();
        lostApiClient.connect();
        LocationRequest request = LocationRequest.create()
                .setPriority( LocationRequest.PRIORITY_HIGH_ACCURACY )
                .setInterval( 1000 )
                .setSmallestDisplacement( 10 );

        Location location = LocationServices.FusedLocationApi.getLastLocation();
        if (location != null) {
            setInitialLocation( location, 15 );
        }
        LocationServices.FusedLocationApi.requestLocationUpdates( request, this );
        showCrosshair();

        setupMapDragging();


    }

    //Celownik pośrodku ekranu
    private void showCrosshair() {
        View crosshair = new View( this );
        crosshair.setLayoutParams( new FrameLayout.LayoutParams( 15, 15, Gravity.CENTER ) );
        crosshair.setBackgroundColor( Color.RED);
        mapView.addView( crosshair );
    }

    //Inicjalizacja lokalizacji
    private void setInitialLocation(Location location, double zoom) {
        mapboxMap.animateCamera( CameraUpdateFactory.newLatLngZoom( new LatLng( location ), zoom ) );
        mapboxMap.setMyLocationEnabled( true );
        setupSpinners( mapboxMap );
        firstRun = false;
    }

    //MenuSpinner ustawienia
    private void setupSpinners(@NonNull MapboxMap mapboxMap) {
        locationSpinner.setOnItemSelectedListener( MainActivity.this );
        bearingSpinner.setOnItemSelectedListener( MainActivity.this );
        setCheckBoxes();

        mapboxMap.setOnMyLocationTrackingModeChangeListener( new MapboxMap.OnMyLocationTrackingModeChangeListener() {
            @Override
            public void onMyLocationTrackingModeChange(@MyLocationTracking.Mode int myLocationTrackingMode) {
                locationSpinner.setOnItemSelectedListener( null );
                switch (myLocationTrackingMode) {
                    case MyLocationTracking.TRACKING_NONE:
                        locationSpinner.setSelection( TRACKING_NONE_INDEX );
                        break;
                    case MyLocationTracking.TRACKING_FOLLOW:
                        locationSpinner.setSelection( TRACKING_FOLLOW_INDEX );
                        break;
                }
                locationSpinner.setOnItemSelectedListener( MainActivity.this );
            }
        } );

        mapboxMap.setOnMyBearingTrackingModeChangeListener( new MapboxMap.OnMyBearingTrackingModeChangeListener() {
            @Override
            public void onMyBearingTrackingModeChange(@MyBearingTracking.Mode int myBearingTrackingMode) {
                bearingSpinner.setOnItemSelectedListener( null );
                switch (myBearingTrackingMode) {
                    case MyBearingTracking.GPS:
                        bearingSpinner.setSelection( BEARING_GPS_INDEX );
                        break;

                    case MyBearingTracking.NONE:
                        bearingSpinner.setSelection( BEARING_NONE_INDEX );
                        break;

                    case MyBearingTracking.COMPASS:
                        bearingSpinner.setSelection( BEARING_COMPASS_INDEX );
                        break;
                }
                bearingSpinner.setOnItemSelectedListener( MainActivity.this );
            }
        } );
    }

    private void setCheckBoxes() {
        if (mapboxMap != null && dismissBearingTrackingOnGestureItem != null) {
            TrackingSettings trackingSettings = mapboxMap.getTrackingSettings();
            UiSettings uiSettings = mapboxMap.getUiSettings();
            dismissBearingTrackingOnGestureItem.setChecked( trackingSettings.isDismissBearingTrackingOnGesture() );
            dismissLocationTrackingOnGestureItem.setChecked( trackingSettings.isDismissLocationTrackingOnGesture() );
            enableRotateGesturesItem.setChecked( uiSettings.isRotateGesturesEnabled() );
            enableScrollGesturesItem.setChecked( uiSettings.isScrollGesturesEnabled() );
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate( R.menu.menu_tracking, menu );
        dismissLocationTrackingOnGestureItem = menu.findItem( R.id.action_toggle_dismissible_location );
        dismissBearingTrackingOnGestureItem = menu.findItem( R.id.action_toggle_dismissible_bearing );
        enableRotateGesturesItem = menu.findItem( R.id.action_toggle_rotate_gesture_enabled );
        enableScrollGesturesItem = menu.findItem( R.id.action_toggle_scroll_gesture_enabled );
        setCheckBoxes();
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean state;
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_toggle_dismissible_location:
                state = !item.isChecked();
                mapboxMap.getTrackingSettings().setDismissLocationTrackingOnGesture( state );
                Toast.makeText( this, "Dismiss tracking mode on gesture = " + state, Toast.LENGTH_SHORT ).show();
                item.setChecked( state );
                return true;
            case R.id.action_toggle_dismissible_bearing:
                state = !item.isChecked();
                mapboxMap.getTrackingSettings().setDismissBearingTrackingOnGesture( state );
                Toast.makeText( this, "Dismiss bearing mode on gesture = " + state, Toast.LENGTH_SHORT ).show();
                item.setChecked( state );
                return true;
            case R.id.action_toggle_rotate_gesture_enabled:
                state = !item.isChecked();
                mapboxMap.getUiSettings().setRotateGesturesEnabled( state );
                Toast.makeText( this, "Rotate gesture enabled = " + state, Toast.LENGTH_SHORT ).show();
                item.setChecked( state );
                return true;
            case R.id.action_toggle_scroll_gesture_enabled:
                state = !item.isChecked();
                mapboxMap.getUiSettings().setScrollGesturesEnabled( state );
                Toast.makeText( this, "Scroll gesture enabled = " + state, Toast.LENGTH_SHORT ).show();
                item.setChecked( state );
                return true;
//            case R.id.menu_slo:
//                E_slow.start();
//                return true;
            default:
                return super.onOptionsItemSelected( item );
        }
    }
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) throws SecurityException {
        TrackingSettings trackingSettings = mapboxMap.getTrackingSettings();
        if (parent.getId() == R.id.spinner_location) {
            switch (position) {
                case TRACKING_FOLLOW_INDEX:
                    trackingSettings.setMyLocationTrackingMode( MyLocationTracking.TRACKING_FOLLOW );
                    break;

                case TRACKING_NONE_INDEX:
                    trackingSettings.setMyLocationTrackingMode( MyLocationTracking.TRACKING_NONE );
                    break;
            }
        } else if (parent.getId() == R.id.spinner_bearing) {
            switch (position) {

                case BEARING_GPS_INDEX:
                    trackingSettings.setMyBearingTrackingMode( MyBearingTracking.GPS );
                    break;

                case BEARING_NONE_INDEX:
                    trackingSettings.setMyBearingTrackingMode( MyBearingTracking.NONE );
                    break;

                case BEARING_COMPASS_INDEX:
                    trackingSettings.setMyBearingTrackingMode( MyBearingTracking.COMPASS );
                    break;
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    //update lokalizacji z celownikiem
    @Override
    public void onLocationChanged(Location location) {
        Timber.e( "Location changed %s", location );
        if (firstRun) {
            setInitialLocation( location, 16 );
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (lostApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates( this );
            lostApiClient.disconnect();
        }
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState( outState );
        outState.putParcelable(BITMAP_STORAGE_KEY, mImageBitmap);
        outState.putParcelable(VIDEO_STORAGE_KEY, mVideoUri);
        outState.putBoolean(IMAGEVIEW_VISIBILITY_STORAGE_KEY, (mImageBitmap != null) );
        outState.putBoolean(VIDEOVIEW_VISIBILITY_STORAGE_KEY, (mVideoUri != null) );
        mapView.onSaveInstanceState( outState );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }


    /* Photo album for this application */
    private String getAlbumName() {
        return getString(R.string.album_name);
    }


    private File getAlbumDir() {
        File storageDir = null;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

            storageDir = mAlbumStorageDirFactory.getAlbumStorageDir(getAlbumName());

            if (storageDir != null) {
                if (! storageDir.mkdirs()) {
                    if (! storageDir.exists()){
                        Log.d("CameraSample", "failed to create directory");
                        return null;
                    }
                }
            }

        } else {
            Log.v(getString(R.string.app_name), "External storage is not mounted READ/WRITE.");
        }

        return storageDir;
    }


    private File createImageFile() throws IOException {
        // Create an image file name
        float coordinateX = hoveringMarker.getLeft() + (hoveringMarker.getWidth() / 2);
        float coordinateY = hoveringMarker.getBottom();
        float[] coords = new float[] {coordinateX, coordinateY};
        final LatLng latLng = mapboxMap.getProjection().fromScreenLocation(new PointF(coords[0], coords[1]));
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        LatLng location = latLng;
        String imageFileName = JPEG_FILE_PREFIX + timeStamp + "_" + location;
        File albumF = getAlbumDir();
        File imageF = File.createTempFile(imageFileName, JPEG_FILE_SUFFIX, albumF);


        return imageF;

    }


    private File setUpPhotoFile() throws IOException {

        File f = createImageFile();
        mCurrentPhotoPath = f.getAbsolutePath();

        return f;
    }

    private void setPic() {

		/* There isn't enough memory to open up more than a couple camera photos */
		/* So pre-scale the target bitmap into which the file is decoded */

		/* Get the size of the ImageView */
        int targetW = mImageView.getWidth();
        int targetH = mImageView.getHeight();

		/* Get the size of the image */
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

		/* Figure out which way needs to be reduced less */
        int scaleFactor = 1;
        if ((targetW > 0) || (targetH > 0)) {
            scaleFactor = Math.min(photoW/targetW, photoH/targetH);
        }

		/* Set bitmap options to scale the image decode target */
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

		/* Decode the JPEG file into a Bitmap */
        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

		/* Associate the Bitmap to the ImageView */
        mImageView.setImageBitmap(bitmap);
        mVideoUri = null;
        mImageView.setVisibility(View.VISIBLE);

    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);

        this.sendBroadcast(mediaScanIntent);
    }

    private void dispatchTakePictureIntent(int actionCode) {

        Intent takePictureIntent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE);

        switch(actionCode) {
            case ACTION_TAKE_PHOTO_B:
                File f = null;

                try {
                    f = setUpPhotoFile();
                    mCurrentPhotoPath = f.getAbsolutePath();
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                } catch (IOException e) {
                    e.printStackTrace();
                    f = null;
                    mCurrentPhotoPath = null;
                }
                break;

            default:
                break;
        } // switch

        startActivityForResult(takePictureIntent, actionCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTION_TAKE_PHOTO_B: {
                if (resultCode == RESULT_OK) {
                    handleBigCameraPhoto();
                }
                break;
            } // ACTION_TAKE_PHOTO_B

        }
    }

    private void handleBigCameraPhoto() {

        if (mCurrentPhotoPath != null) {
            setPic();
            galleryAddPic();
            mCurrentPhotoPath = null;
        }

    }

    Button.OnClickListener mTakePicOnClickListener =
            new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchTakePictureIntent(ACTION_TAKE_PHOTO_B);
                }
            };

    // Some lifecycle callbacks so that the image can survive orientation change


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mImageBitmap = savedInstanceState.getParcelable(BITMAP_STORAGE_KEY);
        mImageView.setImageBitmap(mImageBitmap);
        mImageView.setVisibility(
                savedInstanceState.getBoolean(IMAGEVIEW_VISIBILITY_STORAGE_KEY) ?
                        ImageView.VISIBLE : ImageView.INVISIBLE
        );

    }

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     * http://android-developers.blogspot.com/2009/01/can-i-use-this-intent.html
     *
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    private void setBtnListenerOrDisable(
            Button btn,
            Button.OnClickListener onClickListener,
            String intentName
    ) {
        if (isIntentAvailable(this, intentName)) {
            btn.setOnClickListener(onClickListener);
        } else {
            btn.setText(
                    getText(R.string.cannot).toString() + " " + btn.getText());
            btn.setClickable(false);
        }
    }
}
