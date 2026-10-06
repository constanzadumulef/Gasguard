package com.company.product;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    // Dirección del servidor Apache en la Raspberry
    private static final String IP_SERVIDOR = "192.168.137.50";

    private static final String TAG = "GasGuardLogin";

    private static final String COLOR_OK = "#16A34A";
    private static final String COLOR_ERROR = "#DC2626";
    private static final String COLOR_INFO = "#6B7280";

    private TextView tvMensaje;
    private Button btnAcceder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Indicador para no resutilizar datos
        System.setProperty("http.keepAlive", "false");

        final EditText etRut = findViewById(R.id.etRut);
        final EditText etPassword = findViewById(R.id.etPassword);
        btnAcceder = findViewById(R.id.btnAcceder);
        tvMensaje = findViewById(R.id.tvMensaje);

        btnAcceder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String rut = etRut.getText().toString().trim();
                final String password = etPassword.getText().toString().trim();

                if (rut.isEmpty() || password.isEmpty()) {
                    mostrarMensaje("Por favor ingrese RUT y contraseña", COLOR_ERROR);
                    return;
                }

                mostrarMensaje("Verificando credenciales...", COLOR_INFO);
                btnAcceder.setEnabled(false);

                // La solicitud de red se ejecuta fuera del hilo principal
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String respuesta = consultarServidor(rut, password);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnAcceder.setEnabled(true);
                                procesarRespuesta(respuesta);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    // Realiza la consulta y reintenta una vez si falla la conexión
    private String consultarServidor(String rut, String password) {
        String respuesta = llamarLogin(rut, password);
        if (respuesta.startsWith("ERROR")) {
            respuesta = llamarLogin(rut, password);
        }
        return respuesta;
    }

    // Analiza la respuesta y muestra un mensaje para el usuario
    private void procesarRespuesta(String respuesta) {
        if (respuesta.startsWith("ERROR")) {
            Log.e(TAG, respuesta);
            mostrarMensaje("No fue posible iniciar sesión. Verifique su conexión a internet e intente nuevamente", COLOR_ERROR);
            return;
        }

        int inicio = respuesta.indexOf('{');
        if (inicio < 0) {
            Log.e(TAG, "Respuesta sin formato JSON: " + respuesta);
            mostrarMensaje("No fue posible iniciar sesión en este momento. Intente nuevamente", COLOR_ERROR);
            return;
        }

        try {
            JSONObject json = new JSONObject(respuesta.substring(inicio));
            if (json.getBoolean("ok")) {
                mostrarMensaje("Credencial correcta. Ingresando a GasGuard...", COLOR_OK);
            } else {
                mostrarMensaje("RUT o clave incorrecta, intente nuevamente", COLOR_ERROR);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Respuesta con formato inválido: " + respuesta);
            mostrarMensaje("No fue posible iniciar sesión en este momento. Intente nuevamente", COLOR_ERROR);
        }
    }

    // Mensaje de estado dentro de la tarjeta de acceso
    private void mostrarMensaje(String texto, String color) {
        tvMensaje.setText(texto);
        tvMensaje.setTextColor(Color.parseColor(color));
        tvMensaje.setVisibility(View.VISIBLE);
    }

    // Envía RUT y contraseña a login.php y devuelve la respuesta del servidor
    private String llamarLogin(String rut, String password) {
        HttpURLConnection con = null;
        try {
            URL url = new URL("http://" + IP_SERVIDOR + "/gasguard/login.php");
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Connection", "close");
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setDoOutput(true);

            String datos = "rut=" + URLEncoder.encode(rut, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8");
            OutputStream os = con.getOutputStream();
            os.write(datos.getBytes("UTF-8"));
            os.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String linea;
            while ((linea = br.readLine()) != null) {
                sb.append(linea);
            }
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }
}