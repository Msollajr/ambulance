package com.example.mysignupapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Register extends AppCompatActivity {

    EditText Upassword, Uphone, Uname, Uemail;
    TextView goto_log, goto_admin;
    ImageView togglePassword;
    boolean isPasswordVisible = false;
    DatabaseReference reference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Views
        Upassword = findViewById(R.id.password);
        Uphone = findViewById(R.id.Phone);
        Uname = findViewById(R.id.username);
        Uemail = findViewById(R.id.email);
        goto_log = findViewById(R.id.log_here);
        goto_admin = findViewById(R.id.goto_admin);
        togglePassword = findViewById(R.id.togglePasswordVisibility);
        MaterialButton reg_btn = findViewById(R.id.registerbtn);

        // Show/Hide Password
        togglePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPasswordVisible) {
                    // Hide password
                    Upassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_off_24); // closed eye icon
                } else {
                    // Show password
                    Upassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_24); // open eye icon
                }
                isPasswordVisible = !isPasswordVisible;
                Upassword.setSelection(Upassword.getText().length()); // keep cursor at end
            }
        });

        // Register Button Click
        reg_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!validateUsername() | !validateEmail() | !validatePassword() | !validatePhone()) {
                    return;
                }

                String name = Uname.getText().toString().trim();
                String email = Uemail.getText().toString().trim();
                String password = Upassword.getText().toString().trim();
                String phone = Uphone.getText().toString().trim();

                reference = FirebaseDatabase.getInstance().getReference("users");

                reference.child(phone).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Uphone.setError("Phone number already exists");
                            Uphone.requestFocus();
                        } else {
                            UserHelperClass helperClass = new UserHelperClass(name, email, password, phone);
                            reference.child(phone).setValue(helperClass);
                            Toast.makeText(Register.this, "User Registered Successfully", Toast.LENGTH_LONG).show();

                            // Redirect to login
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            startActivity(intent);
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Register.this, "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Go to login
        goto_log.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
        });

        // Go to admin
        goto_admin.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), Register_admin.class);
            startActivity(intent);
        });
    }

    // Validation methods
    private boolean validateUsername() {
        String val = Uname.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";

        if (val.isEmpty()) {
            Uname.setError("Field cannot be empty");
            return false;
        } else if (val.length() > 20) {
            Uname.setError("Username is too long");
            return false;
        } else if (!val.matches(checkspaces)) {
            Uname.setError("No white spaces allowed");
            return false;
        } else {
            Uname.setError(null);
            return true;
        }
    }

    private boolean validateEmail() {
        String val = Uemail.getText().toString().trim();
        String checkEmail = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if (val.isEmpty()) {
            Uemail.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkEmail)) {
            Uemail.setError("Invalid Email");
            return false;
        } else {
            Uemail.setError(null);
            return true;
        }
    }

    private boolean validatePassword() {
        String val = Upassword.getText().toString().trim();
        String checkPassword = "^(?=.*[a-zA-Z])(?=\\S+$).{4,}$";

        if (val.isEmpty()) {
            Upassword.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkPassword)) {
            Upassword.setError("Password should be at least 4 characters and no spaces");
            return false;
        } else {
            Upassword.setError(null);
            return true;
        }
    }

    private boolean validatePhone() {
        String val = Uphone.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";

        if (val.isEmpty()) {
            Uphone.setError("Enter a valid phone number");
            return false;
        } else if (!val.matches(checkspaces)) {
            Uphone.setError("No white spaces allowed");
            return false;
        } else {
            Uphone.setError(null);
            return true;
        }
    }
}
