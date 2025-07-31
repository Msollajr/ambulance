package com.example.mysignupapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


public class MapsFragment extends Fragment implements OnMapReadyCallback, LocationListener, GoogleMap.OnMarkerClickListener , GoogleApiClient.OnConnectionFailedListener, RoutingListener{
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private List<String> driverList = new ArrayList<>();
    private LocationManager mLocationManager;
    private GoogleMap map, googleMap;
    private GeoFire geoFire;
    private HashMap<String, Marker> driversMarkers;
    private String userId = "";
    private String phone, driverName, driverPhone, driversPhone, driversName,driversID,driversHospital, driversEmail;
    private LinearLayout mDriverInfo, mNotification_pnl;
    private TextView drivers_name, drivers_phone, drivers_emails,drivers_hospital;
    private LatLng pickupLocation;
    private Button findDriver, contactDriver, okay;
    private Location location, mLastLocation;
    private TextView new_msg;
    private String msg_sender_id, msg_sent_id, msg_verify, Hospital;
    private Message message;
    private MediaPlayer mediaPlayer;
    //polyline object
    private List<Polyline> polylines=null;
    protected LatLng startLatLng=null;
    protected LatLng endLatLng=null;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            phone = getArguments().getString("phone");
        }
        mLocationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the notification channel
            NotificationChannel channel = new NotificationChannel("driver_channel_id", "My driver Channel", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("My Notification Channel");
            NotificationManager notificationManager = requireActivity().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        findDriver = view.findViewById(R.id.find_driver);
        contactDriver = view.findViewById(R.id.contact_driver);
        okay = view.findViewById(R.id.okay);
        new_msg = view.findViewById(R.id.new_msg);
        mDriverInfo = (LinearLayout) view.findViewById(R.id.drivers_info);
        mNotification_pnl = (LinearLayout) view.findViewById(R.id.notification);

        drivers_name = view.findViewById(R.id.driver_name);
        drivers_phone = view.findViewById(R.id.driver_phone);
        drivers_emails = view.findViewById(R.id.driver_email);
        drivers_hospital = view.findViewById(R.id.driver_hospital);

        findDriver.setOnClickListener(v -> saveUserLocation());

        // Set click listener for callDriverButton
        contactDriver.setOnClickListener(v -> {
            // Perform action when callDriverButton is clicked
            contact_Driver();
        });

        // listen for user click in the okay button
        okay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteMsg();
                mNotification_pnl.setVisibility(View.GONE);
            }
        });

    }

    private void deleteMsg() {
        DatabaseReference sent_msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child("conversation_id_2").child("messages").child(msg_sent_id);

        // Get the ID of the sender and recipient users
        String senderId = phone;
        String recipientId = msg_sender_id;

        sent_msg_ref.removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
              //  Toast.makeText(getActivity(), " Operation succeeded ", Toast.LENGTH_LONG).show();


                if (msg_verify == "accept") {

             //       findDriver.setText("finding shortest route...");
                    Route();

                } else {
                    mNotification_pnl.setVisibility(View.GONE);
                }
            }

        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getActivity(), " Operation failed to delete ", Toast.LENGTH_LONG).show();
            }
        });

    }

    private void Route() {

       // Toast.makeText(getActivity(), " Operation succeeded ", Toast.LENGTH_LONG).show();
        String AssignedDriver = "0717055717";
        LatLng startPoint = null,currentPoint = null;
        DatabaseReference driversRefLocation = FirebaseDatabase.getInstance().getReference("driversavailable").child(AssignedDriver).child("l");
        //   GeoFire geoFire = new GeoFire(driversRefLocation);

        driversRefLocation.addValueEventListener(new ValueEventListener(){
            public void onDataChange (DataSnapshot snapshot){
                if (snapshot.exists()) {
                    List<Object> map = (List<Object>) snapshot.getValue();
                    double StartLat = 0;
                    double StartLong = 0;
                    driverName = snapshot.getValue().toString();

                    if (map.get(0) != null) {
                        StartLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) {
                        StartLong = Double.parseDouble(map.get(1).toString());
                    }


                    if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (mLastLocation != null) {
                        double endLat = mLastLocation.getLatitude();
                        double endLong = mLastLocation.getLongitude();

                        endLatLng = new LatLng(endLat, endLong);
                        startLatLng = new LatLng(StartLat, StartLong);


                        findDriver.setText( "Finding routes");
                      //  googleMap.clear();
                        Findroutes(startLatLng, endLatLng);



                     //  googleMap.clear();
//                        googleMap.addMarker(new MarkerOptions().position(startLatLng).title("driver"));
//                        googleMap.addMarker(new MarkerOptions().position(endLatLng).title("user"));


                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }

        });

    }



    private void contact_Driver() {

        String conversationId = "conversation_id_1";

        // Get a reference to the Firebase Realtime Database and  a reference to the "conversations" node in the database
        DatabaseReference msg_ref = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId).child("messages");


// Get the ID of the sender and recipient users
        String senderId = phone;
        String recipientId = driversID;

        findDriver.setText("is driver" + driversID);

// Create a new message object with the sender ID, recipient ID, text, and timestamp
        String messageText = "Hello, you have a patients requests";
        long time = System.currentTimeMillis();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d HH:mm a", Locale.getDefault());
        String timestamp= dateFormat.format(new Date(time));


     //  long timestamp = System.currentTimeMillis();
        String messageId = msg_ref.push().getKey();
        Message message = new Message(senderId, recipientId, messageText, timestamp);

// Save the message object to the database
        msg_ref.child(messageId).setValue(message);
       // findDriver.setText("waiting for admin response...");
        mDriverInfo.setVisibility(View.VISIBLE);

    }

    private void saveUserLocation() {
        // Check for location permissions
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);

        } else {
            // Get the user's current location
            mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (mLastLocation != null) {
                double latitude = mLastLocation.getLatitude();
                double longitude = mLastLocation.getLongitude();
                pickupLocation = new LatLng(latitude, longitude);
                map.clear();
                map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("pick point"));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 30));

                // Save the user's location to the Firebase Realtime Database
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Requests");
                geoFire = new GeoFire(ref);
                geoFire.setLocation(phone, new GeoLocation(latitude, longitude));


                Toast.makeText(getActivity(), "Location saved", Toast.LENGTH_SHORT).show();
                findDriver.setText("Finding drivers....");
                findDrivers();
            } else {
                Toast.makeText(getActivity(), "Unable to get location", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void findDrivers() {
        DatabaseReference driversRef = FirebaseDatabase.getInstance().getReference("driversavailable");
        GeoFire geoFire = new GeoFire(driversRef);
        int radius = 100;
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverList.contains(key)) {
                    driverList.add(key);
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference("driver").child(key);
                    driverRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                Map<String, Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                                if (driverMap.get("phone") != null) {
                                    driverName = driverMap.get("name").toString();
                                    driverPhone = driverMap.get("phone").toString();
                                    driversID = driverMap.get("adminNo").toString();
                                    Hospital = driverMap.get("org_name").toString();
                                    //   String driverEmail = driverMap.get("email").toString();
                                    LatLng driverLocation = new LatLng(location.latitude, location.longitude);
                                    // .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_baseline_bus_alert_24));
                                    //    findDriver.setText(key);
                                    map.addMarker(new MarkerOptions().position(driverLocation).icon(BitmapDescriptorFactory.fromResource(R.drawable.amb_16px)).title(key));
                                    driversMarkers.put(key, map.addMarker(new MarkerOptions().position(driverLocation).title(key)));


                                } else {
                                    findDriver.setText("no drivers");
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {
                driverList.remove(key);
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        this.googleMap = googleMap;
        map = googleMap;

        map.setOnMarkerClickListener(this);

        driversMarkers = new HashMap<>();


        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(Objects.requireNonNull(requireActivity()), new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);


        } else {
            requestLocationUpdates();
        }
        // Initialize Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference conversationRef = database.getReference("conversations");


        String conversationId = "conversation_id_2";
// Get a reference to the messages node for the conversation
        DatabaseReference messagesRef = conversationRef.child(conversationId).child("messages");

// Query the messages node for messages sent to the current user ID
        String userId = phone;
        Query query = messagesRef.orderByChild("recipientId").equalTo(userId);

     //   DatabaseReference msgRef = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId).child("messages");

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

                    mNotification_pnl.setVisibility(View.VISIBLE);
                    // Do something with the message (e.g. display it in the UI)
                    //       Log.d(TAG, "Received message: " + message.getText());
                    new_msg.setText(message.getText());
                    //      msg_verify = message.getText();
                    if (message.getText().contains("accepted")) {
                        msg_verify = "accept";
                    }

                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                    }

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(Objects.requireNonNull(requireActivity()), "driver_channel_id")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("Message from driver")
                            .setContentText(message.getText())
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true);
                    Intent intent = new Intent(getActivity(), DriverMapsFragment.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getActivity(), 0, intent, 0);
                    builder.setContentIntent(pendingIntent);


                    // Show the notification
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(Objects.requireNonNull(requireActivity()));
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
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(requireActivity()), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //   mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, this);

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
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

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
        if (location != null) {
            geoFire.removeLocation(phone);
        }
        mLocationManager.removeUpdates(this);
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {

        if (driversMarkers.containsKey(marker.getTitle())) {
            mDriverInfo.setVisibility(View.VISIBLE);
            // Get driver ID from marker title
            String driverId = marker.getTitle();

            // Retrieve driver information from HashMap using driver ID as key
            Marker clickedMarker = driversMarkers.get(driverId);
            //   String driversName = clickedMarker.getTitle();
            if (driverId != null) {
                DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference("driver").child(driverId);
                driverRef.addListenerForSingleValueEvent(new ValueEventListener() {

                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                            Map<String, Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                            if (driverMap.get("phone") != null) {
                                driversPhone = driverMap.get("phone").toString();
                                driversName = driverMap.get("name").toString();
                                driversEmail = driverMap.get("email").toString();
                                driversHospital = driverMap.get("org_name").toString();
                            }
                            // Update driver information views
                            drivers_name.setText("DRIVER'S NAME: " + driversName);
                            drivers_phone.setText("DRIVE'S PHONE: " + driversPhone);
                            drivers_emails.setText("DRIVER'S EMAIL: " + driversEmail);
                            drivers_hospital.setText("DRIVER'S HOSPITAL: " + driversHospital);
                            findDriver.setText("the drivers found ");


                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Handle database error
                    }
                });
            }
        } else {
            Toast.makeText(getActivity(), "not driver", Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    // function to find Routes.
    public void Findroutes(LatLng Start, LatLng End)
    {
        //Toast.makeText(getActivity(),"get location", Toast.LENGTH_SHORT).show();
        findDriver.setText( "fetching location...");
        if(Start==null || End==null) {
            Toast.makeText(getActivity(),"Unable to get location", Toast.LENGTH_LONG).show();
        }
        else
        {

            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(Start, End)
                    .key("AIzaSyDVaottvsjR30jVvaS_yJH6chzSu5ACmBw")  //also define your api key here.
                    .build();
            routing.execute();
        }
    }

    //Routing call back functions.
    @Override
    public void onRoutingFailure(RouteException e) {
        View parentLayout = requireActivity().findViewById(R.id.content);
        Snackbar snackbar= Snackbar.make(parentLayout, e.toString(), Snackbar.LENGTH_LONG);
        snackbar.show();
//        Findroutes(start,end);
    }

    @Override
    public void onRoutingStart() {
        findDriver.setText( "Finding shortest routes...");
        //Toast.makeText(getActivity(),"Finding Route...",Toast.LENGTH_LONG).show();
    }

    //If Route finding success..
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        CameraUpdate center = CameraUpdateFactory.newLatLng(startLatLng);
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(16);
        if(polylines!=null) {
            polylines.clear();
        }
        PolylineOptions polyOptions = new PolylineOptions();
        LatLng polylineStartLatLng=null;
        LatLng polylineEndLatLng=null;


        polylines = new ArrayList<>();
        //add route(s) to the map using polyline
        for (int i = 0; i <route.size(); i++) {

            if(i==shortestRouteIndex)
            {
                polyOptions.color(getResources().getColor(R.color.blue));
                polyOptions.width(10);
                polyOptions.addAll(route.get(shortestRouteIndex).getPoints());
                Polyline polyline = map.addPolyline(polyOptions);
                polylineStartLatLng=polyline.getPoints().get(0);
                int k=polyline.getPoints().size();
                polylineEndLatLng=polyline.getPoints().get(k-1);
                polylines.add(polyline);

            }
            else {

            }

        }

        //Add Marker on route starting position
        MarkerOptions startMarker = new MarkerOptions();
        startMarker.position(polylineStartLatLng);
        startMarker.title("My Location");
        map.addMarker(startMarker);

        //Add Marker on route ending position
        MarkerOptions endMarker = new MarkerOptions();
        endMarker.position(polylineEndLatLng);
        endMarker.title("Driver");
        map.addMarker(endMarker);
    }

    @Override
    public void onRoutingCancelled() {
        Findroutes(startLatLng,endLatLng);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Findroutes(startLatLng,endLatLng);

    }
}


