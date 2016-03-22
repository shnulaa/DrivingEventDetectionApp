package com.driving.events;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;



public class PostDriveSummaryActivity extends Activity {

	private GoogleMap mGoogleMap;
    private ArrayList<Location> mLocationList;
    private ArrayList<EventData> mEventDataList;
    private LocationDBHelper mLocationDBHelper;
    private MarkerOptions mMarketOptions;
    private LatLng mLatLng;
    private int drivingScore = 100;
    private TextView drivingScoreText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.maps_layout);
        drivingScoreText = (TextView) findViewById(R.id.drivingscore);
        mLocationDBHelper = new LocationDBHelper(this);

        initializeMap();
        //Plot Route and Show Event only when any previous drive exist
        if (!mLocationDBHelper.getCurrentDriveID().equalsIgnoreCase("NoDriveExists")) {
            showEventDetailsDrivingScore();
            plotDrivingRoute();
        }else{
            drivingScoreText.setText("No previous or current drive exists, \nplease take a drive and then check back.");
        }
        //Start the service for auto drive detection
        Intent intent = new Intent(this, AutoDriveDetectionService.class);
        startService(intent);
	}

    public void initializeMap()
    {
        mGoogleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        mGoogleMap.setMyLocationEnabled(true);
        mGoogleMap.getUiSettings().setZoomControlsEnabled(false);
        mGoogleMap.getUiSettings().setZoomGesturesEnabled(true);
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(false);
        mGoogleMap.getUiSettings().setRotateGesturesEnabled(true);
    }

    public void showEventDetailsDrivingScore()
    {
        mEventDataList = mLocationDBHelper.getEventDetails();
        for (int i = 0; i < mEventDataList.size(); i++)
        {
            mLatLng = new LatLng(mEventDataList.get(i).latitude, mEventDataList.get(i).longitude);
            mMarketOptions = new MarkerOptions();
            mMarketOptions.position(mLatLng);

            switch (mEventDataList.get(i).eventType) {
                case Constants.SPEEDING_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                    mMarketOptions.title("High Speed Event");
                    mMarketOptions.snippet("Driving at speed of" + String.valueOf(mEventDataList.get(i).speed) + " miles per hour at " + convertDateToString(mEventDataList.get(i).eventTime));
                    updateDrivingScore(5);
                    break;
                case Constants.HARD_TURN_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
                    mMarketOptions.title("Hard Turning Event");
                    mMarketOptions.snippet("Turning at speed of" + String.valueOf(mEventDataList.get(i).speed) + " miles per hour at " + convertDateToString(mEventDataList.get(i).eventTime));
                    updateDrivingScore(4);
                    break;
                case Constants.HARD_BRAKING_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN));
                    mMarketOptions.title("Hard Braking Event");
                    mMarketOptions.snippet("Hard braking with acceleration of" + String.valueOf(mEventDataList.get(i).acceleration) + " miles per hour square at " + convertDateToString(mEventDataList.get(i).eventTime));
                    updateDrivingScore(3);
                    break;
                case Constants.HARD_ACCELERATION_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                    mMarketOptions.title("Hard Acceleration Event");
                    mMarketOptions.snippet("Hard accelerating with acceleration of" + String.valueOf(mEventDataList.get(i).acceleration) + " miles per hour square at " + convertDateToString(mEventDataList.get(i).eventTime));
                    updateDrivingScore(2);
                    break;
                case Constants.PHONE_DISTRACTION_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                    mMarketOptions.title("Phone Distraction Event");
                    mMarketOptions.snippet("Phone distraction at speed of" + String.valueOf(mEventDataList.get(i).speed) + " miles per hour at " + convertDateToString(mEventDataList.get(i).eventTime));
                    updateDrivingScore(1);
                    break;
                case Constants.POTENTIAL_SEVERE_CRASH_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    mMarketOptions.title("Potential Severe Crash Event");
                    mMarketOptions.snippet("Potential severe crash event captured at "+convertDateToString(mEventDataList.get(i).eventTime));
                    break;
                case Constants.PARKING_EVENT:
                    mMarketOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    mMarketOptions.title("Last Vehicle Parked Location");
                    mMarketOptions.snippet("Last vehicle parking done at "+convertDateToString(mEventDataList.get(i).eventTime));
                    break;
            }

            mGoogleMap.addMarker(mMarketOptions);
        }

        //Setting the final driving Score
        drivingScoreText.setText("Your Driving Score is " + String.valueOf(drivingScore) + " out of 100");
    }
	public void plotDrivingRoute()
	{
        //Plotting the route
		mLocationList = mLocationDBHelper.getDrivingRoute();
		int sizeLocationDataList = mLocationList.size() - 1;
		for (int i = 0; i < sizeLocationDataList; i++) 
		{
			mGoogleMap.addPolyline(new PolylineOptions()
			.add(new LatLng(mLocationList.get(i).getLatitude(), mLocationList.get(i).getLongitude()),
                    new LatLng(mLocationList.get(i+1).getLatitude(), mLocationList.get(i+1).getLongitude()))
			.width(5)
			.color(Color.BLUE).geodesic(true));
		}

        //Zooming in to the last mLatLng in drive
		if(sizeLocationDataList>0)
		{
			mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLocationList.get(mLocationList.size() - 1).getLatitude(), mLocationList.get(mLocationList.size() - 1).getLongitude()), 14));
		}
	}

    public void updateDrivingScore(int weight)
    {
        if(drivingScore > 0)
        {
            drivingScore = drivingScore - weight;
        }
    }

    public String convertDateToString(long date)
    {
        SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
        return mSimpleDateFormat.format(new Date(date)).toString();
    }
}
