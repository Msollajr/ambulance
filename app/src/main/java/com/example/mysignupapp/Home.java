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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout   drawer;
    private View           header;
    TextView               usr_name, usr_email;
    String                 name, email, phone;
    private NavigationView navigationView;

    // Profile avatar in toolbar
    private ImageView imgProfileAvatar;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        navigationView = findViewById(R.id.nav_view);
        header = navigationView.getHeaderView(0);
        usr_name  = header.findViewById(R.id.nav_name);
        usr_email = header.findViewById(R.id.nav_email);

        Intent intent = getIntent();
        name  = intent.getStringExtra("name");
        email = intent.getStringExtra("email");
        phone = intent.getStringExtra("phone");

        usr_name.setText(name != null ? name : "");
        usr_email.setText(email != null ? email : "");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ── Profile avatar in toolbar top right ───────────────────────────────
        imgProfileAvatar = findViewById(R.id.img_profile_avatar);
        loadProfileAvatar();
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
            openUserMap();
            navigationView.setCheckedItem(R.id.nav_maps);
        }
    }

    // ── Load profile avatar from Firebase users node ──────────────────────────
    private void loadProfileAvatar() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;

        FirebaseDatabase.getInstance().getReference("users")
                .child(fbUser.getUid()).child("photoUrl")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        String url = ds.getValue(String.class);
                        if (url != null && !url.isEmpty() && imgProfileAvatar != null) {
                            Glide.with(Home.this)
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

        if (id == R.id.nav_maps) {
            openUserMap();

        } else if (id == R.id.nav_first_aid) {
            FirstAidFragment faf = new FirstAidFragment();
            Bundle b = new Bundle();
            b.putString("phone", phone);
            faf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, faf)
                    .addToBackStack("first_aid")
                    .commit();

        } else if (id == R.id.nav_setting) {
            openSettings();

        } else if (id == R.id.nav_about) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AboutFragment()).commit();

        } else if (id == R.id.nav_logout) {
            logout();
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void openSettings() {
        SettingFragment sf = new SettingFragment();
        Bundle b = new Bundle();
        b.putString("name",  name);
        b.putString("email", email);
        b.putString("phone", phone);
        b.putString("role",  "user");
        sf.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, sf)
                .addToBackStack("settings")
                .commit();
        navigationView.setCheckedItem(R.id.nav_setting);
    }

    private void openUserMap() {
        MapsFragment mf = new MapsFragment();
        Bundle b = new Bundle();
        b.putString("phone", phone);
        mf.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mf).commit();
        navigationView.setCheckedItem(R.id.nav_maps);
    }

    /** Called by FirstAidDetailFragment "Call Ambulance" button */
    public void openMapsFragment() { openUserMap(); }

    /**
     * Called by SettingFragment after profile photo is updated
     * so the toolbar avatar refreshes immediately
     */
    public void refreshProfileAvatar() {
        loadProfileAvatar();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}