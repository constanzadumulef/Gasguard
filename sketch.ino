#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <Preferences.h>
#include <time.h>

WiFiClientSecure net;
PubSubClient mqtt(net);
Servo valvula;
Preferences preferencias;

const char* WIFI_SSID = "Wokwi-GUEST";
const char* WIFI_PASS = "";
const char* AWS_ENDPOINT = "a3nfanbzxilxsm-ats.iot.us-east-1.amazonaws.com";

const int MQ2_PIN = 34;
const int SERVO_PIN = 18;

const char* TOPIC_PUB = "gasguard/lecturas";
const char* TOPIC_SUB = "gasguard/gas";


const char AWS_CA[] PROGMEM = R"EOF(
-----BEGIN CERTIFICATE-----
MIIDQTCCAimgAwIBAgITBmyfz5m/jAo54vB4ikPmljZbyjANBgkqhkiG9w0BAQsF
ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6
b24gUm9vdCBDQSAxMB4XDTE1MDUyNjAwMDAwMFoXDTM4MDExNzAwMDAwMFowOTEL
MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv
b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj
ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM
9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw
IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6
VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L
93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm
jgSubJrIqg0CAwEAAaNCMEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
AYYwHQYDVR0OBBYEFIQYzIU07LwMlJQuCFmcx7IQTgoIMA0GCSqGSIb3DQEBCwUA
A4IBAQCY8jdaQZChGsV2USggNiMOruYou6r4lK5IpDB/G/wkjUu0yKGX9rbxenDI
U5PMCCjjmCXPI6T53iHTfIUJrU6adTrCC2qJeHZERxhlbI1Bjjt/msv0tadQ1wUs
N+gDS63pYaACbvXy8MWy7Vu33PqUXHeeE6V/Uq2V8viTO96LXFvKWlJbYK8U90vv
o/ufQJVtMVT8QtPHRh8jrdkPSHCa2XV4cdFyQzR1bldZwgJcJmApzyMZFo6IQ6XU
5MsI+yMRQ+hDKXJioaldXgjUkK642M4UwtBV8ob2xJNDd2ZhwLnoQdeXeGADbkpy
rqXRfboQnoZsG4q5WTP468SQvvG5
-----END CERTIFICATE-----)EOF";

// IMPORTANTE: Asegúrate de que este sea tu certificado de dispositivo (-certificate.pem.crt)
const char AWS_CERT[] PROGMEM = R"EOF(
-----BEGIN CERTIFICATE-----
MIIDWTCCAkGgAwIBAgIUSBquO1thUhQFuSfv+80S4h9DX2YwDQYJKoZIhvcNAQEL
BQAwTTFLMEkGA1UECwxCQW1hem9uIFdlYiBTZXJ2aWNlcyBPPUFtYXpvbi5jb20g
SW5jLiBMPVNlYXR0bGUgU1Q9V2FzaGluZ3RvbiBDPVVTMB4XDTI2MTEwNjIwMDAw
N1oXDTQ5MTIzMTIzNTk1OVowHjEcMBoGA1UEAwwTQVdTIElvVCBDZXJ0aWZpY2F0
ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMkUhyvAnFk8y2vxaISr
7XRvqZRilhltmekWTfK3WgLXHryd5p3efQJBBe2SIU6SwZeHXChUUY4ueKptJlY3
15mto1sgBdqj6MMuZMmJKJw7q4INVKI66Qx+qB4yLXwcsYSRVw47Q8D2n/z5S2Pu
TBocR+F1adbJvU8dyr5oZTFp5iguNlypKeWUlNFjzyIFpl+2M2UBoJw6Q5EZ1beJ
mmBN7G6ayfSqOAP3BSM1qt+NQvmeiDH/Hu++StK5tkZiHLtDYg6/lRxpTeQ3nzxD
Hj3h70XHBiR0GI4d3RZiGBU4KeAz34N/BQk7JI7DSnKLUgnRx5h2LzHPnEnvwUne
5LUCAwEAAaNgMF4wHwYDVR0jBBgwFoAUdg6l98tgRuLQZCmc51DVIUalEB8wHQYD
VR0OBBYEFLnAh2Xgpg3b0zAw05Wx5mGP1EGZMAwGA1UdEwEB/wQCMAAwDgYDVR0P
AQH/BAQDAgeAMA0GCSqGSIb3DQEBCwUAA4IBAQAlHwO7b4Po119+SwJuxZhFrNmM
OGA/Qtk4AnmceEaKPpqGNJhJ8FVKgy9POI3He5gCtqj+6Nrt0iMjbQUbfp9VwbZI
84QEkrjm88EWn6qFLCPxv3nh/o2h0Fg1F0VpArGlxrKyJkJEZfu42FK3skbK3PRE
MQbqXP0HuChNJ1aCu58Gt+jleqrPdDxNcGYb9ziQlL95iurDPMXp6kkQ5sa5DC/g
SEF+7gp1BN26uK/zdcQwLXIlQFXRInAHy7skiKVukxCpOizxlALmm+t0eKywCHXd
v6YtJoNZ6WiIehN6iNzMCl+sYZzN5rv2NkhQkHIEs+HWv4Wdsj7CJt5UolDP
-----END CERTIFICATE-----
)EOF";


