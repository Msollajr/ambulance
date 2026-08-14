package com.example.mysignupapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;

/**
 * Profile screen for User, Driver, and Admin roles.
 * Photos upload to Ubuntu server (savannafibre.site/ambulance/api/)
 * via ImageUploader — NOT Firebase Storage or Base64.
 * Only the returned URL is stored in Firebase Realtime DB.
 */
public class SettingFragment extends Fragment {

    private static final String TAG           = "SettingFragment";
    private static final int    RC_PICK_IMAGE = 301;
    private static final int    RC_CAMERA     = 302;
    private static final int    RC_PERMISSION = 303;

    private String role = "user";
    private String name, email, phone, photoUrl;

    // ── Views ──────────────────────────────────────────────────────────────────
    private ImageView      ivAvatar;
    private TextView       tvDisplayName, tvEmail, tvPhone;
    private MaterialButton btnEditName, btnEditPhone, btnChangePassword, btnChangePhoto;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private DatabaseReference profileRef;

    // ── Upload key (UID for user/admin, phone for driver) ──────────────────────
    private String uploadKey;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            name  = getArguments().getString("name",  "");
            email = getArguments().getString("email", "");
            phone = getArguments().getString("phone", "");
            role  = getArguments().getString("role",  "user");
        }

        ivAvatar          = view.findViewById(R.id.iv_avatar);
        tvDisplayName     = view.findViewById(R.id.myName);
        tvEmail           = view.findViewById(R.id.myEmail);
        tvPhone           = view.findViewById(R.id.myPhone);
        btnEditName       = view.findViewById(R.id.btn_edit_name);
        btnEditPhone      = view.findViewById(R.id.btn_edit_phone);
        btnChangePassword = view.findViewById(R.id.btn_change_password);
        btnChangePhoto    = view.findViewById(R.id.btn_change_photo);

        tvEmail.setText(email);
        tvPhone.setText(phone);
        tvDisplayName.setText(name);

        // Determine upload key
        if ("driver".equals(role)) {
            uploadKey = phone;
        } else {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            uploadKey = u != null ? u.getUid() : phone;
        }

        loadProfileFromDB();

        btnChangePhoto.setOnClickListener(v    -> showPhotoSourceDialog());
        btnEditName.setOnClickListener(v       -> showEditDialog("Name",  name,  this::saveName));
        btnEditPhone.setOnClickListener(v      -> showEditDialog("Phone", phone, this::savePhone));
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Drivers use DB password — no Firebase Auth
        if ("driver".equals(role)) {
            btnChangePassword.setVisibility(View.GONE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAD PROFILE FROM DB
    // ══════════════════════════════════════════════════════════════════════════

    private void loadProfileFromDB() {
        if ("driver".equals(role)) {
            profileRef = FirebaseDatabase.getInstance()
                    .getReference("driver").child(phone);
        } else {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) return;
            String node = "admin".equals(role) ? "admin" : "users";
            profileRef = FirebaseDatabase.getInstance()
                    .getReference(node).child(u.getUid());
        }

        profileRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                String dbName  = ds.child("name").getValue(String.class);
                String dbPhone = ds.child("phone").getValue(String.class);
                photoUrl = ds.child("photoUrl").getValue(String.class);

                if (dbName  != null) { name  = dbName;  tvDisplayName.setText(name);  }
                if (dbPhone != null) { phone = dbPhone;  tvPhone.setText(phone);       }

                // Load profile photo via Glide — handles caching, resizing, placeholder
                if (photoUrl != null && !photoUrl.isEmpty() && isAdded()) {
                    Glide.with(requireActivity())
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_person_add)
                            .error(R.drawable.ic_person_add)
                            .circleCrop()
                            .into(ivAvatar);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Log.e(TAG, "loadProfile error: " + e.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHOTO PICKER
    // ══════════════════════════════════════════════════════════════════════════

    private void showPhotoSourceDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Change profile photo")
                .setItems(new String[]{"Take a photo", "Choose from gallery"}, (d, w) -> {
                    if (w == 0) {
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        startActivityForResult(cam, RC_CAMERA);
                    } else {
                        checkStoragePermissionAndPick();
                    }
                }).show();
    }

    private void checkStoragePermissionAndPick() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ActivityCompat.checkSelfPermission(requireActivity(), perm)
                == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{perm}, RC_PERMISSION);
        }
    }

    private void openGallery() {
        Intent pick = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pick, RC_PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == RC_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            Toast.makeText(getActivity(), "Storage permission required",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        Bitmap bmp = null;
        try {
            if (requestCode == RC_PICK_IMAGE && data != null) {
                Uri uri = data.getData();
                bmp = android.provider.MediaStore.Images.Media.getBitmap(
                        requireActivity().getContentResolver(), uri);
            } else if (requestCode == RC_CAMERA && data != null) {
                bmp = (Bitmap) data.getExtras().get("data");
            }
        } catch (IOException e) {
            Toast.makeText(getActivity(), "Could not load image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bmp != null) {
            // Show preview immediately without waiting for upload
            ivAvatar.setImageBitmap(bmp);
            uploadProfilePhoto(bmp);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPLOAD TO UBUNTU SERVER via ImageUploader (not Firebase Storage)
    // ══════════════════════════════════════════════════════════════════════════

    private void uploadProfilePhoto(Bitmap bmp) {
        Toast.makeText(getActivity(), "Uploading photo…", Toast.LENGTH_SHORT).show();

        ImageUploader.uploadProfileFromBitmap(
                bmp,
                uploadKey,   // UID for user/admin, phone number for driver
                role,

                // ── Success: save URL to Firebase DB ──────────────────────────
                url -> {
                    if (!isAdded()) return;
                    photoUrl = url;

                    // Save URL in Firebase — just a small string, not a blob
                    if (profileRef != null) {
                        profileRef.child("photoUrl").setValue(url);
                    }

                    // Reload avatar in Settings screen with Glide
                    Glide.with(requireActivity())
                            .load(url)
                            .circleCrop()
                            .into(ivAvatar);

                    // ── Refresh toolbar avatar in parent activity instantly ────
                    // User/Admin: Home.java | Driver: Drv_Home.java
                    if (getActivity() instanceof Home) {
                        ((Home) getActivity()).refreshProfileAvatar();
                    } else if (getActivity() instanceof Drv_Home) {
                        ((Drv_Home) getActivity()).refreshProfileAvatar();
                    }

                    Toast.makeText(getActivity(), "Photo updated! ✓",
                            Toast.LENGTH_SHORT).show();
                },

                // ── Error ─────────────────────────────────────────────────────
                err -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Upload failed: " + err);
                    Toast.makeText(getActivity(),
                            "Upload failed: " + err, Toast.LENGTH_LONG).show();
                }
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDIT NAME / PHONE
    // ══════════════════════════════════════════════════════════════════════════

    interface SaveAction { void save(String value); }

    private void showEditDialog(String field, String current, SaveAction action) {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setText(current);
        et.setSingleLine(true);
        et.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit " + field)
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String val = et.getText().toString().trim();
                    if (val.isEmpty()) {
                        Toast.makeText(getActivity(), field + " cannot be empty",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    action.save(val);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveName(String newName) {
        if (profileRef == null) return;
        profileRef.child("name").setValue(newName)
                .addOnSuccessListener(v -> {
                    name = newName;
                    tvDisplayName.setText(name);
                    Toast.makeText(getActivity(), "Name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getActivity(),
                                "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void savePhone(String newPhone) {
        if (profileRef == null) return;
        profileRef.child("phone").setValue(newPhone)
                .addOnSuccessListener(v -> {
                    phone = newPhone;
                    tvPhone.setText(phone);
                    Toast.makeText(getActivity(), "Phone updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getActivity(),
                                "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHANGE PASSWORD — User / Admin only (Firebase Auth)
    // ══════════════════════════════════════════════════════════════════════════

    private void showChangePasswordDialog() {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null);
        android.widget.EditText etCur = dv.findViewById(R.id.et_current_password);
        android.widget.EditText etNew = dv.findViewById(R.id.et_new_password);
        android.widget.EditText etCon = dv.findViewById(R.id.et_confirm_password);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change password")
                .setView(dv)
                .setPositiveButton("Change", (d, w) -> {
                    String cur = etCur.getText().toString().trim();
                    String nw  = etNew.getText().toString().trim();
                    String con = etCon.getText().toString().trim();
                    if (cur.isEmpty() || nw.isEmpty() || con.isEmpty()) {
                        Toast.makeText(getActivity(), "All fields required",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!nw.equals(con)) {
                        Toast.makeText(getActivity(), "Passwords do not match",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (nw.length() < 6) {
                        Toast.makeText(getActivity(), "Min 6 characters",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    changePassword(cur, nw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword(String current, String newPw) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        AuthCredential cred = EmailAuthProvider.getCredential(email, current);
        user.reauthenticate(cred)
                .addOnSuccessListener(v ->
                        user.updatePassword(newPw)
                                .addOnSuccessListener(v2 ->
                                        Toast.makeText(getActivity(),
                                                "Password changed! ✓",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(getActivity(),
                                                "Update failed: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getActivity(),
                                "Current password incorrect", Toast.LENGTH_LONG).show());
    }
}