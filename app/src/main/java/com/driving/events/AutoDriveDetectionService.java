package com.driving.events;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;


public class AutoDriveDetectionService extends Service {

    private GeofenceHelper mGeofenceHelper;
    private ActivityRecognitionHelper mActivityRecognitionHelper;
    private GPSHelper mGPSHelper;
    private LocationDBHelper mLocationDBHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        mLocationDBHelper = new LocationDBHelper(getApplicationContext());
        mGPSHelper = new GPSHelper();
        mGeofenceHelper = new GeofenceHelper();
        mActivityRecognitionHelper = new ActivityRecognitionHelper();
        mGPSHelper.getSingleLocationForGeoFence();
        mActivityRecognitionHelper.startActivityUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if(geofencingEvent!=null) {
            if(!geofencingEvent.hasError()) {
                handleGeofenceInput(geofencingEvent);
            }
        } else if(ActivityRecognitionResult.hasResult(intent)) {
            handleActivityRecognitionInput(ActivityRecognitionResult.extractResult(intent));
        }
        return Service.START_STICKY;
    }

    public void handleActivityRecognitionInput(ActivityRecognitionResult result) {
        for (int i = 0; i < result.getProbableActivities().size(); i++) {
            if(DetectedActivity.WALKING == result.getProbableActivities().get(i).getType()
                    ||DetectedActivity.ON_FOOT == result.getProbableActivities().get(i).getType()) {
                //CONFIDENCE THRESHOLD is 75
                if(result.getProbableActivities().get(i).getConfidence() > Constants.CONFIDENCE_THRESHOLD && Constants.isDriveInProgress) {
                    mGPSHelper.handleWalkingActivityDuringDrive();
                }
            }
            if(DetectedActivity.IN_VEHICLE == result.getProbableActivities().get(i).getType()) {
                if(result.getProbableActivities().get(i).getConfidence() > Constants.CONFIDENCE_THRESHOLD
                        && !Constants.isDriveCheckInProgress && !Constants.isDriveInProgress) {
                    mGeofenceHelper.removeLastGeoFence();
                    mGPSHelper.handlePotentialStartDriveTrigger();
                }
            }
        }
    }

    public void handleGeofenceInput(GeofencingEvent geofencingEvent) {

        if(Geofence.GEOFENCE_TRANSITION_EXIT == geofencingEvent.getGeofenceTransition()) {
            mGeofenceHelper.removeLastGeoFence();
            if(!Constants.isDriveCheckInProgress && !Constants.isDriveInProgress) {
                mGPSHelper.handlePotentialStartDriveTrigger();
            }
        }
    }

    public void onNewLocationFoundForGeoFence(Location location) {
        mGeofenceHelper.createNewGeoFence(location);
    }

    public void onStartDrivingEvent(Location location) {

        Intent intent = new Intent(this, EventDetectionService.class);
        intent.putExtra("isDriveStarted", true);
        startService(intent);
    }

    public void onStopDrivingEvent(Location location) {

        mGeofenceHelper.createNewGeoFence(location);
        Intent intent = new Intent(this, EventDetectionService.class);
        intent.putExtra("isDriveStarted", false);
        startService(intent);
    }

    public void onStartDriveFailed(Location location) {

        mGeofenceHelper.createNewGeoFence(location);
    }

    public void onParkingDetected(Location location) {

        ArrayList<EventData> parkingList = new ArrayList<EventData>();
        EventData eventData = new EventData();
        eventData.eventType = Constants.PARKING_EVENT;
        eventData.eventTime = location.getTime();
        eventData.latitude = location.getLatitude();
        eventData.longitude = location.getLongitude();
        parkingList.add(eventData);
        mLocationDBHelper.updateEventDetails(parkingList);
        parkingList.clear();
    }

    class GeofenceHelper implements ConnectionCallbacks, OnConnectionFailedListener{

        private GoogleApiClient mGoogleApiClient;
        private GeofencingRequest mGeofencingRequest;
        private ArrayList<String> mGeofencedIDList = new ArrayList<String>();
        private Geofence mGeofence;
        private Location mLocation;
        private boolean createNewGeoFence = false;

        public void createNewGeoFence(Location location) {
            createNewGeoFence = true;
            this.mLocation = location;
            connectToGeofenceService();
        }

        public void removeLastGeoFence() {
            createNewGeoFence = false;
            connectToGeofenceService();
        }

        private void connectToGeofenceService() {

            if (mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
            }

            if(!mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            //create new geofence
            if(createNewGeoFence){
                //Step 1
                //GEOFENCE_RADIUS is 200 meters
                mGeofencedIDList.add(Constants.GEOFENCE_NAME);
                mGeofence = new Geofence.Builder().setRequestId(mGeofencedIDList.get(0))
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                        .setCircularRegion(mLocation.getLatitude(), mLocation.getLongitude(), Constants.GEOFENCE_RADIUS)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE).build();

                //Step 2
                mGeofencingRequest = new GeofencingRequest.Builder().addGeofence(mGeofence).build();
                Intent intent = new Intent(getApplicationContext(), AutoDriveDetectionService.class);
                PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                //Step 3
                LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, mGeofencingRequest, pendingIntent);

            } else {
                //remove old geofence
                LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, mGeofencedIDList);
                mGeofencedIDList.clear();
            }

            mGoogleApiClient.disconnect();
        }

        @Override
        public void onConnectionSuspended(int cause) {
        }
        @Override
        public void onConnectionFailed(ConnectionResult result) {
        }

    }

    public class ActivityRecognitionHelper implements ConnectionCallbacks, OnConnectionFailedListener{

        private GoogleApiClient mGoogleApiClientActivity;

        public void startActivityUpdates() {

            mGoogleApiClientActivity = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(ActivityRecognition.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClientActivity.connect();
        }

        public void stop() {
           // ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClientActivity, mPendingIntent);
            //You have to be connected to GoogleAPI Client to call this
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Intent intent = new Intent(getApplicationContext(), AutoDriveDetectionService.class);
            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            //ACTIVITY_RECOGNITION_REQUEST_INTERVAL is 60 seconds
            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClientActivity, Constants.ACTIVITY_RECOGNITION_REQUEST_INTERVAL, pendingIntent);
            mGoogleApiClientActivity.disconnect();
        }

        @Override
        public void onConnectionSuspended(int cause) {
        }

    }

    public class GPSHelper implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

        private GoogleApiClient mGoogleApiClient;
        private LocationRequest mLocationRequest;
        private boolean getSingleLocation = false;
        private ArrayList<Location> mPotentialStartList = new ArrayList<Location>();
        private ArrayList<Location> mPotentialStopList = new ArrayList<Location>();


        public void getSingleLocationForGeoFence() {
            getSingleLocation = true;
            startLocationUpdates();
        }

        public void handlePotentialStartDriveTrigger() {
            Constants.isDriveCheckInProgress = true;
            startLocationUpdates();
        }

        public void handleWalkingActivityDuringDrive() {
            //If not in Drive and Walking/OnFoot with high Confidence
            if(mPotentialStopList.size()>0) {
                confirmStopDrivingEvent();
            }
        }

        public void startLocationUpdates() {

            if(mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
            }

            if(!mGoogleApiClient.isConnected()) {
                mLocationRequest = LocationRequest.create();
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                //GPS_INTERVAL is 1 second
                mLocationRequest.setInterval(Constants.GPS_INTERVAL);
                mLocationRequest.setFastestInterval(Constants.GPS_INTERVAL);
                mGoogleApiClient.connect();
            }
        }

        public void stopLocationUpdates() {

            if(mGoogleApiClient != null) {
                if (mGoogleApiClient.isConnected()) {
                    LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
        }

        @Override
        public void onLocationChanged(Location location) {

            if(Constants.isDriveInProgress) {
                checkForPotentialStopEvent(location);
            } else {
                if(getSingleLocation) {
                    onNewLocationFoundForGeoFence(location);
                    stopLocationUpdates();
                    getSingleLocation = false;
                } else {
                    checkForPotentialStartEvent(location);
                }
            }
        }

        public void checkForPotentialStopEvent(Location location)
        {
            if(location.getSpeed() == 0) {
                mPotentialStopList.add(location);
                //BACKINDRIVE_SPEED_THRESHOLD is 3.57 meters per second or 8 mph
            } else if(mPotentialStopList.size()>0 && location.getSpeed()>Constants.BACKINDRIVE_SPEED_THRESHOLD) {
                //Back in the drive
                mPotentialStopList.clear();
            }
            //POTENTIALSTOP_TIME_THRESHOLD is 300 seconds or 5 mins
            if(mPotentialStopList.size()>0 && System.currentTimeMillis() - mPotentialStopList.get(0).getTime() > Constants.POTENTIALSTOP_TIME_THRESHOLD) {
                confirmStopDrivingEvent();
            }
        }

        public void confirmStopDrivingEvent() {

            Constants.isDriveInProgress = false;
            stopLocationUpdates();
            onStopDrivingEvent(mPotentialStopList.get(mPotentialStopList.size() - 1));
            onParkingDetected(mPotentialStopList.get(0));
            mPotentialStopList.clear();
        }

        public void confirmStartDrivingEvent() {

            Constants.isDriveCheckInProgress = false;
            Constants.isDriveInProgress = true;
            onStartDrivingEvent(mPotentialStartList.get(0));
            mPotentialStartList.clear();
        }

        public void confirmStartDriveFailed(Location location) {

            stopLocationUpdates();
            Constants.isDriveCheckInProgress = false;
            onStartDriveFailed(location);
            mPotentialStartList.clear();
        }


        public void checkForPotentialStartEvent(Location location) {

            mPotentialStartList.add(location);
            //POTENTIALSTOP_SPEED_THRESHOLD is 6.7 meters per second or 15 miles per hour
            if(location.getSpeed() > Constants.POTENTIALSTOP_SPEED_THRESHOLD) {
                confirmStartDrivingEvent();
                //POTENTIALSTART_TIME_THRESHOLD is 60 seconds
            } else if(location.getTime() - mPotentialStartList.get(0).getTime()  > Constants.POTENTIALSTART_TIME_THRESHOLD) {
                confirmStartDriveFailed(location);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {

            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
        }

        @Override
        public void onConnectionSuspended(int cause) {
        }

    }


    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
