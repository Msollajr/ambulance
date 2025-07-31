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

import java.util.jar.Manifest;

public class Admin_Home extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private DrawerLayout drawer;
    private View header;
    TextView usr_name,usr_email;
    String name,email,phone,org_name;
    private NavigationView navigationView;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_activity_home);

        navigationView = findViewById(R.id.adm_nav_view);

        header=navigationView.getHeaderView(0);

        usr_name = (TextView) header.findViewById(R.id.nav_name);
        usr_email = (TextView) header.findViewById(R.id.nav_email);

        Intent intent = getIntent();
            name = intent.getStringExtra("name");
            org_name = intent.getStringExtra("hospital");
            email = intent.getStringExtra("email");
            phone = intent.getStringExtra("phone");



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
            AdminMap viewM = new AdminMap();
            Bundle bundle0 = new Bundle();
            bundle0.putString("phone", phone);
            viewM.setArguments(bundle0);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,viewM).commit();
            navigationView.setCheckedItem(R.id.adm_nav_maps);}
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
            case R.id.adm_nav_maps:
                AdminMap viewM = new AdminMap();
                Bundle bundle0 = new Bundle();
                bundle0.putString("phone", phone);
                viewM.setArguments(bundle0);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,viewM).commit();
                break;
            case R.id.adm_nav_history:
                HistoryFragment viewH = new HistoryFragment();
                Bundle bundle3 = new Bundle();
                bundle3.putString("phone", phone);
                viewH.setArguments(bundle3);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,viewH).commit();
                break;
            case R.id.adm_nav_request:
                RequestFragment viewR = new RequestFragment();
                Bundle bundle2 = new Bundle();
                bundle2.putString("phone", phone);
                viewR.setArguments(bundle2);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,viewR).commit();
                break;
            case R.id.add_nav_driver:
                AddDriverFragment Sfrag = new AddDriverFragment();
                Bundle bundle = new Bundle();
                bundle.putString("org_name", org_name);
                bundle.putString("phone", phone);
                Sfrag.setArguments(bundle);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,Sfrag).commit();
                break;
            case R.id.nav_view_driver:
                DriversAvailable viewD = new DriversAvailable();
                Bundle bundle1 = new Bundle();
                bundle1.putString("phone", phone);
               viewD.setArguments(bundle1);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,viewD).commit();
                break;
            case R.id.adm_nav_setting:
                Drv_SettingFragment hfrag = new Drv_SettingFragment();
                Bundle bundl = new Bundle();
                bundl.putString("name", name);
                bundl.putString("email", email);
                bundl.putString("phone", phone);
                bundl.putString("hospital", org_name);
                hfrag.setArguments(bundl);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,hfrag).commit();
                break;
            case R.id.adm_nav_logout:
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