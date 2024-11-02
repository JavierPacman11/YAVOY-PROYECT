package com.example.ubicacion;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2;

    private static final String PREFS_NAME = "LocationPrefs";
    private static final String KEY_IS_SHARING_LOCATION = "isSharingLocation";

    private boolean isSharingLocation = false;
    private Button toggleButton, signInButton, signOutButton, registerButton;
    private TextView userNameTextView, rutaTextView, empresaTextView;
    private ImageView profilePictureImageView, backgroundImageView;
    private FirebaseAuth mAuth;
    private EditText emailEditText, passwordEditText;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Enlazar vistas
        toggleButton = findViewById(R.id.btn_toggle_location);
        signInButton = findViewById(R.id.btn_sign_in);
        signOutButton = findViewById(R.id.btn_sign_out);
        backgroundImageView = findViewById(R.id.iv_background_image);
        userNameTextView = findViewById(R.id.tv_user_name);
        rutaTextView = findViewById(R.id.txt_ruta);
        empresaTextView = findViewById(R.id.txt_empresa);
        profilePictureImageView = findViewById(R.id.iv_profile_picture);
        emailEditText = findViewById(R.id.et_email);
        passwordEditText = findViewById(R.id.et_password);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Ocultar botones por defecto
        toggleButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.GONE);

        updateUI(currentUser);

        toggleButton.setOnClickListener(v -> {
            if (isLocationEnabled()) {
                permisionButton();
            } else {
                showLocationSettingsDialog();
            }
        });

        signInButton.setOnClickListener(v -> signIn());
        signOutButton.setOnClickListener(v -> signOut());
    }


    private void signIn() {
        String email = emailEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(MainActivity.this, "Ingrese su correo y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                checkAccountStatus(user);

            } else {
                Toast.makeText(MainActivity.this, "Error al iniciar sesión", Toast.LENGTH_SHORT).show();
                updateUI(null);
            }
        });
    }

    private void checkAccountStatus(FirebaseUser user) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("vehiculos").child(user.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Boolean activo = dataSnapshot.child("activo").getValue(Boolean.class);
                    String empresa = dataSnapshot.child("empresa").getValue(String.class);
                    String ruta = dataSnapshot.child("ruta").getValue(String.class);

                    if (activo != null && activo) {
                        loadCompanyAndRoutes(empresa, ruta, user.getUid());
                    } else {
                        Toast.makeText(MainActivity.this, "Cuenta desactivada. Contacte con el administrador.", Toast.LENGTH_SHORT).show();
                        signOut();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Error al verificar estado de la cuenta", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void loadCompanyAndRoutes(String empresa, String ruta, String userId) {
        DatabaseReference routeRef = FirebaseDatabase.getInstance().getReference("rutas")
                .child(empresa)
                .child("rutas")
                .child(ruta)
                .child("vehiculos")
                .child(userId);

        routeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String nombreVehiculo = dataSnapshot.child("nombre").getValue(String.class);
                    Double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = dataSnapshot.child("longitude").getValue(Double.class);

                    // Actualiza la UI con los datos obtenidos
                    userNameTextView.setText(nombreVehiculo);
                    rutaTextView.setText(ruta);
                    empresaTextView.setText(empresa);

                    // Aquí puedes continuar cargando más datos o mostrar el mapa con la ubicación
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Error al cargar los datos de la ruta", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void signOut() {
        stopSharingLocation();
        mAuth.signOut();
        updateUI(null);
        Toast.makeText(MainActivity.this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
    }


    private void updateUI(FirebaseUser user) {
        if (user != null) {
            //userNameTextView.setText(user.getEmail());
            toggleButton.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.GONE);
            emailEditText.setVisibility(View.GONE);
            passwordEditText.setVisibility(View.GONE);
        } else {
            userNameTextView.setText("Inicie Sesión para Comenzar");
            empresaTextView.setText("");
            rutaTextView.setText("");
            toggleButton.setVisibility(View.GONE);
            signOutButton.setVisibility(View.GONE);
            signInButton.setVisibility(View.VISIBLE);
            emailEditText.setVisibility(View.VISIBLE);
            passwordEditText.setVisibility(View.VISIBLE);
        }
    }


    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showLocationSettingsDialog() {
        new AlertDialog.Builder(this).setTitle("Ubicación Desactivada").setMessage("Para compartir tu ubicación, debes activar el servicio de ubicación. ¿Deseas ir a la configuración de ubicación?").setPositiveButton("Ir a Configuración", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        }).setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).create().show();
    }

    private void permisionButton() {
        // Verificar permisos de ubicación
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Solicitar permisos de ubicación si no están concedidos
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permiso de ubicación ya concedido, verificar permisos de notificaciones
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Solicitar permiso para publicar notificaciones
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
                } else {
                    // Permiso de notificaciones concedido, proceder con compartir o detener ubicación
                    funcionButton();
                }
            } else {
                // Android versión menor a Tiramisu (API 33), proceder con compartir o detener ubicación
                funcionButton();
            }
        }
    }

    // Método para manejar los resultados de la solicitud de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso de ubicación concedido, verificar permisos de notificaciones
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        // Solicitar permiso para publicar notificaciones
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
                    } else {
                        // Permiso de notificaciones concedido, proceder con compartir o detener ubicación
                        funcionButton();
                    }
                } else {
                    // Android versión menor a Tiramisu (API 33), proceder con compartir o detener ubicación
                    funcionButton();
                }
            } else {
                // Permiso de ubicación denegado
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // El usuario ha denegado el permiso permanentemente
                    showPermissionDeniedDialog();
                } else {
                    Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso de notificaciones concedido, proceder con compartir o detener ubicación
                funcionButton();
            } else {
                // Permiso de notificaciones denegado
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // El usuario ha denegado el permiso permanentemente
                    showPermissionDeniedDialog();
                } else {
                    Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this).setTitle("Permiso Requerido").setMessage("El permiso necesario para la aplicación ha sido denegado permanentemente. Por favor, habilítalo manualmente en la configuración de la aplicación.").setPositiveButton("Ir a Configuración", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            }
        }).setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).create().show();
    }

    private void funcionButton() {
        // Permiso de notificaciones concedido, procede con compartir o detener ubicación
        if (isSharingLocation) {
            stopSharingLocation();
        } else {
            startSharingLocation();
        }
    }

    private void startSharingLocation() {
        isSharingLocation = true;
        saveLocationState(isSharingLocation);
        toggleButton.setText("Detener envío de ubicación");

        backgroundImageView.setVisibility(View.VISIBLE);
        Animation rotateScaleAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_scale);
        backgroundImageView.startAnimation(rotateScaleAnimation);

        Intent serviceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopSharingLocation() {
        isSharingLocation = false;
        saveLocationState(isSharingLocation);
        toggleButton.setText("Iniciar envío de ubicación");

        Animation rotateScaleAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_and_shrink);
        backgroundImageView.startAnimation(rotateScaleAnimation);

        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
    }

    private void saveLocationState(boolean isSharing) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_SHARING_LOCATION, isSharing);
        editor.apply();
    }

    private boolean getLocationState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_SHARING_LOCATION, false);
    }

    private void updateUIBasedOnLocationState() {
        if (isSharingLocation) {
            toggleButton.setText("Detener envío de ubicación");
            backgroundImageView.setVisibility(View.VISIBLE);
            Animation rotateScaleAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_scale);
            backgroundImageView.startAnimation(rotateScaleAnimation);
        } else {
            toggleButton.setText("Iniciar envío de ubicación");
            backgroundImageView.setVisibility(View.GONE);
        }
    }

}
