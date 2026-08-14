package com.example.mysignupapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Drv_Home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout    drawer;
    private NavigationView  navigationView;
    private View            header;
    TextView                usr_name, usr_email;
    String                  name, email, phone, hospital;

    // Profile avatar in toolbar
    private ImageView imgProfileAvatar;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drv_activity_home);

        navigationView = findViewById(R.id.drv_nav_view);
        header         = navigationView.getHeaderView(0);
        usr_name       = header.findViewById(R.id.nav_name);
        usr_email      = header.findViewById(R.id.nav_email);

        Intent intent = getIntent();
        name     = intent.getStringExtra("name");
        email    = intent.getStringExtra("email");
        phone    = intent.getStringExtra("phone");
        hospital = intent.getStringExtra("hospital");

        usr_name.setText(name != null ? name : "");
        usr_email.setText(email != null ? email : "");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ── Profile avatar in toolbar top right ───────────────────────────────
        imgProfileAvatar = findViewById(R.id.img_profile_avatar);
        loadDriverAvatar();
        imgProfileAvatar.setOnClickListener(v -> openSettings());

        drawer = findViewById(R.id.drawer_layout);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (savedInstanceState == null) {
            openDriverMap();
            navigationView.setCheckedItem(R.id.drv_nav_maps);
        }

        // Save FCM token so admin can send push notifications to this driver
        if (phone != null) {
            AmbulanceMessagingService.saveDriverToken(phone);
            AmbulanceMessagingService.subscribeDriverToTopic();
        }
    }

    // ── Load driver profile photo from driver/{phone}/photoUrl ───────────────
    private void loadDriverAvatar() {
        if (phone == null) return;

        FirebaseDatabase.getInstance().getReference("driver")
                .child(phone).child("photoUrl")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        String url = ds.getValue(String.class);
                        if (url != null && !url.isEmpty() && imgProfileAvatar != null) {
                            Glide.with(Drv_Home.this)
                                    .load(url)
                                    .placeholder(R.drawable.ic_person_add)
                                    .error(R.drawable.ic_person_add)
                                    .circleCrop()
                                    .into(imgProfileAvatar);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.drv_nav_maps) {
            openDriverMap();

        } else if (id == R.id.drv_nav_requests) {
            RequestsFragment rf = new RequestsFragment();
            Bundle b = new Bundle();
            b.putString("phone", phone);
            rf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, rf).commit();

        } else if (id == R.id.drv_nav_about) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AboutFragment()).commit();

        } else if (id == R.id.drv_nav_setting) {
            openSettings();

        } else if (id == R.id.drv_nav_logout) {
            logout();
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void openSettings() {
        SettingFragment sf = new SettingFragment();
        Bundle b = new Bundle();
        b.putString("name",     name);
        b.putString("email",    email);
        b.putString("phone",    phone);
        b.putString("hospital", hospital);
        b.putString("role",     "driver");
        sf.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, sf)
                .addToBackStack("settings")
                .commit();
        navigationView.setCheckedItem(R.id.drv_nav_setting);
    }

    private void openDriverMap() {
        DriverMapsFragment mf = new DriverMapsFragment();
        Bundle b = new Bundle();
        b.putString("phone", phone);
        mf.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mf).commit();
        navigationView.setCheckedItem(R.id.drv_nav_maps);
    }

    /** Called by SettingFragment after profile photo updated — refreshes avatar instantly */
    public void refreshProfileAvatar() {
        loadDriverAvatar();
    }

    private void logout() {
        if (phone != null) {
            FirebaseDatabase.getInstance()
                    .getReference("driversavailable")
                    .child(phone).removeValue();
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}