//Clave privada
const char AWS_KEY[] PROGMEM = R"EOF(
-----BEGIN RSA PRIVATE KEY-----
MIIEpQIBAAKCAQEAyRSHK8CcWTzLa/FohKvtdG+plGKWGW2Z6RZN8rdaAtcevJ3m
nd59AkEF7ZIhTpLBl4dcKFRRji54qm0mVjfXma2jWyAF2qPowy5kyYkonDurgg1U
ojrpDH6oHjItfByxhJFXDjtDwPaf/PlLY+5MGhxH4XVp1sm9Tx3KvmhlMWnmKC42
XKkp5ZSU0WPPIgWmX7YzZQGgnDpDkRnVt4maYE3sbprJ9Ko4A/cFIzWq341C+Z6I
Mf8e775K0rm2RmIcu0NiDr+VHGlN5DefPEMePeHvRccGJHQYjh3dFmIYFTgp4DPf
g38FCTskjsNKcotSCdHHmHYvMc+cSe/BSd7ktQIDAQABAoIBAQC0pvO11CrjZWIg
zVcocso35KYHAPd3SueO6JIJML72dExSCs6JIL1QzBhGUxYHYIP63gpm1TIcOhYA
j2V06mCR2dU8Zi28O6w/xquYANjyXA1/Hvjdc22y4wupbU0+Bw1ZW7ZJu+boMuqT
vWkFE69defD+Vp1K8h5ZORvQKzxWwgTqMSLNIUVBd/0A8t723utQZps5Aij7n7EF
srwey33WmMlXYhvTVSnoLX/N0dtyG1bI7/xrirIOUQ0uIDvpcpXGwmAsj/3K3H9F
kFm/YHTmH/9OvrLQ0JCAzTvKkbwRLtmh3gJZAelNyFHPIxioVpusFTG6bUNwtP/a
cx3oLvohAoGBAOUaWJr4UriMawoQQP3JHXR0HInWe+xvw20a7lHhEyJoDH0TmatZ
YY0ztZuvyM3iNUP6O5PyjCUxNfeqztZ/A66yXzOU4/tSmzd9iBweuXJzuDWI+BoG
JyDiuDIxYTkvYP3CiG7TzKBRhD5++GrkBEhtisp0LICY9aM4D5L7FIrpAoGBAOCv
9oie846Kjai3UamUA0tusbHfIFdcKX6WvtlFjIgOfPDLy67WaTbuiXB2cJeznyZi
T5q46D6Y+ZKJ8YR++oBFzgQofZExcdVLnx6GB6wH2NtopnrmsrPcyB+IO/x/qS91
7eR+GEoZ4ydD4XdB5z5UgLQkZGegXffoMCwIUxPtAoGBAJYkzAjT0TKNI+E2gcFQ
unMFXWagIBr1q/em/KTAgs+7rOOTotP/AcWOK+jaLRlwtEPWjJELFfmR5LJpSKCe
IQWudGHTpA2jN4IITNlQ8WwmloTPPPmhKqeMrfaHEhZuDHyBrvQi7inrAmYKxDcZ
NA1bbOf9duTYWbMcHGjRD8dxAoGBAMdmQeMTNVDN01PkhJt7GMVr0lTpkHkW+Fg4
jdyiZUolmWnVYsihzbQLU3u0EM4AgX/+BWlpKerLtcj/lJAiZEtMJ7329SDSgZRq
TSc4iTx5plFpeT1e2T7X895P9/Rokwvkm5+I7CNu//FLw58jpIXjVmkIVvbEEkC9
FWsuZBQtAoGATdKIsHlAnSqNnTp6pg/XGJn7PO4lsN2squ17bABAMWLhOMwcXBbw
LgNZ1faOB83bBRX1p0EJ6AWlFzpkxwvklZV9DftXA2zyJbX0UCpk2caVMSuUPq79
Mj8GkcCOh/7Bs3OlYap54ORMDXmllnqz1HUIPFZSxmWW4K1KMy5ndCs=
-----END RSA PRIVATE KEY-----
)EOF";

