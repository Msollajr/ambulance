package com.example.mysignupapp;

import static android.content.ContentValues.TAG;

import static androidx.core.content.ContextCompat.getSystemService;

import android.Manifest;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class AdminMap extends Fragment implements OnMapReadyCallback, LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private LocationManager mLocationManager;
    private GoogleMap map;
    private SupportMapFragment mapFragment;
    private GeoFire geoFire;
    private DatabaseReference ref;
    private Marker driverMarker;
    private LinearLayout mUserInfo;
    private TextView user_msg;
    private String phone, msg_sender_id, msg_sent_id;
    private String driverName = "";
    private String userId = "";
    private Button accept, reject;
    private Message message;
    private MediaPlayer mediaPlayer;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_map, container, false);
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            phone = getArguments().getString("phone");
        }
        //   getAssignedUser();
        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

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
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        mUserInfo = (LinearLayout) view.findViewById(R.id.Users_info);
        user_msg = view.findViewById(R.id.User_msg);
        accept = view.findViewById(R.id.accept);
        reject = view.findViewById(R.id.reject);

        mediaPlayer = MediaPlayer.create(getActivity(), R.raw.notification_sound);

        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatabaseReference msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("conversation_id_2").child("messages");
                DatabaseReference sent_msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("conversation_id_1").child("messages").child(msg_sent_id);
                DatabaseReference archived_msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("archived_conversations").child("messages").child(msg_sent_id);


                // Get the ID of the sender and recipient users
                String senderId = phone;
                String recipientId = msg_sender_id;

                // Create a new message object with the sender ID, recipient ID, text, and timestamp
                String messageText = "Driver accepted request and ambulance is on the way";
                long time = System.currentTimeMillis();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");
                String timestamp= dateFormat.format(new Date(time));
                String messageId = msg_ref.push().getKey();
                Message message = new Message(senderId, recipientId, messageText, timestamp);
                // Save the message object to the database
                msg_ref.child(messageId).setValue(message);

                //getting dara for set message
                sent_msg_ref.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Object sent_msg_data = snapshot.getValue();

                        archived_msg_ref.setValue(sent_msg_data).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                Toast.makeText(getActivity(), "Operation succeed", Toast.LENGTH_LONG).show();
                            }
                        }).addOnFailureListener(e -> Toast.makeText(getActivity(), "Operation failed", Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
                sent_msg_ref.removeValue();

                //hiding the message bar
               mUserInfo.setVisibility(View.GONE);


            }
        });

        reject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatabaseReference msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("conversation_id_2").child("messages");
                DatabaseReference sent_msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("conversation_id_1").child("messages").child(msg_sent_id);

                // Get the ID of the sender and recipient users
                String senderId = phone;
                String recipientId = msg_sender_id;

                // Create a new message object with the sender ID, recipient ID, text, and timestamp
                String messageText = "Driver rejected request due to poor condition of ambulance";
                long time = System.currentTimeMillis();
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d HH:mm a", Locale.getDefault());
                String timestamp= dateFormat.format(new Date(time));

                String messageId = msg_ref.push().getKey();
                Message message = new Message(senderId, recipientId, messageText, timestamp);
                // Save the message object to the database
                msg_ref.child(messageId).setValue(message);

                sent_msg_ref.removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Toast.makeText(getActivity(), " Operation Succeed ", Toast.LENGTH_LONG).show();
                    }

                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getActivity(), " Operation failed to delete ", Toast.LENGTH_LONG).show();
                    }
                });

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
        String userId = phone;
        Query query = messagesRef.orderByChild("recipientId").equalTo(userId);


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
                    // Do something with the message (e.g. display it in the UI)
                    //       Log.d(TAG, "Received message: " + message.getText());
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
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) !=
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

