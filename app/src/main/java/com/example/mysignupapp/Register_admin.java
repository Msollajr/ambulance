package com.example.mysignupapp;

import static android.widget.Toast.LENGTH_LONG;

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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class Register_admin extends AppCompatActivity {

    FirebaseDatabase rootNode;
    DatabaseReference reference;

    EditText Apassword, Aphone, Aname, Aemail, Aorg_name;
    TextView goto_log;
    ImageView togglePassword;
    boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_admin);

        Apassword = findViewById(R.id.password);
        Aphone = findViewById(R.id.Phone);
        Aname = findViewById(R.id.username);
        Aemail = findViewById(R.id.email);
        Aorg_name = findViewById(R.id.org_name);
        goto_log = findViewById(R.id.log_here);
        togglePassword = findViewById(R.id.togglePasswordVisibility);

        MaterialButton reg_btn = findViewById(R.id.registerbtn);

        // 🔒 Toggle Password Visibility
        togglePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPasswordVisible) {
                    Apassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_off_24);
                } else {
                    Apassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    togglePassword.setImageResource(R.drawable.visibility_24);
                }
                Apassword.setSelection(Apassword.getText().length());
                isPasswordVisible = !isPasswordVisible;
            }
        });

        reg_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!validatePhone() | !validateUsername() | !validateOrgname() | !validateEmail() | !validatePassword()) {
                    return;
                }

                rootNode = FirebaseDatabase.getInstance();
                reference = rootNode.getReference("admin");

                String name = Aname.getText().toString();
                String email = Aemail.getText().toString();
                String password = Apassword.getText().toString();
                String phone = Aphone.getText().toString();
                String org_name = Aorg_name.getText().toString();

                AdminHelperClass helperClass = new AdminHelperClass(name, email, password, phone, org_name);
                reference.child(phone).setValue(helperClass);

                Toast.makeText(Register_admin.this, "Registered successfully", LENGTH_LONG).show();

                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        goto_log.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            }
        });
    }

    private boolean validateUsername() {
        String val = Aname.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";
        if (val.isEmpty()) {
            Aname.setError("Field cannot be empty");
            return false;
        } else if (val.length() > 20) {
            Aname.setError("Username is too long!");
            return false;
        } else if (!val.matches(checkspaces)) {
            Aname.setError("No white spaces allowed!");
            return false;
        } else {
            Aname.setError(null);
            return true;
        }
    }

    private boolean validateOrgname() {
        String val = Aorg_name.getText().toString().trim();
        if (val.isEmpty()) {
            Aorg_name.setError("Field cannot be empty");
            return false;
        } else if (val.length() > 20) {
            Aorg_name.setError("Organization name is too long!");
            return false;
        } else {
            Aorg_name.setError(null);
            return true;
        }
    }

    private boolean validateEmail() {
        String val = Aemail.getText().toString().trim();
        String checkEmail = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
        if (val.isEmpty()) {
            Aemail.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkEmail)) {
            Aemail.setError("Invalid Email!");
            return false;
        } else {
            Aemail.setError(null);
            return true;
        }
    }

    private boolean validatePassword() {
        String val = Apassword.getText().toString().trim();
        String checkPassword = "^(?=.*[a-zA-Z])(?=\\S+$).{4,}$";
        if (val.isEmpty()) {
            Apassword.setError("Field cannot be empty");
            return false;
        } else if (!val.matches(checkPassword)) {
            Apassword.setError("Password must have 4+ characters, no spaces");
            return false;
        } else {
            Apassword.setError(null);
            return true;
        }
    }

    private boolean validatePhone() {
        String val = Aphone.getText().toString().trim();
        String checkspaces = "\\A\\w{4,20}\\z";

        if (val.isEmpty()) {
            Aphone.setError("Enter a valid phone number");
            return false;
        } else if (!val.matches(checkspaces)) {
            Aphone.setError("No white spaces allowed");
            return false;
        } else if ("exist".equals(isUser())) {
            Aphone.setError("Phone number already exists");
            return false;
        } else {
            Aphone.setError(null);
            return true;
        }
    }

    private String isUser() {
        final String[] phn = new String[1];
        String userEnteredPhone = Aphone.getText().toString().trim();

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("users");
        Query checkUser = reference.orderByChild("phone").equalTo(userEnteredPhone);

        checkUser.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                phn[0] = snapshot.exists() ? "exist" : "not exist";
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        return phn[0];
    }
}
