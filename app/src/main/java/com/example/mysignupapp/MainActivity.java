package com.example.mysignupapp;

import static java.lang.String.valueOf;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView; // ✅ For the eye icon
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    FirebaseDatabase rootNode;
    DatabaseReference reference;

    EditText password, phone;
    boolean passwordVisible = false; // ✅ State variable for password visibility

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        password = findViewById(R.id.password);
        phone = findViewById(R.id.email);
        TextView goto_reg = findViewById(R.id.goto_register);
        Spinner spinner_accounts = findViewById(R.id.spinner_accounts);
        MaterialButton login_btn = findViewById(R.id.loginbtn);
        ImageView togglePassword = findViewById(R.id.togglePasswordVisibility); // ✅ Eye icon

        // ✅ Toggle password visibility
        togglePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (passwordVisible) {
                    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_off_24); // Your eye-off icon
                } else {
                    password.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_24); // Your eye-on icon
                }
                passwordVisible = !passwordVisible;
                password.setSelection(password.getText().length()); // Keep cursor at end
            }
        });

        // Spinner setup
        spinner_accounts.setOnItemSelectedListener(this);
        List<String> categories = new ArrayList<>();
        categories.add("User");
        categories.add("Admin");
        categories.add("Driver");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_accounts.setAdapter(dataAdapter);

        login_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = String.valueOf(spinner_accounts.getSelectedItem());
                if (account.equals("Driver")) {
                    driverLogin();
                } else if (account.equals("Admin")) {
                    AdminLogin();
                } else if (account.equals("User")) {
                    userLogin();
                } else {
                    Toast.makeText(MainActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
                }
            }

            private void driverLogin() {
                if (!validatePhone() | !validatePassword()) {
                    return;
                }
                String userEnteredPhone = phone.getText().toString().trim();
                String userEnteredPass = password.getText().toString().trim();

                DatabaseReference reference = FirebaseDatabase.getInstance().getReference("driver");
                Query checkUser = reference.orderByChild("phone").equalTo(userEnteredPhone);

                checkUser.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String passFromDb = snapshot.child(userEnteredPhone).child("password").getValue(String.class);
                            if (passFromDb.equals(userEnteredPass)) {
                                String usr_name = snapshot.child(userEnteredPhone).child("name").getValue(String.class);
                                String usr_email = snapshot.child(userEnteredPhone).child("email").getValue(String.class);
                                String usr_phone = snapshot.child(userEnteredPhone).child("phone").getValue(String.class);
                                String org_name = snapshot.child(userEnteredPhone).child("org_name").getValue(String.class);

                                Intent intent = new Intent(getApplicationContext(), Drv_Home.class);
                                intent.putExtra("name", usr_name);
                                intent.putExtra("email", usr_email);
                                intent.putExtra("phone", usr_phone);
                                intent.putExtra("hospital", org_name);
                                startActivity(intent);

                                Toast.makeText(MainActivity.this, "Welcome " + usr_name, Toast.LENGTH_SHORT).show();
                            } else {
                                password.setError("Invalid password");
                            }
                        } else {
                            phone.setError("No such driver");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            private void AdminLogin() {
                if (!validatePhone() | !validatePassword()) {
                    return;
                }
                String userEnteredPhone = phone.getText().toString().trim();
                String userEnteredPass = password.getText().toString().trim();

                DatabaseReference reference = FirebaseDatabase.getInstance().getReference("admin");
                Query checkUser = reference.orderByChild("phone").equalTo(userEnteredPhone);

                checkUser.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String passFromDb = snapshot.child(userEnteredPhone).child("password").getValue(String.class);
                            if (passFromDb.equals(userEnteredPass)) {
                                String usr_name = snapshot.child(userEnteredPhone).child("name").getValue(String.class);
                                String usr_email = snapshot.child(userEnteredPhone).child("email").getValue(String.class);
                                String usr_phone = snapshot.child(userEnteredPhone).child("phone").getValue(String.class);
                                String org_name = snapshot.child(userEnteredPhone).child("org_name").getValue(String.class);

                                Intent intent = new Intent(getApplicationContext(), Admin_Home.class);
                                intent.putExtra("name", usr_name);
                                intent.putExtra("email", usr_email);
                                intent.putExtra("phone", usr_phone);
                                intent.putExtra("hospital", org_name);
                                startActivity(intent);

                                Toast.makeText(MainActivity.this, "Welcome " + usr_name, Toast.LENGTH_SHORT).show();
                            } else {
                                password.setError("Invalid password");
                            }
                        } else {
                            phone.setError("No such admin");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            private void userLogin() {
                if (!validatePhone() | !validatePassword()) {
                    return;
                }
                String userEnteredPhone = phone.getText().toString().trim();
                String userEnteredPass = password.getText().toString().trim();

                DatabaseReference reference = FirebaseDatabase.getInstance().getReference("users");
                Query checkUser = reference.orderByChild("phone").equalTo(userEnteredPhone);

                checkUser.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String passFromDb = snapshot.child(userEnteredPhone).child("password").getValue(String.class);
                            if (passFromDb.equals(userEnteredPass)) {
                                String usr_name = snapshot.child(userEnteredPhone).child("name").getValue(String.class);
                                String usr_email = snapshot.child(userEnteredPhone).child("email").getValue(String.class);
                                String usr_phone = snapshot.child(userEnteredPhone).child("phone").getValue(String.class);

                                Intent intent = new Intent(getApplicationContext(), Home.class);
                                intent.putExtra("name", usr_name);
                                intent.putExtra("email", usr_email);
                                intent.putExtra("phone", usr_phone);
                                startActivity(intent);

                                Toast.makeText(MainActivity.this, "Welcome " + usr_name, Toast.LENGTH_SHORT).show();
                            } else {
                                password.setError("Invalid password");
                            }
                        } else {
                            phone.setError("No such user");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        });

        goto_reg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Register.class);
                startActivity(intent);
            }
        });
    }

    private boolean validatePassword() {
        String val = password.getText().toString().trim();
        if (val.isEmpty()) {
            password.setError("Field cannot be empty");
            return false;
        } else {
            password.setError(null);
            return true;
        }
    }

    private boolean validatePhone() {
        String val = phone.getText().toString().trim();
        if (val.isEmpty()) {
            phone.setError("Field cannot be empty");
            return false;
        } else {
            phone.setError(null);
            return true;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String item = adapterView.getItemAtPosition(i).toString();
        Toast.makeText(adapterView.getContext(), "Selected: " + item, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }
}
