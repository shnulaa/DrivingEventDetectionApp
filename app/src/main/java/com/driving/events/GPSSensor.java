package com.driving.events;


import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

public class GPSSensor implements ConnectionCallbacks,
        OnConnectionFailedListener, LocationListener {

    private ArrayList<Location> mLocationDataList = new ArrayList<Location>();
    private static GPSSensor gpsSensor;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    public static GPSSensor getInstance()
    {
        if (gpsSensor == null) {
            gpsSensor = new GPSSensor();
        }
        return gpsSensor;
    }

    public void register(Handler mHandler, Context context)
    {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .setHandler(mHandler)
                .addOnConnectionFailedListener(this)
                .build();
        //GPS_INTERVAL is 1000 milliseconds, i.e. 1 second
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(Constants.GPS_INTERVAL);
        mLocationRequest.setFastestInterval(Constants.GPS_INTERVAL);
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    public void unregister() {

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onLocationChanged(Location location) {

        mLocationDataList.add(location);
    }

    public ArrayList<Location> getGPSList()
    {
        return mLocationDataList;
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }
    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }
}