const int UMBRAL_AVISO_ADC = 1800;
const int UMBRAL_CRITICO_ADC = 3000;
const uint8_t COLA_MAX = 10;

bool modoAusente = false;
bool valvulaCerrada = true;
bool wifiReportado = false;

int gasActual = 0;

unsigned long ultimo = 0;
unsigned long ultimoIntentoWiFi = 0;
unsigned long ultimoIntentoMQTT = 0;
unsigned long ultimoEnvioCola = 0;

String claveCola(uint8_t indice) {
  return "q" + String(indice);
}

const char* estadoGas() {
  if (gasActual >= UMBRAL_CRITICO_ADC) return "critico";
  if (gasActual >= UMBRAL_AVISO_ADC) return "alerta";
  return "normal";
}

void cerrarValvulaLocal() {
  if (!valvulaCerrada) {
    valvula.write(90);
    valvulaCerrada = true;
    Serial.println("Valvula cerrada");
  }
}

void callback(char* topic, byte* payload, unsigned int len) {
  if (strcmp(topic, TOPIC_SUB) != 0) return;

  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, payload, len);

  if (error) {
    Serial.print("Error leyendo orden: ");
    Serial.println(error.c_str());
    return;
  }

  const char* accion = doc["accion"];
  if (accion == nullptr) {
    Serial.println("Orden recibida sin campo accion");
    return;
  }

  Serial.print("Orden recibida: ");
  Serial.println(accion);

  if (strcmp(accion, "ausente") == 0) {
    modoAusente = true;
    preferencias.putBool("ausente", true);
    cerrarValvulaLocal();
    Serial.println("Proteccion al salir activada");
    return;
  }

  if (strcmp(accion, "casa") == 0) {
    modoAusente = false;
    preferencias.putBool("ausente", false);
    Serial.println("Modo En casa activado");
    return;
  }

  if (strcmp(accion, "cerrar") == 0) {
    cerrarValvulaLocal();
    return;
  }

  if (strcmp(accion, "abrir") == 0) {
    if (modoAusente || gasActual >= UMBRAL_AVISO_ADC) {
      cerrarValvulaLocal();
      Serial.println("Apertura rechazada: proteccion activa o nivel de gas elevado");
      return;
    }

    valvula.write(0);
    valvulaCerrada = false;
    Serial.println("Valvula abierta");
  }
}

void conectarWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiReportado) {
      wifiReportado = true;
      Serial.print("WiFi conectado. IP: ");
      Serial.println(WiFi.localIP());
    }
    return;
  }

  wifiReportado = false;

  if (millis() - ultimoIntentoWiFi < 15000) return;

  ultimoIntentoWiFi = millis();
  Serial.print("Intentando conectar WiFi. Estado: ");
  Serial.println(WiFi.status());

  WiFi.begin(WIFI_SSID, WIFI_PASS);
}

void conectarMQTT() {
  if (WiFi.status() != WL_CONNECTED || mqtt.connected()) return;
  if (time(nullptr) < 1700000000) return;
  if (millis() - ultimoIntentoMQTT < 3000) return;

  ultimoIntentoMQTT = millis();
  Serial.println("Conectando a AWS IoT...");

  if (mqtt.connect("esp32-1")) {
    bool suscrito = mqtt.subscribe(TOPIC_SUB);

    if (suscrito) {
      Serial.println("AWS IoT conectado y suscrito");
    } else {
      Serial.println("Conectado a AWS IoT, pero fallo la suscripcion");
    }
  } else {
    Serial.print("Fallo MQTT. Codigo: ");
    Serial.println(mqtt.state());
  }
}

