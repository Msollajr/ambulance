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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Register extends AppCompatActivity {

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText Upassword, Uphone, Uname, Uemail;
    private TextView goto_log, goto_admin;
    private ImageView togglePassword;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth  = FirebaseAuth.getInstance();
        dbRef  = FirebaseDatabase.getInstance().getReference("users");

        Upassword     = findViewById(R.id.password);
        Uphone        = findViewById(R.id.Phone);
        Uname         = findViewById(R.id.username);
        Uemail        = findViewById(R.id.email);
        goto_log      = findViewById(R.id.log_here);
        goto_admin    = findViewById(R.id.goto_admin);
        togglePassword = findViewById(R.id.togglePasswordVisibility);
        MaterialButton reg_btn = findViewById(R.id.registerbtn);

        // ── Password toggle ────────────────────────────────────────────────────
        togglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                Upassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setImageResource(R.drawable.visibility_off_24);
            } else {
                Upassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setImageResource(R.drawable.visibility_24);
            }
            isPasswordVisible = !isPasswordVisible;
            Upassword.setSelection(Upassword.getText().length());
        });

        // ── Register button ────────────────────────────────────────────────────
        reg_btn.setOnClickListener(v -> {
            if (!validateUsername() | !validateEmail()
                    | !validatePassword() | !validatePhone()) return;
            registerUser();
        });

        goto_log.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        goto_admin.setOnClickListener(v ->
                startActivity(new Intent(this, Register_admin.class)));
    }

    // ── Registration flow ─────────────────────────────────────────────────────

    private void registerUser() {
        String name     = Uname.getText().toString().trim();
        String email    = Uemail.getText().toString().trim();
        String password = Upassword.getText().toString().trim();
        String phone    = Uphone.getText().toString().trim();

        // Step 1: Create Firebase Auth account (email + password)
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid = firebaseUser.getUid();

                        // Step 2: Save profile to Realtime DB under UID
                        // We store UID as the key so Auth UID = DB key — they are
                        // permanently linked. Password is NOT stored in the DB;
                        // Firebase Auth handles all authentication securely.
                        UserHelperClass user = new UserHelperClass(name, email, "", phone);
                        dbRef.child(uid).setValue(user)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this,
                                            "Registered successfully! Please login.",
                                            Toast.LENGTH_LONG).show();

                                    // Sign out immediately — user must login through
                                    // the proper login screen
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
                        // Firebase Auth error — e.g. email already exists
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();

                        // Highlight the email field if it's a duplicate
                        if (error != null && error.contains("email")) {
                            Uemail.setError("Email already registered");
                            Uemail.requestFocus();
                        }
                    }
                });
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private boolean validateUsername() {
        String val = Uname.getText().toString().trim();
        if (val.isEmpty()) { Uname.setError("Field cannot be empty"); return false; }
        if (val.length() > 20) { Uname.setError("Username is too long"); return false; }
        if (!val.matches("\\A\\w{4,20}\\z")) {
            Uname.setError("No white spaces allowed"); return false; }
        Uname.setError(null);
        return true;
    }

    private boolean validateEmail() {
        String val = Uemail.getText().toString().trim();
        if (val.isEmpty()) { Uemail.setError("Field cannot be empty"); return false; }
        if (!val.matches("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+")) {
            Uemail.setError("Invalid email address"); return false; }
        Uemail.setError(null);
        return true;
    }

    private boolean validatePassword() {
        String val = Upassword.getText().toString().trim();
        if (val.isEmpty()) { Upassword.setError("Field cannot be empty"); return false; }
        if (!val.matches("^(?=.*[a-zA-Z])(?=\\S+$).{6,}$")) {
            Upassword.setError("Min 6 characters, at least one letter, no spaces");
            return false; }
        Upassword.setError(null);
        return true;
    }

    private boolean validatePhone() {
        String val = Uphone.getText().toString().trim();
        if (val.isEmpty()) { Uphone.setError("Enter a valid phone number"); return false; }
        if (!val.matches("\\A\\w{4,20}\\z")) {
            Uphone.setError("No white spaces allowed"); return false; }
        Uphone.setError(null);
        return true;
    }
}