package com.example.mysignupapp;

import static java.lang.String.valueOf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText etLoginField;   // email for User/Admin, phone for Driver
    private EditText etPassword;
    private LinearLayout btnGoogleSignIn;
    private TextView tvGoogleNote;
    private Spinner spinnerAccounts;
    private boolean passwordVisible = false;
    private String selectedRole = "User";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        // Google Sign-In config
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // ── Bind views ──────────────────────────────────────────────────────────
        etLoginField    = findViewById(R.id.email);      // reused view — shows email or phone
        etPassword      = findViewById(R.id.password);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
        tvGoogleNote    = findViewById(R.id.tv_google_note);
        spinnerAccounts = findViewById(R.id.spinner_accounts);

        TextView gotoReg        = findViewById(R.id.goto_register);
        MaterialButton loginBtn = findViewById(R.id.loginbtn);
        ImageView togglePwIcon  = findViewById(R.id.togglePasswordVisibility);

        // ── Password toggle ─────────────────────────────────────────────────────
        togglePwIcon.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePwIcon.setImageResource(R.drawable.visibility_off_24);
            } else {
                etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePwIcon.setImageResource(R.drawable.visibility_24);
            }
            passwordVisible = !passwordVisible;
            etPassword.setSelection(etPassword.getText().length());
        });

        // ── Role spinner ─────────────────────────────────────────────────────────
        spinnerAccounts.setOnItemSelectedListener(this);
        List<String> roles = new ArrayList<>();
        roles.add("User");
        roles.add("Admin");
        roles.add("Driver");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccounts.setAdapter(adapter);

        // ── Login button ─────────────────────────────────────────────────────────
        loginBtn.setOnClickListener(v -> {
            switch (selectedRole) {
                case "Driver": driverLogin();  break;
                case "Admin":  adminLogin();   break;
                default:       userLogin();    break;
            }
        });

        btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        gotoReg.setOnClickListener(v -> startActivity(new Intent(this, Register.class)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER LOGIN — Firebase Auth (email + password)
    // Realtime DB profile is read using the UID returned by Auth
    // ══════════════════════════════════════════════════════════════════════════

    private void userLogin() {
        if (!validateField() | !validatePassword()) return;

        String email    = etLoginField.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid = firebaseUser.getUid();

                        // Load profile from Realtime DB using the Auth UID as key
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(uid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if (!snapshot.exists()) {
                                            Toast.makeText(MainActivity.this,
                                                    "Profile not found. Please register.",
                                                    Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                            return;
                                        }
                                        String name  = snapshot.child("name").getValue(String.class);
                                        String em    = snapshot.child("email").getValue(String.class);
                                        String phone = snapshot.child("phone").getValue(String.class);

                                        Toast.makeText(MainActivity.this,
                                                "Welcome " + name, Toast.LENGTH_SHORT).show();

                                        Intent intent = new Intent(getApplicationContext(), Home.class);
                                        intent.putExtra("name",  name);
                                        intent.putExtra("email", em);
                                        intent.putExtra("phone", phone);
                                        startActivity(intent);
                                        finish();
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError e) {
                                        Toast.makeText(MainActivity.this,
                                                "DB error: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage() : "Login failed";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        etPassword.setError("Email or password incorrect");
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN LOGIN — Firebase Auth (email + password)
    // Same pattern as user — Auth authenticates, DB provides profile data
    // ══════════════════════════════════════════════════════════════════════════

    private void adminLogin() {
        if (!validateField() | !validatePassword()) return;

        String email    = etLoginField.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid = firebaseUser.getUid();

                        // Load admin profile from Realtime DB using Auth UID
                        FirebaseDatabase.getInstance().getReference("admin")
                                .child(uid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if (!snapshot.exists()) {
                                            // Auth succeeded but no admin profile — wrong role
                                            Toast.makeText(MainActivity.this,
                                                    "No admin account found for this email. " +
                                                            "Check your role selection.",
                                                    Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                            return;
                                        }
                                        String name    = snapshot.child("name").getValue(String.class);
                                        String em      = snapshot.child("email").getValue(String.class);
                                        String phone   = snapshot.child("phone").getValue(String.class);
                                        String orgName = snapshot.child("org_name").getValue(String.class);

                                        Toast.makeText(MainActivity.this,
                                                "Welcome " + name, Toast.LENGTH_SHORT).show();

                                        Intent intent = new Intent(getApplicationContext(), Admin_Home.class);
                                        intent.putExtra("name",     name);
                                        intent.putExtra("email",    em);
                                        intent.putExtra("phone",    phone);
                                        intent.putExtra("hospital", orgName);
                                        startActivity(intent);
                                        finish();
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError e) {}
                                });
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage() : "Login failed";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        etPassword.setError("Email or password incorrect");
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRIVER LOGIN — Realtime DB only (phone + password)
    // Drivers are registered by admin and don't use Firebase Auth email login.
    // Their account key is their phone number in the "driver" node.
    // ══════════════════════════════════════════════════════════════════════════

    private void driverLogin() {
        if (!validateField() | !validatePassword()) return;

        String enteredPhone = etLoginField.getText().toString().trim();
        String enteredPass  = etPassword.getText().toString().trim();

        // Drivers are stored under driver/{phone} as the key — query directly
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("driver").child(enteredPhone);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    etLoginField.setError("No driver found with this phone");
                    etLoginField.requestFocus();
                    return;
                }

                String passFromDb = snapshot.child("password").getValue(String.class);

                if (passFromDb != null && passFromDb.equals(enteredPass)) {
                    String name    = snapshot.child("name").getValue(String.class);
                    String email   = snapshot.child("email").getValue(String.class);
                    String phone   = snapshot.child("phone").getValue(String.class);
                    String orgName = snapshot.child("org_name").getValue(String.class);

                    // If phone not stored, use the key we queried with
                    if (phone == null) phone = enteredPhone;

                    Toast.makeText(MainActivity.this, "Welcome " + name,
                            Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(getApplicationContext(), Drv_Home.class);
                    intent.putExtra("name",     name);
                    intent.putExtra("email",    email != null ? email : "");
                    intent.putExtra("phone",    phone);
                    intent.putExtra("hospital", orgName != null ? orgName : "");
                    startActivity(intent);
                    finish();
                } else {
                    etPassword.setError("Incorrect password");
                    etPassword.requestFocus();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this,
                        "Login error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GOOGLE SIGN-IN — unchanged, already uses Firebase Auth + UID in DB
    // ══════════════════════════════════════════════════════════════════════════

    private void launchGoogleSignIn() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken(), account);
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken, GoogleSignInAccount googleAccount) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid   = firebaseUser.getUid();
                        String name  = googleAccount.getDisplayName() != null
                                ? googleAccount.getDisplayName() : "Google User";
                        String email = googleAccount.getEmail() != null
                                ? googleAccount.getEmail() : "";

                        DatabaseReference userRef = FirebaseDatabase.getInstance()
                                .getReference("users").child(uid);

                        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!snapshot.exists()) {
                                    // First Google login — create profile (phone = uid placeholder)
                                    UserHelperClass newUser =
                                            new UserHelperClass(name, email, "", uid);
                                    userRef.setValue(newUser);
                                }
                                String displayName  = snapshot.exists()
                                        ? snapshot.child("name").getValue(String.class)  : name;
                                String displayEmail = snapshot.exists()
                                        ? snapshot.child("email").getValue(String.class) : email;
                                String displayPhone = snapshot.exists()
                                        ? snapshot.child("phone").getValue(String.class) : uid;

                                Toast.makeText(MainActivity.this,
                                        "Welcome " + displayName, Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(getApplicationContext(), Home.class);
                                intent.putExtra("name",  displayName  != null ? displayName  : name);
                                intent.putExtra("email", displayEmail != null ? displayEmail : email);
                                intent.putExtra("phone", displayPhone != null ? displayPhone : uid);
                                startActivity(intent);
                                finish();
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    } else {
                        Toast.makeText(this, "Firebase auth failed: " +
                                        (task.getException() != null
                                                ? task.getException().getMessage() : "unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SPINNER — update hint text and Google button visibility by role
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        selectedRole = parent.getItemAtPosition(position).toString();
        boolean isUser  = selectedRole.equals("User");
        boolean isDriver = selectedRole.equals("Driver");

        // Show Google button only for User role
        btnGoogleSignIn.setVisibility(isUser ? View.VISIBLE : View.GONE);
        tvGoogleNote.setVisibility(isUser ? View.VISIBLE : View.GONE);

        // Update the login field hint:
        // User → Email, Admin → Email, Driver → Phone
        etLoginField.setHint(isDriver ? "Phone number" : "Email address");
        etLoginField.setInputType(isDriver
                ? InputType.TYPE_CLASS_PHONE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    // ── Validation ─────────────────────────────────────────────────────────────

    private boolean validateField() {
        String val = etLoginField.getText().toString().trim();
        if (val.isEmpty()) {
            etLoginField.setError("Field cannot be empty");
            return false;
        }
        etLoginField.setError(null);
        return true;
    }

    private boolean validatePassword() {
        String val = etPassword.getText().toString().trim();
        if (val.isEmpty()) {
            etPassword.setError("Field cannot be empty");
            return false;
        }
        etPassword.setError(null);
        return true;
    }
}