void guardarLecturaPendiente(const char* mensaje) {
  uint8_t inicio = preferencias.getUChar("qHead", 0);
  uint8_t cantidad = preferencias.getUChar("qCount", 0);

  if (cantidad >= COLA_MAX) {
    preferencias.remove(claveCola(inicio).c_str());
    inicio = (inicio + 1) % COLA_MAX;
    cantidad--;
    Serial.println("Cola llena: se reemplazo la lectura mas antigua");
  }

  uint8_t destino = (inicio + cantidad) % COLA_MAX;
  String clave = claveCola(destino);

  if (preferencias.putString(clave.c_str(), mensaje) > 0) {
    cantidad++;
    preferencias.putUChar("qHead", inicio);
    preferencias.putUChar("qCount", cantidad);
    Serial.println("Lectura guardada temporalmente");
  } else {
    Serial.println("No se pudo guardar la lectura temporal");
  }
}

void enviarLecturaPendiente() {
  if (!mqtt.connected()) return;

  uint8_t inicio = preferencias.getUChar("qHead", 0);
  uint8_t cantidad = preferencias.getUChar("qCount", 0);

  if (cantidad == 0 || millis() - ultimoEnvioCola < 1000) return;

  ultimoEnvioCola = millis();

  String clave = claveCola(inicio);
  String mensaje = preferencias.getString(clave.c_str(), "");

  if (mensaje.length() == 0) {
    preferencias.remove(clave.c_str());
    preferencias.putUChar("qHead", (inicio + 1) % COLA_MAX);
    preferencias.putUChar("qCount", cantidad - 1);
    return;
  }

  if (mqtt.publish(TOPIC_PUB, mensaje.c_str())) {
    preferencias.remove(clave.c_str());
    preferencias.putUChar("qHead", (inicio + 1) % COLA_MAX);
    preferencias.putUChar("qCount", cantidad - 1);
    Serial.println("Lectura pendiente enviada");
  } else {
    Serial.println("No se pudo enviar la lectura pendiente");
  }
}

void setup() {
  Serial.begin(115200);

  preferencias.begin("gasguard", false);
  modoAusente = preferencias.getBool("ausente", false);

  valvula.attach(SERVO_PIN);
  valvula.write(90);
  valvulaCerrada = true;

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  ultimoIntentoWiFi = millis();



  configTime(0, 0, "pool.ntp.org", "time.nist.gov");

  net.setCACert(AWS_CA);
  net.setCertificate(AWS_CERT);
  net.setPrivateKey(AWS_KEY);

  mqtt.setServer(AWS_ENDPOINT, 8883);
  mqtt.setCallback(callback);
  mqtt.setSocketTimeout(15);
  mqtt.setKeepAlive(60);
}

void loop() {
  gasActual = analogRead(MQ2_PIN);

  if (modoAusente || gasActual >= UMBRAL_CRITICO_ADC) {
    cerrarValvulaLocal();
  }

  conectarWiFi();

  if (mqtt.connected()) {
    mqtt.loop();
    enviarLecturaPendiente();
  } else {
    conectarMQTT();
  }

  if (millis() - ultimo >= 5000) {
    ultimo = millis();

    char mensaje[160];

    snprintf(
      mensaje,
      sizeof(mensaje),
      "{\"gas\":%d,\"estado\":\"%s\",\"valvula\":\"%s\",\"modo\":\"%s\"}",
      gasActual,
      estadoGas(),
      valvulaCerrada ? "cerrada" : "abierta",
      modoAusente ? "ausente" : "casa"
    );

    uint8_t pendientes = preferencias.getUChar("qCount", 0);
    bool publicado = false;

    if (mqtt.connected() && pendientes == 0) {
      publicado = mqtt.publish(TOPIC_PUB, mensaje);
    }

    if (publicado) {
      Serial.print("Enviado a AWS: ");
      Serial.println(mensaje);
    } else {
      guardarLecturaPendiente(mensaje);
      Serial.print("Pendiente: ");
      Serial.println(mensaje);
    }
  }

  delay(100);
}
