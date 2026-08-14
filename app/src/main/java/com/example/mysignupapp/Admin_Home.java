package com.example.mysignupapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class Admin_Home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private View header;
    TextView usr_name, usr_email;
    String name, email, phone, org_name;
    private NavigationView navigationView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_activity_home);

        navigationView = findViewById(R.id.adm_nav_view);
        header = navigationView.getHeaderView(0);
        usr_name  = header.findViewById(R.id.nav_name);
        usr_email = header.findViewById(R.id.nav_email);

        Intent intent = getIntent();
        name     = intent.getStringExtra("name");
        org_name = intent.getStringExtra("hospital");
        email    = intent.getStringExtra("email");
        phone    = intent.getStringExtra("phone");

        usr_name.setText(name);
        usr_email.setText(email);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (savedInstanceState == null) {
            // ── Default homepage = Requests page ──────────────────────────
            openRequestsPage();
            navigationView.setCheckedItem(R.id.adm_nav_request);
        }
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

        if (id == R.id.adm_nav_request) {
            openRequestsPage();

        } else if (id == R.id.adm_nav_maps) {
            openAdminMap();

        } else if (id == R.id.adm_nav_ambulances) {
            ManageAmbulancesFragment maf = new ManageAmbulancesFragment();
            Bundle b = new Bundle();
            b.putString("phone",    phone);
            b.putString("org_name", org_name);
            maf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, maf).commit();

        } else if (id == R.id.add_nav_driver) {
            AddDriverFragment adf = new AddDriverFragment();
            Bundle b = new Bundle();
            b.putString("org_name", org_name);
            b.putString("phone",    phone);
            adf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, adf).commit();

        } else if (id == R.id.nav_view_driver) {
            DriversAvailable da = new DriversAvailable();
            Bundle b = new Bundle();
            b.putString("phone", phone);
            da.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, da).commit();

        } else if (id == R.id.adm_nav_history) {
            HistoryFragment hf = new HistoryFragment();
            Bundle b = new Bundle();
            b.putString("phone", phone);
            hf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, hf).commit();

        } else if (id == R.id.adm_nav_setting) {
            SettingFragment sf = new SettingFragment();
            Bundle b = new Bundle();
            b.putString("name",     name);
            b.putString("email",    email);
            b.putString("phone",    phone);
            b.putString("hospital", org_name);
            b.putString("role",     "admin");
            sf.setArguments(b);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, sf).commit();

        } else if (id == R.id.adm_nav_logout) {
            logout();
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /** Default page — all requests for this admin's drivers */
    private void openRequestsPage() {
        Adminrequests rf = new Adminrequests();
        Bundle b = new Bundle();
        b.putString("phone",    phone);
        b.putString("org_name", org_name);
        rf.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, rf).commit();
        navigationView.setCheckedItem(R.id.adm_nav_request);
    }

    private void openAdminMap() {
        AdminMap vm = new AdminMap();
        Bundle b = new Bundle();
        b.putString("phone", phone);
        vm.setArguments(b);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, vm).commit();
        navigationView.setCheckedItem(R.id.adm_nav_maps);
    }

    private void logout() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}