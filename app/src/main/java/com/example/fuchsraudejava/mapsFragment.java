package com.example.fuchsraudejava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

public class mapsFragment extends Fragment {

    private final OnMapReadyCallback callback = new OnMapReadyCallback() {

        @Override
        public void onMapReady(GoogleMap googleMap) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                googleMap.setMyLocationEnabled(true);
            FirebaseFirestore.getInstance().collection("Markers").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            GeoPoint loc = document.getGeoPoint("location");
                            String desc = document.getString("desc");
                            String title = document.getString("title");
                            googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(loc.getLatitude(),loc.getLongitude()))
                                    .title(title)
                                    .snippet(desc));
                        }
                    }
                }
            });
            FirebaseFirestore.getInstance().collection("Report Cases").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            if(document.getBoolean("approved")) {
                                GeoPoint loc = document.getGeoPoint("location");
                                Timestamp time = document.getTimestamp("time");
                                String timeStamp = new SimpleDateFormat("dd/MMM/yyyy").format(time.toDate());
                                googleMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(loc.getLatitude(), loc.getLongitude()))
                                        .title(timeStamp)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                            }
                        }
                    }
                }
            });
            LatLng graz = new LatLng(47.0707, 15.4395);
            LatLngBounds steiermark = new LatLngBounds(new LatLng(46.58202479324854, 13.542867166466237), new LatLng(47.8062708754351, 16.20705151869049));
            googleMap.setLatLngBoundsForCameraTarget(steiermark);
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(steiermark, width, height, 0));
            BufferedReader reader = null;
            try {
                reader =  new BufferedReader(new InputStreamReader(getResources().getAssets().open("steiermark.txt")));
                String[] coos = null;
                Double lat = 0.0;
                Double lng = 0.0;
                PolygonOptions plo = new PolygonOptions()
                        .fillColor(Color.argb(100, 255, 189, 46))
                        .strokeColor(Color.parseColor("#ffbd2e"));
                String line = "";
                while((line = reader.readLine()) != null){
                    coos = line.split(",");
                    lat = Double.valueOf(coos[0]);
                    lng = Double.valueOf(coos[1]);
                    plo.add(new LatLng(lng,lat));
                }
                Polygon steiermarkPolygon = googleMap.addPolygon(plo);
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }
    }
}