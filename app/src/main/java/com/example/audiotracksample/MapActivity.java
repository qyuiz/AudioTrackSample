package com.example.audiotracksample;

import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MapActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.map_activity);

        MapView mapView = findViewById(R.id.mapView);
        IMapController mapController = mapView.getController();
        mapController.setZoom(16.0);
        GeoPoint centerPoint = new GeoPoint(getIntent().getDoubleExtra("Lati", 0.0),
                getIntent().getDoubleExtra("Longi", 0.0));
        mapController.setCenter(centerPoint);

        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
    }
}
