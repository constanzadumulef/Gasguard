package com.company.product;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Referencias a los elementos del diseño XML
        EditText etRut = findViewById(R.id.etRut);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnAcceder = findViewById(R.id.btnAcceder);

        // Acción al hacer clic en el botón de acceso
        btnAcceder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String rut = etRut.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (rut.isEmpty() || password.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Por favor ingrese RUT y contraseña", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Conectando a GasGuard...", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}