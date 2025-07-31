package com.example.mysignupapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class AddDriverFragment extends Fragment {
    FirebaseDatabase rootNode;
    DatabaseReference reference;

    EditText dName, dEmail, dPhone, dPassword;
    Button Register;
    ImageView togglePasswordVisibility;
    boolean isPasswordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_driver, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String Admin_org = getArguments().getString("org_name");
        String Admin_phone = getArguments().getString("phone");

        dName = view.findViewById(R.id.Name);
        dEmail = view.findViewById(R.id.Email);
        dPhone = view.findViewById(R.id.Phone);
        dPassword = view.findViewById(R.id.password);
        Register = view.findViewById(R.id.Register);
        togglePasswordVisibility = view.findViewById(R.id.togglePasswordVisibility);

        // Toggle password visibility
        togglePasswordVisibility.setOnClickListener(v -> {
            if (isPasswordVisible) {
                dPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.visibility_off_24);
            } else {
                dPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.visibility_24);
            }
            dPassword.setSelection(dPassword.getText().length());
            isPasswordVisible = !isPasswordVisible;
        });

        Register.setOnClickListener(v -> {
            if (!validatePhone() | !validateUsername() | !validateEmail() | !validatePassword()) {
                return;
            }

            rootNode = FirebaseDatabase.getInstance();
            reference = rootNode.getReference("driver");

            String name = dName.getText().toString();
            String email = dEmail.getText().toString();
            String password = dPassword.getText().toString();
            String phone = dPhone.getText().toString();

            DriversHelperClass helperClass = new DriversHelperClass(name, email, password, phone, Admin_org, Admin_phone);

            reference.child(phone).setValue(helperClass)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getActivity(), "Driver added successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getActivity(), AdminMap.class);
                        intent.putExtra("org_name", Admin_org);
                        intent.putExtra("phone", Admin_phone);
                        startActivity(intent);
                        requireActivity().finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getActivity(), "Failed to add driver: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private boolean validateUsername() {
        String val = dName.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";
        if (val.isEmpty()) {
            dName.setError("Field cannot be empty");
            return false;
        } else if (val.length() > 20) {
            dName.setError("Username is too long!");
            return false;
        } else if (!val.matches(checkspaces)) {
            dName.setError("No white spaces allowed!");
            return false;
        } else {
            dName.setError(null);
            return true;
        }
    }

    private boolean validateEmail() {
        String val = dEmail.getText().toString().trim();
        String checkEmail = "[a-zA-Z0-9._-]+@[a-z]+.+[a-z]+";
        if (val.isEmpty()) {
            dEmail.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkEmail)) {
            dEmail.setError("Invalid email format!");
            return false;
        } else {
            dEmail.setError(null);
            return true;
        }
    }

    private boolean validatePassword() {
        String val = dPassword.getText().toString().trim();
        String checkPassword = "^(?=.*[a-zA-Z])(?=\\S+$).{4,}$";
        if (val.isEmpty()) {
            dPassword.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkPassword)) {
            dPassword.setError("Password must be at least 4 characters with no spaces.");
            return false;
        } else {
            dPassword.setError(null);
            return true;
        }
    }

    private boolean validatePhone() {
        String val = dPhone.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";

        if (val.isEmpty()) {
            dPhone.setError("Enter valid phone number");
            return false;
        } else if (!val.matches(checkspaces)) {
            dPhone.setError("No white spaces allowed!");
            return false;
        } else {
            dPhone.setError(null);
            return true;
        }
    }
}
