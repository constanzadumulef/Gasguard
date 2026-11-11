package com.company.product;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GasGuard";
    private static final String AWS_SERVER_URI = "ssl://a3nfanbzxilxsm-ats.iot.us-east-1.amazonaws.com:8883";
    private static final String PREFS_APP = "gasguard_local";
    private static final String PREF_CLIENT_ID = "mqtt_client_id";
    private static final String PREF_ULTIMA_LECTURA = "ultima_lectura";
    private static final String PREF_HISTORIAL_LECTURAS = "historial_lecturas";
    private static final String PREF_FECHA_LECTURA = "ultima_lectura_fecha";
    private static final String PREF_MODO_AUSENTE = "modo_ausente";
    private static final String PREF_MODO_DISPOSITIVO_PENDIENTE = "modo_dispositivo_pendiente";
    private static final String PREF_MODO_NO_CONFIRMADO = "modo_no_confirmado";
    private static final String PREF_CIERRE_PENDIENTE = "cierre_ausente_pendiente";
    private static final int UMBRAL_AVISO_ADC = 1800;
    private static final int UMBRAL_CRITICO_ADC = 3000;
    private static final long TIMEOUT_CONFIRMACION_MODO_MS = 15000L;
    private static final int REQUEST_AWS_CERTIFICATE = 7401;
    private static final int REQUEST_AWS_PRIVATE_KEY = 7402;

    private FirebaseAuth firebaseAuth;
    private CredentialManager credentialManager;
    private MqttAsyncClient mqttClient;
    private TextView tvEstadoConexion;
    private TextView tvEstadoGas;
    private TextView tvLecturas;
    private TextView tvHoraLectura;
    private TextView tvModoEstado;
    private TextView tvModoAyuda;
    private TextView tvEstadoValvula;
    private TextView tvUsuario;
    private TextView tvRangosGas;
    private ProgressBar progressGas;
    private Button btnAbrirValvula;
    private Button btnConfigurarCertificado;
    private Button btnModoAusente;
    private ImageView ivModoCandado;
    private byte[] certificadoPendiente;
    private boolean valvulaAbiertaReportada;
    private boolean gasElevado;
    private boolean gasLecturaEnVivo;
    private final Handler modoUiHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutConfirmacionModo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(getBaseContext());
        configurarPantallaLogin();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null && tvEstadoConexion == null) {
            mostrarPanelControl(currentUser);
        }
    }

    private void configurarPantallaLogin() {
        tvEstadoConexion = null;
        tvEstadoGas = null;
        tvLecturas = null;
        tvHoraLectura = null;
        tvModoEstado = null;
        tvModoAyuda = null;
        tvEstadoValvula = null;
        tvUsuario = null;
        btnAbrirValvula = null;
        btnModoAusente = null;
        ivModoCandado = null;
        setContentView(R.layout.activity_main);
        Button btnGoogle = findViewById(R.id.btnGoogle);
        Button btnEmail = findViewById(R.id.btnEmail);
        Button btnRegistro = findViewById(R.id.btnRegistro);
        Button btnRecuperar = findViewById(R.id.btnRecuperar);
        EditText etCorreo = findViewById(R.id.etCorreo);
        EditText etContrasena = findViewById(R.id.etContrasena);
        TextView tvMensaje = findViewById(R.id.tvMensaje);
        btnGoogle.setOnClickListener(v -> iniciarSesionConGoogle(tvMensaje, btnGoogle));
        btnEmail.setOnClickListener(v -> iniciarSesionConCorreo(etCorreo, etContrasena, tvMensaje, btnEmail));
        btnRegistro.setOnClickListener(v -> registrarConCorreo(etCorreo, etContrasena, tvMensaje, btnRegistro));
        btnRecuperar.setOnClickListener(v -> recuperarContrasena(etCorreo, tvMensaje));
    }

    private void iniciarSesionConCorreo(EditText etCorreo, EditText etContrasena, TextView tvMensaje, Button boton) {
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();
        if (!validarCorreoYContrasena(correo, contrasena, tvMensaje)) return;
        boton.setEnabled(false);
        mostrarMensaje(tvMensaje, "Verificando tu cuenta…", false);
        firebaseAuth.signInWithEmailAndPassword(correo, contrasena).addOnCompleteListener(this, task -> {
            boton.setEnabled(true);
            if (task.isSuccessful()) {
                mostrarPanelControl(firebaseAuth.getCurrentUser());
            } else {
                Log.e(TAG, "Falló el ingreso con correo", task.getException());
                mostrarMensaje(tvMensaje, mensajeAuth(task.getException()), true);
            }
        });
    }

    private void registrarConCorreo(EditText etCorreo, EditText etContrasena, TextView tvMensaje, Button boton) {
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();
        if (!validarCorreoYContrasena(correo, contrasena, tvMensaje)) return;
        if (contrasena.length() < 6) {
            mostrarMensaje(tvMensaje, "La contraseña debe tener al menos 6 caracteres.", true);
            return;
        }
        boton.setEnabled(false);
        mostrarMensaje(tvMensaje, "Creando tu cuenta…", false);
        firebaseAuth.createUserWithEmailAndPassword(correo, contrasena).addOnCompleteListener(this, task -> {
            boton.setEnabled(true);
            if (task.isSuccessful()) {
                mostrarPanelControl(firebaseAuth.getCurrentUser());
            } else {
                Log.e(TAG, "Falló el registro con correo", task.getException());
                mostrarMensaje(tvMensaje, mensajeAuth(task.getException()), true);
            }
        });
    }

    private boolean validarCorreoYContrasena(String correo, String contrasena, TextView tvMensaje) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mostrarMensaje(tvMensaje, "Escribe un correo válido.", true);
            return false;
        }
        if (contrasena.isEmpty()) {
            mostrarMensaje(tvMensaje, "Escribe tu contraseña.", true);
            return false;
        }
        return true;
    }

    private void recuperarContrasena(EditText etCorreo, TextView tvMensaje) {
        String correo = etCorreo.getText().toString().trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mostrarMensaje(tvMensaje, "Escribe primero tu correo para enviarte el enlace.", true);
            return;
        }
        mostrarMensaje(tvMensaje, "Enviando enlace de recuperación…", false);
        firebaseAuth.sendPasswordResetEmail(correo).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                mostrarMensaje(tvMensaje, "Revisa tu correo: enviamos el enlace de recuperación.", false);
            } else {
                Log.e(TAG, "Falló la recuperación de contraseña", task.getException());
                mostrarMensaje(tvMensaje, mensajeAuth(task.getException()), true);
            }
        });
    }

    private String mensajeAuth(Exception error) {
        if (error instanceof com.google.firebase.auth.FirebaseAuthException) {
            String codigo = ((com.google.firebase.auth.FirebaseAuthException) error).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(codigo)
                    || "ERROR_PROVIDER_DISABLED".equals(codigo)) {
                return "Activa el proveedor Correo/contraseña en Firebase Console > Authentication > Proveedores de inicio de sesión.";
            }
            if ("ERROR_WEAK_PASSWORD".equals(codigo)) {
                return "La contraseña debe tener al menos 6 caracteres.";
            }
        }
        if (error instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException
                || error instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            return "Correo o contraseña incorrectos. Si aún no tienes cuenta, selecciona Crear cuenta.";
        }
        if (error instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            return "Ese correo ya tiene una cuenta. Prueba Ingresar o recuperar la contraseña.";
        }
        return "No se pudo completar la solicitud. Comprueba tu conexión e inténtalo otra vez.";
    }

    private void mostrarMensaje(TextView vista, String mensaje, boolean error) {
        vista.setText(mensaje);
        vista.setVisibility(android.view.View.VISIBLE);
        vista.setTextColor(ContextCompat.getColor(this, error ? R.color.status_error : R.color.brand_primary));
    }

    private void iniciarSesionConGoogle(TextView tvMensaje, Button btnGoogle) {
        btnGoogle.setEnabled(false);
        tvMensaje.setText("Selecciona tu cuenta de Google…");

        GetSignInWithGoogleOption googleSignInOption =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id))
                        .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleSignInOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        procesarCredencialGoogle(result.getCredential(), tvMensaje, btnGoogle);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "No se pudo seleccionar la cuenta de Google ("
                                + e.getClass().getSimpleName() + ")", e);
                        String mensaje = "No se pudo abrir el acceso de Google. Comprueba que el dispositivo tenga Google Play services actualizado.";
                        if ("GetCredentialCancellationException".equals(e.getClass().getSimpleName())) {
                            mensaje = "Se canceló el acceso con Google.";
                        } else if ("NoCredentialException".equals(e.getClass().getSimpleName())) {
                            mensaje = "No hay una cuenta de Google disponible en este dispositivo. Agrega una cuenta e inténtalo de nuevo.";
                        }
                        mostrarMensaje(tvMensaje, mensaje, true);
                        btnGoogle.setEnabled(true);
                    }
                }
        );
    }

    private void procesarCredencialGoogle(Credential credential, TextView tvMensaje, Button btnGoogle) {
        if (!(credential instanceof CustomCredential)
                || !TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            mostrarMensaje(tvMensaje, "La cuenta seleccionada no entregó una credencial de Google.", true);
            btnGoogle.setEnabled(true);
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);

            firebaseAuth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                btnGoogle.setEnabled(true);
                if (task.isSuccessful()) {
                    mostrarPanelControl(firebaseAuth.getCurrentUser());
                } else {
                    Log.e(TAG, "Firebase rechazó el inicio de sesión", task.getException());
                    mostrarMensaje(tvMensaje, "Firebase no pudo validar la cuenta. Revisa la configuración del proyecto.", true);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "No se pudo leer la credencial de Google", e);
            mostrarMensaje(tvMensaje, "No se pudo leer la cuenta de Google. Intenta nuevamente.", true);
            btnGoogle.setEnabled(true);
        }
    }

    private void mostrarPanelControl(FirebaseUser user) {
        setContentView(R.layout.activity_control);

        tvEstadoConexion = findViewById(R.id.tvEstadoConexion);
        tvEstadoGas = findViewById(R.id.tvEstadoGas);
        tvLecturas = findViewById(R.id.tvLecturas);
        tvHoraLectura = findViewById(R.id.tvHoraLectura);
        tvModoEstado = findViewById(R.id.tvModoEstado);
        tvModoAyuda = findViewById(R.id.tvModoAyuda);
        tvEstadoValvula = findViewById(R.id.tvEstadoValvula);
        tvUsuario = findViewById(R.id.tvUsuario);
        tvRangosGas = findViewById(R.id.tvRangosGas);
        progressGas = findViewById(R.id.progressGas);
        mostrarNivelGas("Esperando lectura", R.color.text_secondary, 0);
        btnAbrirValvula = findViewById(R.id.btnAbrir);
        valvulaAbiertaReportada = false;
        gasLecturaEnVivo = false;
        actualizarAccionValvula();
        btnConfigurarCertificado = findViewById(R.id.btnConfigurarCertificado);
        btnModoAusente = findViewById(R.id.btnModoAusente);
        ivModoCandado = findViewById(R.id.ivModoCandado);
        Button btnHistorial = findViewById(R.id.btnHistorial);
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        tvUsuario.setText(user != null && user.getEmail() != null
                ? "Sesión: " + user.getEmail()
                : "Sesión iniciada con Google");

        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String ultimaLectura = prefs.getString(PREF_ULTIMA_LECTURA, null);
        long fechaLectura = prefs.getLong(PREF_FECHA_LECTURA, 0L);
        if (ultimaLectura == null) {
            tvEstadoGas.setText("Esperando lectura del sensor");
            tvLecturas.setText("—");
            tvHoraLectura.setText("Los rangos mostrados son de demostración, no ppm calibrados.");
        } else {
            mostrarLectura(ultimaLectura, fechaLectura, false);
        }
        actualizarModoHogar();

        if (AwsMqttCredentials.isConfigured(this)) {
            btnConfigurarCertificado.setVisibility(android.view.View.GONE);
            actualizarEstadoConexion("Conectando…", false);
            inicializarMqtt();
        } else {
            btnConfigurarCertificado.setText("Conectar dispositivo");
            btnConfigurarCertificado.setOnClickListener(v -> seleccionarArchivoAws(REQUEST_AWS_CERTIFICATE));
            actualizarEstadoConexion("Vincula el dispositivo para comenzar.", false);
        }

        btnAbrirValvula.setOnClickListener(v -> solicitarAperturaValvula());
        btnModoAusente.setOnClickListener(v -> cambiarModoHogar());
        btnHistorial.setOnClickListener(v -> mostrarHistorialLocal());
        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }

    private void guardarLecturaLocal(String payload, long timestamp) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        JSONArray actual;
        try {
            actual = new JSONArray(prefs.getString(PREF_HISTORIAL_LECTURAS, "[]"));
        } catch (Exception e) {
            actual = new JSONArray();
        }
        JSONArray actualizado = new JSONArray();
        int inicio = Math.max(0, actual.length() - 39);
        for (int i = inicio; i < actual.length(); i++) actualizado.put(actual.opt(i));
        JSONObject entrada = new JSONObject();
        try {
            entrada.put("payload", payload);
            entrada.put("timestamp", timestamp);
            actualizado.put(entrada);
            prefs.edit().putString(PREF_HISTORIAL_LECTURAS, actualizado.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo guardar la lectura localmente", e);
        }
    }

    private void mostrarHistorialLocal() {
        JSONArray historial;
        try {
            historial = new JSONArray(getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .getString(PREF_HISTORIAL_LECTURAS, "[]"));
        } catch (Exception e) {
            historial = new JSONArray();
        }
        if (historial.length() == 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Historial local")
                    .setMessage("Aún no hay lecturas guardadas.")
                    .setPositiveButton("Cerrar", null)
                    .show();
            return;
        }

        StringBuilder texto = new StringBuilder();
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM HH:mm:ss", new Locale("es", "CL"));
        for (int i = historial.length() - 1; i >= 0; i--) {
            JSONObject entrada = historial.optJSONObject(i);
            if (entrada == null) continue;
            long fecha = entrada.optLong("timestamp", 0L);
            String payload = entrada.optString("payload", "");
            try {
                JSONObject lectura = new JSONObject(payload);
                texto.append(formato.format(new Date(fecha)))
                        .append("  ·  ")
                        .append(lectura.optString("gas", "sin dato"))
                        .append(" ADC\n");
            } catch (Exception e) {
                texto.append(formato.format(new Date(fecha))).append("  ·  ").append(payload).append('\n');
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Historial local · últimas " + historial.length() + " lecturas")
                .setMessage(texto.toString().trim())
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void mostrarLectura(String payload, long timestamp, boolean enVivo) {
        if (tvEstadoGas == null || tvLecturas == null || tvHoraLectura == null) return;
        if (enVivo) gasLecturaEnVivo = true;
        try {
            JSONObject lectura = new JSONObject(payload);
            if (!lectura.has("gas") || lectura.isNull("gas")) {
                tvEstadoGas.setText("Mensaje recibido sin dato de gas");
                tvLecturas.setText(payload);
                tvHoraLectura.setText(textoHoraLectura(timestamp, enVivo));
                return;
            }

            int gas = lectura.optInt("gas", -1);
            if (gas >= 0) {
                gas = Math.max(0, Math.min(4095, gas));
                tvLecturas.setText(String.format(Locale.getDefault(), "%,d / 4.095", gas));
                progressGas.setProgress(gas);
            } else {
                tvLecturas.setText(lectura.optString("gas"));
            }

            String estado = lectura.optString("estado", lectura.optString("gas_status", ""))
                    .trim().toLowerCase(Locale.ROOT);
            if (estado.isEmpty() && gas >= 0) {
                estado = gas >= UMBRAL_CRITICO_ADC ? "critico"
                        : gas >= UMBRAL_AVISO_ADC ? "alerta" : "normal";
            }
            if (estado.contains("crit") || estado.contains("pelig") || estado.contains("danger")) {
                mostrarIconoAlerta(true);
                mostrarNivelGas("Alerta por posible fuga de gas", R.color.status_error, gas);
                mostrarEstadoValvula("Nivel crítico. Se solicitó el cierre automático de la válvula.", R.color.status_warning, false);
                if (enVivo) enviarComandoValvula("cerrar");
            } else if (estado.contains("alert") || estado.contains("warn")) {
                mostrarIconoAlerta(false);
                mostrarNivelGas("Atención · nivel elevado", R.color.status_warning, gas);
            } else if (estado.contains("normal") || estado.contains("segur") || "ok".equals(estado)) {
                mostrarIconoAlerta(false);
                mostrarNivelGas("Normal · sin nivel elevado", R.color.status_ok, gas);
            } else {
                mostrarIconoAlerta(false);
                mostrarNivelGas("Lectura recibida · sin clasificar", R.color.text_secondary, gas);
            }

            String estadoValvula = lectura.optString("valvula", "").trim();
            if (!estadoValvula.isEmpty()) {
                String estadoValvulaNormalizado = estadoValvula.toLowerCase(Locale.ROOT);
                if (estadoValvulaNormalizado.contains("cerr") || estadoValvulaNormalizado.contains("closed")) {
                    valvulaAbiertaReportada = false;
                    mostrarEstadoValvula("Válvula cerrada · confirmada", R.color.status_ok, true);
                } else if (estadoValvulaNormalizado.contains("abier") || estadoValvulaNormalizado.contains("open")) {
                    valvulaAbiertaReportada = true;
                    mostrarEstadoValvula("Válvula abierta · confirmada", R.color.status_ok, true);
                }
                actualizarAccionValvula();
                if (estadoValvulaNormalizado.contains("cerr") || estadoValvulaNormalizado.contains("closed")) {
                    getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                            .putBoolean(PREF_CIERRE_PENDIENTE, false).apply();
                    actualizarModoHogar();
                }
            }
            String modoDispositivo = lectura.optString("modo", "").trim().toLowerCase(Locale.ROOT);
            if (!modoDispositivo.isEmpty()) {
                boolean modoRemotoAusente = "ausente".equals(modoDispositivo);
                boolean modoLocalAusente = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                        .getBoolean(PREF_MODO_AUSENTE, false);
                if (modoRemotoAusente == modoLocalAusente) {
                    getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                            .putBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, false)
                            .putBoolean(PREF_MODO_NO_CONFIRMADO, false).apply();
                    cancelarTimeoutConfirmacionModo();
                    actualizarModoHogar();
                }
            }
            tvHoraLectura.setText(textoHoraLectura(timestamp, enVivo)
                    + " · escala de demostración; no equivale a ppm calibrados");
        } catch (Exception e) {
            tvEstadoGas.setText("Lectura sin formato reconocido");
            tvLecturas.setText(payload);
            tvHoraLectura.setText(textoHoraLectura(timestamp, enVivo));
        }
    }

    private void mostrarNivelGas(String estado, int colorRes, int gas) {
        int color = ContextCompat.getColor(this, colorRes);
        gasElevado = colorRes != R.color.status_ok;
        tvEstadoGas.setText("● " + estado);
        tvEstadoGas.setTextColor(color);
        if (progressGas != null) progressGas.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        if (tvRangosGas == null) return;

        String[] lineas = {
                "● Normal: 0–1.799",
                "● Atención: 1.800–2.999",
                "● Riesgo: 3.000–4.095"
        };
        int[] colores = {R.color.status_ok, R.color.status_warning, R.color.status_error};
        SpannableStringBuilder texto = new SpannableStringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            int inicio = texto.length();
            texto.append(lineas[i]);
            texto.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, colores[i])),
                    inicio, inicio + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < lineas.length - 1) texto.append('\n');
        }
        tvRangosGas.setText(texto);
    }

    private void mostrarIconoAlerta(boolean mostrar) {
        if (tvEstadoGas == null) return;
        Drawable icono = mostrar ? ContextCompat.getDrawable(this, R.drawable.ic_gas_alert) : null;
        if (icono == null) {
            tvEstadoGas.setCompoundDrawables(null, null, null, null);
            return;
        }
        int tamano = (int) (24 * getResources().getDisplayMetrics().density);
        icono.setBounds(0, 0, tamano, tamano);
        tvEstadoGas.setCompoundDrawables(icono, null, null, null);
        tvEstadoGas.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
    }

    private void mostrarEstadoValvula(String texto, int colorRes, boolean confirmado) {
        if (tvEstadoValvula == null) return;
        tvEstadoValvula.setText(texto);
        tvEstadoValvula.setTextColor(ContextCompat.getColor(this, colorRes));
        Drawable icono = confirmado ? ContextCompat.getDrawable(this, R.drawable.ic_status_confirmed) : null;
        if (icono != null) {
            int tamano = (int) (22 * getResources().getDisplayMetrics().density);
            icono.setBounds(0, 0, tamano, tamano);
            tvEstadoValvula.setCompoundDrawables(null, null, icono, null);
            tvEstadoValvula.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
        } else {
            tvEstadoValvula.setCompoundDrawables(null, null, null, null);
        }
    }

    private String textoHoraLectura(long timestamp, boolean enVivo) {
        if (timestamp <= 0L) return "Sin hora registrada";
        String hora = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("es", "CL"))
                .format(new Date(timestamp));
        return enVivo ? "Actualizada " + hora : "Guardada " + hora + " · puede estar desactualizada";
    }

    private void actualizarModoHogar() {
        if (tvModoEstado == null || btnModoAusente == null || ivModoCandado == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        boolean ausente = prefs.getBoolean(PREF_MODO_AUSENTE, false);
        boolean pendiente = prefs.getBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, false);
        boolean sinConfirmacion = prefs.getBoolean(PREF_MODO_NO_CONFIRMADO, false);
        if (ausente) {
            tvModoEstado.setText(sinConfirmacion ? "Protección no confirmada"
                    : pendiente ? "Protección solicitada" : "Modo Ausente · protección activa");
            tvModoEstado.setTextColor(ContextCompat.getColor(this,
                    pendiente && !sinConfirmacion ? R.color.status_warning : R.color.status_error));
            btnModoAusente.setText("Volver a En casa");
            ivModoCandado.setImageResource(R.drawable.ic_lock_closed);
        } else {
            tvModoEstado.setText("En casa · activa");
            tvModoEstado.setTextColor(ContextCompat.getColor(this, R.color.status_ok));
            btnModoAusente.setText("Activar protección al salir");
            ivModoCandado.setImageResource(R.drawable.ic_lock_open);
        }
        if (tvModoAyuda != null) {
            if (sinConfirmacion) {
                tvModoAyuda.setText("No se pudo confirmar la protección. Comprueba la conexión del dispositivo e inténtalo de nuevo.");
                tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.status_error));
            } else if (pendiente) {
                tvModoAyuda.setText("Comprobando el cambio por unos segundos…");
                tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
            } else {
                tvModoAyuda.setText("Al salir, se solicita el cierre. Al volver, la válvula no se abre automáticamente.");
                tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }

    private void cambiarModoHogar() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        boolean ausente = !prefs.getBoolean(PREF_MODO_AUSENTE, false);
        cancelarTimeoutConfirmacionModo();
        prefs.edit().putBoolean(PREF_MODO_AUSENTE, ausente)
                .putBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, true)
                .putBoolean(PREF_MODO_NO_CONFIRMADO, false)
                .putBoolean(PREF_CIERRE_PENDIENTE, false).apply();
        actualizarModoHogar();

        if (ausente) {
            if (mqttClient != null && mqttClient.isConnected()) {
                enviarModoDispositivo(true);
            } else if (tvEstadoValvula != null) {
                mostrarEstadoValvula("Sin conexión. La protección al salir se enviará al reconectar.", R.color.status_warning, false);
                if (tvModoAyuda != null) {
                    tvModoAyuda.setText("Sin conexión. Se enviará al reconectar.");
                    tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
                }
            }
        } else {
            if (mqttClient != null && mqttClient.isConnected()) enviarModoDispositivo(false);
            if (tvEstadoValvula != null) {
                mostrarEstadoValvula("En casa. La válvula conserva su estado actual.", R.color.text_secondary, false);
            }
            if (mqttClient == null || !mqttClient.isConnected()) {
                if (tvModoAyuda != null) {
                    tvModoAyuda.setText("Sin conexión. Se actualizará al reconectar.");
                    tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
                }
            }
        }
    }

    private void sincronizarModoAusente() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (mqttClient == null || !mqttClient.isConnected()) return;
        if (prefs.getBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, false)) {
            enviarModoDispositivo(prefs.getBoolean(PREF_MODO_AUSENTE, false));
        }
        if (prefs.getBoolean(PREF_CIERRE_PENDIENTE, false)) enviarComandoValvula("cerrar");
    }

    private void enviarModoDispositivo(boolean ausente) {
        if (mqttClient == null || !mqttClient.isConnected()) return;
        programarTimeoutConfirmacionModo();
        String payload = ausente ? "{\"accion\":\"ausente\"}" : "{\"accion\":\"casa\"}";
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            mqttClient.publish("gasguard/gas", message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    runOnUiThread(() -> {
                        actualizarModoHogar();
                        if (tvEstadoValvula != null && ausente) {
                            mostrarEstadoValvula("Protección al salir solicitada. Comprobando respuesta.", R.color.status_warning, false);
                        }
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "No se pudo sincronizar el modo del hogar", exception);
                    runOnUiThread(() -> {
                        cancelarTimeoutConfirmacionModo();
                        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                                .putBoolean(PREF_MODO_NO_CONFIRMADO, true).apply();
                        actualizarModoHogar();
                    });
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "No se pudo publicar el modo del hogar", e);
            cancelarTimeoutConfirmacionModo();
            getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                    .putBoolean(PREF_MODO_NO_CONFIRMADO, true).apply();
            actualizarModoHogar();
        }
    }

    private void programarTimeoutConfirmacionModo() {
        cancelarTimeoutConfirmacionModo();
        timeoutConfirmacionModo = () -> {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
            if (!prefs.getBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, false)) return;
            prefs.edit().putBoolean(PREF_MODO_NO_CONFIRMADO, true).apply();
            actualizarModoHogar();
            mostrarEstadoValvula("No se confirmó el cambio de válvula. Comprueba la conexión del dispositivo e inténtalo de nuevo.", R.color.status_error, false);
        };
        modoUiHandler.postDelayed(timeoutConfirmacionModo, TIMEOUT_CONFIRMACION_MODO_MS);
    }

    private void cancelarTimeoutConfirmacionModo() {
        if (timeoutConfirmacionModo != null) {
            modoUiHandler.removeCallbacks(timeoutConfirmacionModo);
            timeoutConfirmacionModo = null;
        }
    }

    private void seleccionarArchivoAws(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        try {
            byte[] selectedFile = leerArchivo(data.getData());
            if (requestCode == REQUEST_AWS_CERTIFICATE) {
                certificadoPendiente = selectedFile;
                actualizarEstadoConexion("Ahora selecciona la clave privada del certificado.", false);
                Toast.makeText(this, "Certificado elegido. Ahora selecciona Clave-private.pem.key.", Toast.LENGTH_LONG).show();
                seleccionarArchivoAws(REQUEST_AWS_PRIVATE_KEY);
            } else if (requestCode == REQUEST_AWS_PRIVATE_KEY) {
                if (certificadoPendiente == null) {
                    throw new IllegalStateException("Selecciona primero el certificado X.509.");
                }
                AwsMqttCredentials.save(this, certificadoPendiente, selectedFile);
                certificadoPendiente = null;
                btnConfigurarCertificado.setVisibility(android.view.View.GONE);
                actualizarEstadoConexion("Conectando…", false);
                inicializarMqtt();
            }
        } catch (Exception e) {
            certificadoPendiente = null;
            Log.e(TAG, "No se pudo importar el certificado de AWS", e);
            actualizarEstadoConexion("No se pudo importar. Verifica que elegiste el certificado y su clave privada.", false);
        }
    }

    private byte[] leerArchivo(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("No se pudo abrir el archivo seleccionado.");
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private void cerrarSesion() {
        firebaseAuth.signOut();
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) mqttClient.disconnect();
            } catch (MqttException e) {
                Log.w(TAG, "No se pudo cerrar la conexión MQTT", e);
            }
            mqttClient = null;
        }
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        configurarPantallaLogin();
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        Log.w(TAG, "No se pudo limpiar el estado de Credential Manager", e);
                        configurarPantallaLogin();
                    }
                }
        );
    }

    private void inicializarMqtt() {
        try {
            if (mqttClient == null) {
                mqttClient = new MqttAsyncClient(
                        AWS_SERVER_URI,
                        obtenerClientId(),
                        new MqttDefaultFilePersistence(getCacheDir().getAbsolutePath())
                );
                mqttClient.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        runOnUiThread(() -> actualizarEstadoConexion("Sistema conectado", true));
                        if (reconnect) {
                            suscribirTopicos();
                            sincronizarModoAusente();
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        runOnUiThread(() -> actualizarEstadoConexion("Sin conexión. Reintentando…", false));
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                        long recibido = System.currentTimeMillis();
                        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                                .putString(PREF_ULTIMA_LECTURA, payload)
                                .putLong(PREF_FECHA_LECTURA, recibido)
                                .apply();
                        guardarLecturaLocal(payload, recibido);
                        runOnUiThread(() -> mostrarLectura(payload, recibido, true));
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {}
                });
            }

            if (mqttClient.isConnected()) {
                actualizarEstadoConexion("Sistema conectado", true);
                return;
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setSocketFactory(AwsMqttCredentials.createSocketFactory(this));
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    runOnUiThread(() -> actualizarEstadoConexion("Sistema conectado", true));
                    suscribirTopicos();
                    sincronizarModoAusente();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Falló la conexión MQTT", exception);
                    runOnUiThread(() -> actualizarEstadoConexion(
                            "Sin conexión. Revisando el enlace del sistema…", false));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar MQTT", e);
            actualizarEstadoConexion("No se pudo iniciar la conexión. Intenta de nuevo.", false);
        }
    }

    private String obtenerClientId() {
        android.content.SharedPreferences preferencias =
                getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String clientId = preferencias.getString(PREF_CLIENT_ID, null);
        if (clientId == null) {
            clientId = "GasGuard-" + UUID.randomUUID();
            preferencias.edit().putString(PREF_CLIENT_ID, clientId).apply();
        }
        return clientId;
    }

    private void actualizarEstadoConexion(String texto, boolean conectado) {
        if (!conectado) gasLecturaEnVivo = false;
        if (!conectado && tvModoAyuda != null
                && getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getBoolean(PREF_MODO_DISPOSITIVO_PENDIENTE, false)) {
            tvModoAyuda.setText("Sin conexión. Se enviará al reconectar.");
            tvModoAyuda.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
        }
        if (tvEstadoConexion != null) {
            boolean esError = texto.toLowerCase(Locale.ROOT).contains("sin conexión")
                    || texto.toLowerCase(Locale.ROOT).contains("no se pudo");
            tvEstadoConexion.setText((conectado ? "● " : esError ? "● " : "● ") + texto);
            int color = conectado ? R.color.status_ok : esError ? R.color.status_error : R.color.status_warning;
            tvEstadoConexion.setTextColor(ContextCompat.getColor(this, color));
        }
        if (btnAbrirValvula != null) btnAbrirValvula.setEnabled(conectado && !valvulaAbiertaReportada);
    }

    private void actualizarAccionValvula() {
        if (btnAbrirValvula == null) return;
        btnAbrirValvula.setText("Abrir válvula");
        btnAbrirValvula.setEnabled(mqttClient != null && mqttClient.isConnected() && !valvulaAbiertaReportada);
    }

    private void solicitarAperturaValvula() {
        if (valvulaAbiertaReportada) {
            mostrarEstadoValvula("La válvula ya está abierta.", R.color.status_ok, true);
            return;
        }

        boolean modoAusente = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getBoolean(PREF_MODO_AUSENTE, false);
        if (modoAusente) {
            mostrarEstadoValvula("Modo Fuera de casa activo. Vuelve a En casa antes de abrir.", R.color.status_warning, false);
            return;
        }
        if (!gasLecturaEnVivo) {
            mostrarEstadoValvula("Esperando una lectura actual antes de abrir la válvula.", R.color.status_warning, false);
            return;
        }
        if (gasElevado) {
            mostrarEstadoValvula("Nivel elevado. No se puede abrir la válvula todavía.", R.color.status_error, false);
            return;
        }
        enviarComandoValvula("abrir");
    }

    private void suscribirTopicos() {
        if (mqttClient == null || !mqttClient.isConnected()) return;
        try {
            mqttClient.subscribe("gasguard/lecturas", 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Suscrito a gasguard/lecturas");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "No se pudo suscribir a gasguard/lecturas", exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse a lecturas", e);
        }
    }

    private void enviarComandoValvula(String accion) {
        boolean esCierre = "cerrar".equals(accion);
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (mqttClient == null || !mqttClient.isConnected()) {
            if (esCierre) {
                prefs.edit().putBoolean(PREF_CIERRE_PENDIENTE, true).apply();
                if (tvEstadoValvula != null) {
                    mostrarEstadoValvula("Sin conexión. La solicitud de cierre queda pendiente.", R.color.status_warning, false);
                }
                actualizarModoHogar();
            } else {
                actualizarEstadoConexion("Sin conexión MQTT: no se envió el comando", false);
                if (tvEstadoValvula != null) mostrarEstadoValvula("Sin conexión. No se envió la solicitud de apertura.", R.color.status_warning, false);
            }
            return;
        }

        String payload = "{\"accion\":\"" + accion + "\"}";
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            if (esCierre) prefs.edit().putBoolean(PREF_CIERRE_PENDIENTE, true).apply();
            mqttClient.publish("gasguard/gas", message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    if (esCierre) prefs.edit().putBoolean(PREF_CIERRE_PENDIENTE, false).apply();
                    runOnUiThread(() -> {
                        if (tvEstadoValvula != null) {
                            mostrarEstadoValvula(esCierre
                                    ? "Solicitud de cierre enviada; esperando respuesta de la válvula."
                                    : "Solicitud de apertura enviada; esperando respuesta de la válvula.", R.color.status_warning, false);
                        }
                        actualizarModoHogar();
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "No se pudo enviar el comando de válvula", exception);
                    if (esCierre) prefs.edit().putBoolean(PREF_CIERRE_PENDIENTE, true).apply();
                    runOnUiThread(() -> {
                        if (tvEstadoValvula != null) mostrarEstadoValvula("No se recibió respuesta. Se volverá a intentar.", R.color.status_warning, false);
                        actualizarModoHogar();
                    });
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "No se pudo enviar el comando", e);
            if (esCierre) prefs.edit().putBoolean(PREF_CIERRE_PENDIENTE, true).apply();
            if (tvEstadoValvula != null) mostrarEstadoValvula("No se recibió respuesta. Se volverá a intentar.", R.color.status_warning, false);
        }
    }
}
