package com.example.mysignupapp;

import static android.content.ContentValues.TAG;

import static androidx.core.content.ContextCompat.getSystemService;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DriverMapsFragment extends Fragment implements OnMapReadyCallback, LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private LocationManager mLocationManager;
    private GoogleMap map;
    private SupportMapFragment mapFragment;
    private GeoFire geoFire;
    private DatabaseReference ref;
    private Marker driverMarker;
    private LinearLayout mUserInfo;
    private TextView user_msg;
    private String phone, msg_sender_id, msg_sent_id,driversID;
    private String driverName = "";
    private Button accept, reject;
    private Message message;
    private MediaPlayer mediaPlayer;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_driver_maps, container, false);
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            phone = getArguments().getString("phone");
        }
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference("driver").child(phone);
        driverRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                    if (driverMap.get("adminNo") != null) {

                        driversID = driverMap.get("adminNo").toString();
                    } else {
                        Toast.makeText(getActivity(), "no drivers found", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
        //   getAssignedUser();
        mLocationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the notification channel
            NotificationChannel channel = new NotificationChannel("user_channel_id", "My user Channel", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("My Notification Channel");
            NotificationManager notificationManager = getActivity().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mUserInfo = (LinearLayout) view.findViewById(R.id.users_info);
        user_msg = view.findViewById(R.id.user_msg);
        accept = view.findViewById(R.id.OK);


        mediaPlayer = MediaPlayer.create(getActivity(), R.raw.notification_sound);

        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUserInfo.setVisibility(View.GONE);
            }
        });


    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        map = googleMap;
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);

        } else {
            requestLocationUpdates();
        }
        // Create a notification channel


        // Initialize Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference conversationRef = database.getReference("conversations");

// Specify the conversation ID you want to retrieve messages for
        String conversationId = "conversation_id_1";

// Get a reference to the messages node for the conversation
        DatabaseReference messagesRef = conversationRef.child(conversationId).child("messages");

// Query the messages node for messages sent to the current user ID
     //   String userId = driversID;
        Query query = messagesRef.orderByChild("recipientId").equalTo(driversID);


        // DatabaseReference msgref = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId).child("messages").orderByChild('recipientId').equalTo(userId);;

        // Attach a ValueEventListener to the "messages" node
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Loop through all the messages in the "messages" node
                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    // Get the message object from the DataSnapshot
                    message = messageSnapshot.getValue(Message.class);

                    msg_sender_id = message.getSenderId();
                    msg_sent_id = messageSnapshot.getKey();

                    mUserInfo.setVisibility(View.VISIBLE);

                    user_msg.setText("received text" + message.getText());

                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                    }

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(requireActivity(), "user_channel_id")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("Message from user")
                            .setContentText(message.getText())
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true);
                    Intent intent = new Intent(getActivity(), DriverMapsFragment.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getActivity(), 0, intent, 0);
                    builder.setContentIntent(pendingIntent);


                    // Show the notification
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireActivity());
                    notificationManager.notify(0, builder.build());

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle any errors that occur
                Toast.makeText(getActivity(), "Error reading messages: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //  mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdates();
            } else {
                Toast.makeText(getActivity(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        map.clear();
        map.addMarker(new MarkerOptions().position(latLng).title("Current Location"));
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

        ref = FirebaseDatabase.getInstance().getReference("driversavailable");
        geoFire = new GeoFire(ref);
        geoFire.setLocation(phone, new GeoLocation(location.getLatitude(), location.getLongitude()));

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // geoFire.removeLocation(phone);
        mLocationManager.removeUpdates(this);
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}

