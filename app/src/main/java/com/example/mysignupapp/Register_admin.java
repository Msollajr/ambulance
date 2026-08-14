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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Register_admin extends AppCompatActivity {

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText Apassword, Aphone, Aname, Aemail, Aorg_name;
    private TextView goto_log;
    private ImageView togglePassword;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_admin);

        mAuth  = FirebaseAuth.getInstance();
        dbRef  = FirebaseDatabase.getInstance().getReference("admin");

        Apassword  = findViewById(R.id.password);
        Aphone     = findViewById(R.id.Phone);
        Aname      = findViewById(R.id.username);
        Aemail     = findViewById(R.id.email);
        Aorg_name  = findViewById(R.id.org_name);
        goto_log   = findViewById(R.id.log_here);
        togglePassword = findViewById(R.id.togglePasswordVisibility);
        MaterialButton reg_btn = findViewById(R.id.registerbtn);

        // ── Password toggle ────────────────────────────────────────────────────
        togglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                Apassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setImageResource(R.drawable.visibility_off_24);
            } else {
                Apassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setImageResource(R.drawable.visibility_24);
            }
            isPasswordVisible = !isPasswordVisible;
            Apassword.setSelection(Apassword.getText().length());
        });

        // ── Register button ────────────────────────────────────────────────────
        reg_btn.setOnClickListener(v -> {
            if (!validatePhone() | !validateUsername() | !validateOrgname()
                    | !validateEmail() | !validatePassword()) return;
            registerAdmin();
        });

        goto_log.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
    }

    // ── Registration flow ─────────────────────────────────────────────────────

    private void registerAdmin() {
        String name     = Aname.getText().toString().trim();
        String email    = Aemail.getText().toString().trim();
        String password = Apassword.getText().toString().trim();
        String phone    = Aphone.getText().toString().trim();
        String org_name = Aorg_name.getText().toString().trim();

        // Step 1: Create Firebase Auth account (email + password)
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid = firebaseUser.getUid();

                        // Step 2: Save admin profile to Realtime DB under UID
                        // UID = Auth UID = DB key → permanently linked
                        // Password is NOT stored in DB — Auth handles it securely
                        AdminHelperClass admin =
                                new AdminHelperClass(name, email, "", phone, org_name);
                        dbRef.child(uid).setValue(admin)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this,
                                            "Admin registered successfully! Please login.",
                                            Toast.LENGTH_LONG).show();

                                    mAuth.signOut();

                                    Intent intent = new Intent(this, MainActivity.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this,
                                                "Profile save failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show());

                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();

                        if (error != null && error.contains("email")) {
                            Aemail.setError("Email already registered");
                            Aemail.requestFocus();
                        }
                    }
                });
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private boolean validateUsername() {
        String val = Aname.getText().toString().trim();
        if (val.isEmpty()) { Aname.setError("Field cannot be empty"); return false; }
        if (val.length() > 20) { Aname.setError("Username is too long!"); return false; }
        if (!val.matches("\\A\\w{4,20}\\z")) {
            Aname.setError("No white spaces allowed!"); return false; }
        Aname.setError(null);
        return true;
    }

    private boolean validateOrgname() {
        String val = Aorg_name.getText().toString().trim();
        if (val.isEmpty()) { Aorg_name.setError("Field cannot be empty"); return false; }
        if (val.length() > 40) {
            Aorg_name.setError("Organization name is too long!"); return false; }
        Aorg_name.setError(null);
        return true;
    }

    private boolean validateEmail() {
        String val = Aemail.getText().toString().trim();
        if (val.isEmpty()) { Aemail.setError("Field cannot be empty"); return false; }
        if (!val.matches("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+")) {
            Aemail.setError("Invalid email address!"); return false; }
        Aemail.setError(null);
        return true;
    }

    private boolean validatePassword() {
        String val = Apassword.getText().toString().trim();
        if (val.isEmpty()) { Apassword.setError("Field cannot be empty"); return false; }
        if (!val.matches("^(?=.*[a-zA-Z])(?=\\S+$).{6,}$")) {
            Apassword.setError("Min 6 characters, at least one letter, no spaces");
            return false; }
        Apassword.setError(null);
        return true;
    }

    private boolean validatePhone() {
        String val = Aphone.getText().toString().trim();
        if (val.isEmpty()) { Aphone.setError("Enter a valid phone number"); return false; }
        if (!val.matches("\\A\\w{4,20}\\z")) {
            Aphone.setError("No white spaces allowed"); return false; }
        Aphone.setError(null);
        return true;
    }
}