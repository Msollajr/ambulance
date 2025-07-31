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

public class Drv_Home extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private DrawerLayout drawer;
    private View header;
    TextView usr_name,usr_email;
    String name,email,phone,hospital;
    private NavigationView navigationView;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drv_activity_home);

        navigationView = findViewById(R.id.drv_nav_view);

        header=navigationView.getHeaderView(0);

        usr_name = (TextView) header.findViewById(R.id.nav_name);
        usr_email = (TextView) header.findViewById(R.id.nav_email);

        Intent intent = getIntent();
            name = intent.getStringExtra("name");
            email = intent.getStringExtra("email");
            phone = intent.getStringExtra("phone");
            hospital = intent.getStringExtra("hospital");



        usr_name.setText(name);
        usr_email.setText(email);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);

        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle =new ActionBarDrawerToggle(this,drawer,toolbar,R.string.navigation_drawer_open,R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (savedInstanceState == null){
            DriverMapsFragment mfrag = new DriverMapsFragment();
            Bundle bundle = new Bundle();
            bundle.putString("phone", phone);
            mfrag.setArguments(bundle);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,mfrag).commit();
           // getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,new DriverMapsFragment()).commit();
            navigationView.setCheckedItem(R.id.nav_maps);}
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)){
            drawer.closeDrawer(GravityCompat.START);
        }else {
            super.onBackPressed();
        }

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.drv_nav_maps:
                DriverMapsFragment mfrag = new DriverMapsFragment();
                Bundle bundle = new Bundle();
                bundle.putString("phone", phone);
                mfrag.setArguments(bundle);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,mfrag).commit();
                break;
            case R.id.drv_nav_about:
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,new AboutFragment()).commit();
                break;
            case R.id.drv_nav_setting:
                Drv_SettingFragment Sfrag = new Drv_SettingFragment();
                Bundle bundl = new Bundle();
                bundl.putString("name", name);
                bundl.putString("email", email);
                bundl.putString("phone", phone);
                bundl.putString("hospital", hospital);
                Sfrag.setArguments(bundl);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,Sfrag).commit();
                break;
            case R.id.drv_nav_logout:
                logout();
                break;
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    private void logout() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}