package com.example.mysignupapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Firebase Cloud Messaging service.
 *
 * SETUP — add to AndroidManifest.xml inside <application>:
 *
 *   <service
 *       android:name=".AmbulanceMessagingService"
 *       android:exported="false">
 *       <intent-filter>
 *           <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *       </intent-filter>
 *   </service>
 *
 * SENDING NOTIFICATIONS (from server or Firebase Functions):
 *   POST https://fcm.googleapis.com/fcm/send
 *   Body: { "to": "{deviceToken}", "notification": { "title": "...", "body": "..." } }
 *
 * TOKEN STORAGE:
 *   Each time the app starts, the FCM token is saved under:
 *   users/{uid}/fcmToken   or   driver/{phone}/fcmToken
 *   This lets your backend target the right device.
 */
public class AmbulanceMessagingService extends FirebaseMessagingService {

    private static final String TAG        = "AmbulanceFCM";
    private static final String CHANNEL_ID = "ambulance_fcm";

    // ── Called when a message is received while app is in foreground ───────────
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message from: " + remoteMessage.getFrom());

        String title = "Ambulance Alert";
        String body  = "You have a new notification";

        // Notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                    ? remoteMessage.getNotification().getTitle() : title;
            body  = remoteMessage.getNotification().getBody() != null
                    ? remoteMessage.getNotification().getBody() : body;
        }

        // Data payload overrides notification payload
        if (remoteMessage.getData().containsKey("title")) {
            title = remoteMessage.getData().get("title");
        }
        if (remoteMessage.getData().containsKey("body")) {
            body = remoteMessage.getData().get("body");
        }

        showNotification(title, body);
    }

    // ── Called when FCM token is refreshed ────────────────────────────────────
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveTokenToDatabase(token);
    }

    // ── Show the notification ──────────────────────────────────────────────────
    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_ONE_SHOT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, flags);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(sound)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Ambulance Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Emergency request alerts");
            ch.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    // ── Save FCM token to Firebase DB so backend can send targeted push ────────
    public static void saveTokenToDatabase(String token) {
        // Try to save under authenticated user (User / Admin)
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(user.getUid()).child("fcmToken").setValue(token);
            FirebaseDatabase.getInstance().getReference("admin")
                    .child(user.getUid()).child("fcmToken").setValue(token);
        }
        // Driver token is saved separately after driver login — call this from Drv_Home
    }

    /**
     * Call this from Drv_Home.onCreate() after driver logs in.
     * Saves the FCM token under driver/{phone}/fcmToken
     */
    public static void saveDriverToken(String driverPhone) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token != null && !token.isEmpty()) {
                FirebaseDatabase.getInstance().getReference("driver")
                        .child(driverPhone).child("fcmToken").setValue(token);
                Log.d(TAG, "Driver FCM token saved: " + token);
            }
        });
    }

    /**
     * Subscribe driver to topic so admin can broadcast to all drivers.
     * Call from Drv_Home.onCreate()
     */
    public static void subscribeDriverToTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("drivers")
                .addOnSuccessListener(v -> Log.d(TAG, "Subscribed to drivers topic"));
    }
}
