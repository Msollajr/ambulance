package com.example.mysignupapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class Drv_SettingFragment extends Fragment {
    TextView usr_name,usr_email,usr_phone,usr_hospital;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drv_fragment_setting,container,false);


        String username = getArguments().getString("name");
        String email = getArguments().getString("email");
        String phone = getArguments().getString("phone");
        String hospital= getArguments().getString("hospital");

        this.usr_name = (TextView) view.findViewById(R.id.myName);
        this.usr_email = (TextView)view.findViewById(R.id.myEmail);
        this.usr_phone = (TextView)view.findViewById(R.id.myPhone);
        this.usr_hospital = (TextView)view.findViewById(R.id.myHospital);


        usr_name.setText(username);
        usr_email.setText(email);
        usr_phone.setText(phone);
        usr_hospital.setText(hospital);
        return view;
    }
}
