package com.example.mysignupapp;

import static android.content.Intent.getIntent;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingFragment extends Fragment {
    TextView usr_name,usr_email,usr_phone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setting,container,false);


        String username = getArguments().getString("name");
        String email = getArguments().getString("email");
        String phone = getArguments().getString("phone");

        this.usr_name = (TextView) view.findViewById(R.id.myName);
        this.usr_email = (TextView)view.findViewById(R.id.myEmail);
        this.usr_phone = (TextView)view.findViewById(R.id.myPhone);

        usr_name.setText(username);
        usr_email.setText(email);
        usr_phone.setText(phone);
        return view;
    }
}
