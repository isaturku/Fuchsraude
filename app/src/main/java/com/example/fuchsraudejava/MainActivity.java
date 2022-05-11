package com.example.fuchsraudejava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private Location currentLocation;
    private boolean clicked = false;
    private final int FINE_LOCATION_RQ = 101;
    private final int CAMERA_RQ = 102;
    private final int STORAGE_RQ = 103;
    private final int IMAGE_CAPTURE = 104;
    private StorageReference storage = FirebaseStorage.getInstance().getReference();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Animation fromBottom;
    private Animation toBottom;
    private CoordinatorLayout coordinatorLayout;
    private BottomNavigationView bnv;
    private NavController navController;
    private NavHostFragment nhf;
    private FloatingActionButton fab;
    private Uri photoUri;
    private String photoPath;
    private FusedLocationProviderClient flpc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Fuchsraude);
        setContentView(R.layout.activity_main);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        bnv = (BottomNavigationView)findViewById(R.id.bnv);
        bnv.setBackground(null);
        bnv.getMenu().getItem(3).setEnabled(false);
        fromBottom = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.from_bottom_anim);
        toBottom = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.to_bottom_anim);
        nhf = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        navController = nhf.getNavController();
        NavigationUI.setupWithNavController(bnv,navController);
        fab = findViewById(R.id.fab);
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION},FINE_LOCATION_RQ);
        }
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            flpc = LocationServices.getFusedLocationProviderClient(this);
            flpc.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location loc = task.getResult();
                    checkAdjacency(loc);
                }
            });
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }
    private void takePicture(){
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String [] {Manifest.permission.CAMERA},CAMERA_RQ);
        }
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            String imageFileName = createFileName();
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            try {
                File imageFile = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );
                photoPath = imageFileName;
                photoUri = FileProvider.getUriForFile(MainActivity.this, "com.example.fuchsraudejava.fileprovider", imageFile);;
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
                startActivityForResult(takePictureIntent,IMAGE_CAPTURE);
            }catch (IOException e){
                Snackbar.make(coordinatorLayout,"Error Occurred!",Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_RQ && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            takePicture();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == IMAGE_CAPTURE){
            ProgressDialog pDialog = new ProgressDialog(this);
            pDialog.setMessage("Image Getting Uploaded");
            pDialog.setMax(100);
            pDialog.show();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            Date date = Calendar.getInstance().getTime();
            String currentDate  = sdf.format(date);
            writeOnDB();
            uploadToStorage(pDialog);
        }
    }
    private void writeOnDB(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            flpc = LocationServices.getFusedLocationProviderClient(this);
            flpc.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    currentLocation = task.getResult();
                    if (currentLocation != null){
                        HashMap<String,Object> report = new HashMap<String,Object>();
                        report.put("pic",photoPath);
                        report.put("location",new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude()));
                        report.put("alt",currentLocation.getAltitude());
                        report.put("time",Timestamp.now());
                        report.put("approved",false);
                        db.collection("Report Cases")
                                .add(report)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Log.v("report","Report uploaded in Database");
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.v("report","Report not uploaded in Database");
                            }
                        });
                    }
                }
            });
        }
    }
    private void uploadToStorage(ProgressDialog pDialog){
        UploadTask uploadTask = storage.child(photoPath).putFile(photoUri);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Snackbar.make(fab,"Photo could not be uploaded",Snackbar.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                pDialog.dismiss();
                Snackbar.make(fab,"Photo uploaded succesfully",Snackbar.LENGTH_SHORT).show();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                Double progress = (100.0 * (snapshot.getBytesTransferred()/snapshot.getTotalByteCount()));
                pDialog.setProgress(progress.intValue());
            }
        });
    }
    private String createFileName(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "fr_" + timeStamp;
        return imageFileName;
    }
    private void checkAdjacency(Location loc){
        FirebaseFirestore.getInstance().collection("Markers").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Location illness = new Location(LocationManager.GPS_PROVIDER);
                        illness.setLatitude(document.getGeoPoint("location").getLatitude());
                        illness.setLongitude(document.getGeoPoint("location").getLongitude());
                        if(illness.distanceTo(loc) <= 1000){
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(coordinatorLayout.getContext());
                            alertDialog.setMessage(R.string.alertText)
                                    .setTitle(R.string.alertTitle)
                                    .setIcon(R.drawable.ic_twotone_warning_24)
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    });
                            alertDialog.create().show();
                            return;
                        }
                    }
                }
            }
        });
        FirebaseFirestore.getInstance().collection("Report Cases").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        if (document.getBoolean("approved")) {
                            Location illness = new Location(LocationManager.GPS_PROVIDER);
                            illness.setLatitude(document.getGeoPoint("location").getLatitude());
                            illness.setLongitude(document.getGeoPoint("location").getLongitude());
                            if (illness.distanceTo(loc) <= 1000) {
                                AlertDialog.Builder alertDialog = new AlertDialog.Builder(coordinatorLayout.getContext());
                                alertDialog.setMessage(R.string.alertText)
                                        .setTitle(R.string.alertTitle)
                                        .setIcon(R.drawable.ic_twotone_warning_24)
                                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                            }
                                        });
                                alertDialog.create().show();
                                return;
                            }
                        }
                    }
                }
            }
        });
    }
}