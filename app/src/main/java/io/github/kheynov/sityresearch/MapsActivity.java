package io.github.kheynov.sityresearch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Objects;

import static io.github.kheynov.sityresearch.Constants.ONE_MEGABYTE;
import static io.github.kheynov.sityresearch.Constants.errorTag;
import static io.github.kheynov.sityresearch.Constants.infoTag;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference rootDir;
    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    ArrayList<MapObject> markers = new ArrayList<>();

    View mapView;

    private GoogleMap mMap;
    private FloatingActionButton fab;

    enum fabStates {
        ADD,
        INFO
    }

    private fabStates fabState = fabStates.ADD; //false - add, true - info

    private Marker lastSelectedMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mapView = findViewById(R.id.map);
        rootDir = storage.getReference();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        }
        fab = findViewById(R.id.fab_add_info);
        setFabState(fabState);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fabState == fabStates.INFO) {
                    show_info_dialog(findMarkerByPosition(lastSelectedMarker.getPosition()));
                }else{
                    show_add_dialog();
                }
            }
        });

    }

    void setFabState(fabStates state) {
        if (state == fabStates.INFO) {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_info_circle_white_128dp));
        } else {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_add_circle_white_128dp));
        }
    }

    void downloadImage(String reference, final ImageView imgView) {
        StorageReference downloadFileRef = rootDir.child(reference);

        downloadFileRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                imgView.setImageBitmap(bitmap);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getApplicationContext(), "Unable to download image", Toast.LENGTH_SHORT).show();
            }
        });

    }

    void uploadImage(Bitmap bitmap, String reference) {

        StorageReference uploadFileRef = rootDir.child(reference);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        UploadTask uploadTask = uploadFileRef.putBytes(data);

        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Log.i(infoTag, Objects.requireNonNull(taskSnapshot.getUploadSessionUri()).toString());
            }
        });

    }

    void setFirebaseDatabase() {
        try {
            DatabaseReference ref = firebaseDatabase.getReference("markers");

            final Snackbar snackbar = Snackbar.make(mapView, getResources().getString(R.string.updating), Snackbar.LENGTH_INDEFINITE);
            snackbar.show();
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                    for (DataSnapshot marker : dataSnapshot.getChildren()) {
                        MapObject obj = marker.getValue(MapObject.class);
                        if (obj != null) {
                            Log.i(infoTag, obj.address);
                        }
                        markers.add(obj);
                    }
                    updateMarkers(markers);
                    snackbar.dismiss();
                    snackbar.setText("Обновлено").setDuration(BaseTransientBottomBar.LENGTH_SHORT).show();

                    Log.i(infoTag, markers.toString());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Snackbar.make(mapView, getResources().getString(R.string.sync_error), Snackbar.LENGTH_LONG).show();
                    Log.i(errorTag, databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            Snackbar.make(mapView, getResources().getString(R.string.sync_error), Snackbar.LENGTH_LONG).show();
            Log.e(errorTag, e.getMessage());
        }
    }

    void postMarker(MapObject object) {
        DatabaseReference ref = firebaseDatabase.getReference("markers");
        ref.push().setValue(object, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
                final Snackbar snackbar = Snackbar.make(mapView, getResources().getString(R.string.successfully_posted), Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
                Log.e(errorTag, Objects.requireNonNull(databaseError).toString());
            }
        });
    }

    void updateMarkers(ArrayList<MapObject> markersArray) {
        mMap.clear();
        for (MapObject marker : markersArray) {
            mMap.addMarker(new MarkerOptions().position(new LatLng(marker.getLatitude(), marker.getLongitude())));
            Log.i("MAPINFO", "NEW MARKER");
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMinZoomPreference(1.0f);
        LatLng cameraFocus = new LatLng(55.00336, 82.940194);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraFocus, 1.0f));
        setFirebaseDatabase();
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(final Marker marker) {

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 11.0f), new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() {
                        fabState = fabStates.INFO;
                        setFabState(fabState);
                        lastSelectedMarker = marker;
                    }

                    @Override
                    public void onCancel() {
                        fabState = fabStates.ADD;
                        setFabState(fabState);
                    }
                });

                return true;
            }
        });
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                fabState = fabStates.ADD;
                setFabState(fabState);
            }
        });
        mMap.getUiSettings().setMapToolbarEnabled(true);

    }

    MapObject findMarkerByPosition(LatLng pos) {
        MapObject res = null;
        for (MapObject marker : markers) {
            if (marker.getLatitude() == pos.latitude
                    && marker.getLongitude() == pos.longitude) {
                res = marker;
                break;
            }
        }
        return res;
    }

    void show_add_dialog() {//TODO: Реализовать функцию добавения объекта
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(
                MapsActivity.this, R.style.BottomSheetDialog
        );
        View bottomSheetView = LayoutInflater.from(getApplicationContext())
                .inflate(R.layout.bottom_sheet_add_layout, (LinearLayout) findViewById(R.id.bottom_sheet_container));
        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }

    @SuppressLint("SetTextI18n")
    void show_info_dialog(MapObject object) {
        BottomSheetDialog bsd = new BottomSheetDialog(MapsActivity.this, R.style.BottomSheetDialog);
        View bottomSheetView = LayoutInflater.from(getApplicationContext())
                .inflate(R.layout.bottom_sheet_info_layout,
                        (LinearLayout) findViewById(R.id.bottom_sheet_info_container));
        TextView address = bottomSheetView.findViewById(R.id.address_info);
        TextView latitude = bottomSheetView.findViewById(R.id.latitude_info);
        TextView longitude = bottomSheetView.findViewById(R.id.longitude_info);
        TextView description = bottomSheetView.findViewById(R.id.object_description_info);
        ImageView objectImageView = bottomSheetView.findViewById(R.id.image_of_object_info);

        address.setText(getResources().getString(R.string.address) + object.getAddress());
        latitude.setText(getResources().getString(R.string.latitude) + object.getLatitude());
        longitude.setText(getResources().getString(R.string.longitude) + object.getLongitude());
        description.setText(getResources().getString(R.string.description) + object.getDescription());

        downloadImage(object.getImgRef(), objectImageView);//TODO: Грузить изображения в фоновом режиме чтобы избежать долгой загрузки во время просмотра информации, а также перерасхода трафика

        bsd.setContentView(bottomSheetView);
        bsd.show();
    }

    public void fab_add(View view) {
        show_add_dialog();
    }
}
